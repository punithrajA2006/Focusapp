package com.focuslock.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.focuslock.data.model.ActiveSession
import com.focuslock.data.model.PairingConfig
import com.focuslock.data.model.SessionConfig
import com.focuslock.data.model.SessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Single source of truth for session state, pairing secrets, and app allowlists.
 */
class FocusLockRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("focuslock_secure_prefs", Context.MODE_PRIVATE)

    private val _activeSessionFlow = MutableStateFlow<ActiveSession?>(null)
    val activeSessionFlow: StateFlow<ActiveSession?> = _activeSessionFlow.asStateFlow()

    private val _pairingConfigFlow = MutableStateFlow(PairingConfig())
    val pairingConfigFlow: StateFlow<PairingConfig> = _pairingConfigFlow.asStateFlow()

    private val _allowedPackagesFlow = MutableStateFlow(SessionConfig.defaultAllowedPackages)
    val allowedPackagesFlow: StateFlow<Set<String>> = _allowedPackagesFlow.asStateFlow()

    init {
        loadPersistedState()
    }

    private fun loadPersistedState() {
        // Load Pairing
        val isPaired = prefs.getBoolean(KEY_IS_PAIRED, false)
        val hmacKey = prefs.getString(KEY_HMAC_SECRET, "") ?: ""
        val laptopHost = prefs.getString(KEY_LAPTOP_HOST, "") ?: ""
        val laptopPort = prefs.getInt(KEY_LAPTOP_PORT, 5000)
        _pairingConfigFlow.value = PairingConfig(
            isPaired = isPaired,
            hmacSecretHex = hmacKey,
            laptopHost = laptopHost,
            laptopPort = laptopPort
        )

        // Load Allowed Packages
        val savedPackages = prefs.getStringSet(KEY_ALLOWED_PACKAGES, null)
        if (savedPackages != null) {
            _allowedPackagesFlow.value = savedPackages + SessionConfig.defaultAllowedPackages
        }

        // Load Active Session if one was running before kill/reboot
        val sessionJson = prefs.getString(KEY_ACTIVE_SESSION, null)
        if (sessionJson != null) {
            try {
                val json = JSONObject(sessionJson)
                val status = SessionStatus.valueOf(json.optString("status", SessionStatus.ACTIVE.name))
                if (status == SessionStatus.ACTIVE) {
                    val session = ActiveSession(
                        sessionId = json.getString("sessionId"),
                        startTimeWallMs = json.getLong("startTimeWallMs"),
                        startElapsedRealtimeMs = json.getLong("startElapsedRealtimeMs"),
                        expectedDurationMs = json.getLong("expectedDurationMs"),
                        targetEndElapsedRealtimeMs = json.getLong("targetEndElapsedRealtimeMs"),
                        lastVerifiedRealtimeMs = json.getLong("lastVerifiedRealtimeMs"),
                        accumulatedElapsedBeforeRebootMs = json.optLong("accumulatedElapsedBeforeRebootMs", 0L),
                        rebootCount = json.optInt("rebootCount", 0),
                        status = status
                    )
                    _activeSessionFlow.value = session
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun startSession(durationMinutes: Int, allowedPackages: Set<String>): ActiveSession {
        val nowElapsed = android.os.SystemClock.elapsedRealtime()
        val nowWall = System.currentTimeMillis()
        val durationMs = durationMinutes * 60 * 1000L

        val session = ActiveSession(
            sessionId = UUID.randomUUID().toString(),
            startTimeWallMs = nowWall,
            startElapsedRealtimeMs = nowElapsed,
            expectedDurationMs = durationMs,
            targetEndElapsedRealtimeMs = nowElapsed + durationMs,
            lastVerifiedRealtimeMs = nowElapsed,
            status = SessionStatus.ACTIVE,
            config = SessionConfig(
                durationMinutes = durationMinutes,
                allowedPackages = allowedPackages + SessionConfig.defaultAllowedPackages
            )
        )

        persistSession(session)
        _activeSessionFlow.value = session
        return session
    }

    fun extendSession(additionalMinutes: Int) {
        val current = _activeSessionFlow.value ?: return
        if (current.status != SessionStatus.ACTIVE) return

        val addedMs = additionalMinutes * 60 * 1000L
        val updated = current.copy(
            expectedDurationMs = current.expectedDurationMs + addedMs,
            targetEndElapsedRealtimeMs = current.targetEndElapsedRealtimeMs + addedMs
        )
        persistSession(updated)
        _activeSessionFlow.value = updated
    }

    fun endSession(reason: SessionStatus) {
        val current = _activeSessionFlow.value ?: return
        val updated = current.copy(status = reason)
        persistSession(null)
        _activeSessionFlow.value = updated
    }

    fun savePairing(secretHex: String, host: String, port: Int) {
        prefs.edit()
            .putBoolean(KEY_IS_PAIRED, true)
            .putString(KEY_HMAC_SECRET, secretHex)
            .putString(KEY_LAPTOP_HOST, host)
            .putInt(KEY_LAPTOP_PORT, port)
            .putLong(KEY_PAIRED_AT, System.currentTimeMillis())
            .apply()

        _pairingConfigFlow.value = PairingConfig(
            isPaired = true,
            hmacSecretHex = secretHex,
            laptopHost = host,
            laptopPort = port,
            pairedAtTimestamp = System.currentTimeMillis()
        )
    }

    fun updateAllowedPackages(packages: Set<String>) {
        val combined = packages + SessionConfig.defaultAllowedPackages
        prefs.edit().putStringSet(KEY_ALLOWED_PACKAGES, combined).apply()
        _allowedPackagesFlow.value = combined
    }

    private fun persistSession(session: ActiveSession?) {
        if (session == null) {
            prefs.edit().remove(KEY_ACTIVE_SESSION).apply()
        } else {
            val json = JSONObject().apply {
                put("sessionId", session.sessionId)
                put("startTimeWallMs", session.startTimeWallMs)
                put("startElapsedRealtimeMs", session.startElapsedRealtimeMs)
                put("expectedDurationMs", session.expectedDurationMs)
                put("targetEndElapsedRealtimeMs", session.targetEndElapsedRealtimeMs)
                put("lastVerifiedRealtimeMs", session.lastVerifiedRealtimeMs)
                put("accumulatedElapsedBeforeRebootMs", session.accumulatedElapsedBeforeRebootMs)
                put("rebootCount", session.rebootCount)
                put("status", session.status.name)
            }
            prefs.edit().putString(KEY_ACTIVE_SESSION, json.toString()).apply()
        }
    }

    companion object {
        private const val KEY_ACTIVE_SESSION = "key_active_session"
        private const val KEY_IS_PAIRED = "key_is_paired"
        private const val KEY_HMAC_SECRET = "key_hmac_secret"
        private const val KEY_LAPTOP_HOST = "key_laptop_host"
        private const val KEY_LAPTOP_PORT = "key_laptop_port"
        private const val KEY_PAIRED_AT = "key_paired_at"
        private const val KEY_ALLOWED_PACKAGES = "key_allowed_packages"
    }
}
