package com.focuslock.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.provider.Settings
import com.focuslock.data.model.ActiveSession
import com.focuslock.data.model.FocusStats
import com.focuslock.data.model.PairingConfig
import com.focuslock.data.model.SessionConfig
import com.focuslock.data.model.SessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.TimeZone
import java.util.UUID

/**
 * Single source of truth for the session, laptop pairing, and lifetime stats.
 *
 * The timer never trusts the wall clock. It counts monotonic ticks (elapsedRealtime) and, when the
 * process loads after a reboot (boot counter changed, or the monotonic clock went backwards), it
 * banks the time proven before the reboot and grants no credit for the downtime.
 */
class FocusLockRepository(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences("focuslock_secure_prefs", Context.MODE_PRIVATE)

    private val _activeSessionFlow = MutableStateFlow<ActiveSession?>(null)
    val activeSessionFlow: StateFlow<ActiveSession?> = _activeSessionFlow.asStateFlow()

    private val _pairingConfigFlow = MutableStateFlow(PairingConfig())
    val pairingConfigFlow: StateFlow<PairingConfig> = _pairingConfigFlow.asStateFlow()

    private val _allowedPackagesFlow = MutableStateFlow(SessionConfig.defaultAllowedPackages)
    val allowedPackagesFlow: StateFlow<Set<String>> = _allowedPackagesFlow.asStateFlow()

    private val _laptopOnlineFlow = MutableStateFlow(false)
    val laptopOnlineFlow: StateFlow<Boolean> = _laptopOnlineFlow.asStateFlow()

    private val _statsFlow = MutableStateFlow(FocusStats())
    val statsFlow: StateFlow<FocusStats> = _statsFlow.asStateFlow()

    init {
        loadPersistedState()
    }

    // ---------------------------------------------------------------- loading

    private fun currentBootCount(): Int = try {
        Settings.Global.getInt(appContext.contentResolver, Settings.Global.BOOT_COUNT, -1)
    } catch (e: Exception) {
        -1
    }

    private fun loadPersistedState() {
        _pairingConfigFlow.value = PairingConfig(
            isPaired = prefs.getBoolean(KEY_IS_PAIRED, false),
            hmacSecretHex = prefs.getString(KEY_HMAC_SECRET, "") ?: "",
            laptopHost = prefs.getString(KEY_LAPTOP_HOST, "") ?: "",
            laptopPort = prefs.getInt(KEY_LAPTOP_PORT, 5001)
        )

        prefs.getStringSet(KEY_ALLOWED_PACKAGES, null)?.let {
            _allowedPackagesFlow.value = it + SessionConfig.defaultAllowedPackages
        }

        _statsFlow.value = FocusStats(
            completedSessions = prefs.getInt(KEY_STAT_SESSIONS, 0),
            totalMinutes = prefs.getInt(KEY_STAT_MINUTES, 0),
            streakDays = currentStreak()
        )

        prefs.getString(KEY_ACTIVE_SESSION, null)?.let { restoreSession(it) }
    }

    private fun restoreSession(raw: String) {
        try {
            val j = JSONObject(raw)
            if (SessionStatus.valueOf(j.optString("status", SessionStatus.ACTIVE.name)) != SessionStatus.ACTIVE) {
                persistSession(null)
                return
            }
            val packages = HashSet<String>()
            j.optJSONArray("allowedPackages")?.let { arr ->
                for (i in 0 until arr.length()) packages.add(arr.getString(i))
            }

            var session = ActiveSession(
                sessionId = j.getString("sessionId"),
                startTimeWallMs = j.getLong("startTimeWallMs"),
                startElapsedRealtimeMs = j.getLong("startElapsedRealtimeMs"),
                expectedDurationMs = j.getLong("expectedDurationMs"),
                targetEndElapsedRealtimeMs = j.getLong("targetEndElapsedRealtimeMs"),
                lastVerifiedRealtimeMs = j.getLong("lastVerifiedRealtimeMs"),
                accumulatedElapsedBeforeRebootMs = j.optLong("accumulatedElapsedBeforeRebootMs", 0L),
                rebootCount = j.optInt("rebootCount", 0),
                bootCount = j.optInt("bootCount", -1),
                status = SessionStatus.ACTIVE,
                config = SessionConfig(
                    durationMinutes = j.optInt("durationMinutes", 60),
                    allowedPackages = packages + SessionConfig.defaultAllowedPackages
                )
            )

            val nowElapsed = SystemClock.elapsedRealtime()
            val boot = currentBootCount()
            val rebooted = nowElapsed < session.lastVerifiedRealtimeMs ||
                (session.bootCount != -1 && boot != -1 && session.bootCount != boot)
            if (rebooted) {
                val provenBeforeReboot =
                    (session.lastVerifiedRealtimeMs - session.startElapsedRealtimeMs).coerceAtLeast(0L)
                session = session.copy(
                    startElapsedRealtimeMs = nowElapsed,
                    lastVerifiedRealtimeMs = nowElapsed,
                    accumulatedElapsedBeforeRebootMs = session.accumulatedElapsedBeforeRebootMs + provenBeforeReboot,
                    rebootCount = session.rebootCount + 1,
                    bootCount = boot
                )
            }

            if (session.remainingTimeMs(nowElapsed) <= 0L) {
                recordFinished(session, SessionStatus.COMPLETED)
                persistSession(null)
            } else {
                persistSession(session)
                _activeSessionFlow.value = session
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ---------------------------------------------------------------- session lifecycle

    /** Starts a session. Refuses if one is already running, so it can never be replaced by a shorter one. */
    fun startSession(durationMinutes: Int, allowedPackages: Set<String>): ActiveSession {
        _activeSessionFlow.value?.let { if (it.status == SessionStatus.ACTIVE) return it }

        val nowElapsed = SystemClock.elapsedRealtime()
        val durationMs = durationMinutes * 60 * 1000L
        val session = ActiveSession(
            sessionId = UUID.randomUUID().toString(),
            startTimeWallMs = System.currentTimeMillis(),
            startElapsedRealtimeMs = nowElapsed,
            expectedDurationMs = durationMs,
            targetEndElapsedRealtimeMs = nowElapsed + durationMs,
            lastVerifiedRealtimeMs = nowElapsed,
            bootCount = currentBootCount(),
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

    /** Called every few seconds by the service so a reboot loses at most that much proven time. */
    fun touchVerified() {
        val current = _activeSessionFlow.value ?: return
        if (current.status != SessionStatus.ACTIVE) return
        val updated = current.copy(lastVerifiedRealtimeMs = SystemClock.elapsedRealtime())
        persistSession(updated)
        _activeSessionFlow.value = updated
    }

    fun endSession(reason: SessionStatus) {
        val current = _activeSessionFlow.value ?: return
        if (current.status == SessionStatus.ACTIVE) recordFinished(current, reason)
        persistSession(null)
        _activeSessionFlow.value = current.copy(status = reason)
    }

    fun setLaptopOnline(online: Boolean) {
        if (_laptopOnlineFlow.value != online) _laptopOnlineFlow.value = online
    }

    // ---------------------------------------------------------------- pairing & replay protection

    fun savePairing(secretHex: String, host: String, port: Int) {
        prefs.edit()
            .putBoolean(KEY_IS_PAIRED, true)
            .putString(KEY_HMAC_SECRET, secretHex)
            .putString(KEY_LAPTOP_HOST, host)
            .putInt(KEY_LAPTOP_PORT, port)
            .apply()
        _pairingConfigFlow.value = PairingConfig(
            isPaired = true,
            hmacSecretHex = secretHex,
            laptopHost = host,
            laptopPort = port,
            pairedAtTimestamp = System.currentTimeMillis()
        )
    }

    /** Accepts the JSON printed by the laptop (`cli.py pair`) or shown on its dashboard. */
    fun savePairingFromJson(raw: String): Boolean {
        if (_activeSessionFlow.value?.status == SessionStatus.ACTIVE) return false // no re-pairing mid-session
        return try {
            val j = JSONObject(raw.trim())
            val secret = j.getString("secret").trim().lowercase()
            val host = j.getString("laptop_host").trim()
            val port = j.optInt("laptop_port", 5001)
            if (!secret.matches(Regex("[0-9a-f]{64}")) || host.isBlank() || port !in 1..65535) {
                false
            } else {
                savePairing(secret, host, port)
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    fun isNonceUsed(nonce: String): Boolean =
        prefs.getStringSet(KEY_NONCES, emptySet())?.contains(nonce) == true

    fun rememberNonce(nonce: String) {
        val set = LinkedHashSet(prefs.getStringSet(KEY_NONCES, emptySet()) ?: emptySet())
        set.add(nonce)
        while (set.size > 64) set.remove(set.first())
        prefs.edit().putStringSet(KEY_NONCES, set).apply()
    }

    fun updateAllowedPackages(packages: Set<String>) {
        val combined = packages + SessionConfig.defaultAllowedPackages
        prefs.edit().putStringSet(KEY_ALLOWED_PACKAGES, combined).apply()
        _allowedPackagesFlow.value = combined
    }

    // ---------------------------------------------------------------- stats

    private fun today(): Long = (System.currentTimeMillis() + TimeZone.getDefault().rawOffset) / 86_400_000L

    /** Streak counts only if you studied today or yesterday; otherwise it has lapsed. */
    private fun currentStreak(): Int {
        val last = prefs.getLong(KEY_STAT_LAST_DAY, -1L)
        return if (last >= today() - 1) prefs.getInt(KEY_STAT_STREAK, 0) else 0
    }

    private fun recordFinished(session: ActiveSession, reason: SessionStatus) {
        val nowElapsed = SystemClock.elapsedRealtime()
        val studiedMs = if (reason == SessionStatus.COMPLETED) {
            session.expectedDurationMs
        } else {
            (session.expectedDurationMs - session.remainingTimeMs(nowElapsed)).coerceAtLeast(0L)
        }
        val minutes = (studiedMs / 60_000L).toInt()
        val editor = prefs.edit()
        var sessions = prefs.getInt(KEY_STAT_SESSIONS, 0)
        var streak = prefs.getInt(KEY_STAT_STREAK, 0)

        if (reason == SessionStatus.COMPLETED) {
            sessions += 1
            val day = today()
            val last = prefs.getLong(KEY_STAT_LAST_DAY, -1L)
            streak = when (last) {
                day -> streak.coerceAtLeast(1)
                day - 1 -> streak + 1
                else -> 1
            }
            editor.putLong(KEY_STAT_LAST_DAY, day)
        }
        val totalMinutes = prefs.getInt(KEY_STAT_MINUTES, 0) + minutes
        editor.putInt(KEY_STAT_SESSIONS, sessions)
            .putInt(KEY_STAT_MINUTES, totalMinutes)
            .putInt(KEY_STAT_STREAK, streak)
            .apply()
        _statsFlow.value = FocusStats(sessions, totalMinutes, currentStreak())
    }

    // ---------------------------------------------------------------- persistence

    private fun persistSession(session: ActiveSession?) {
        if (session == null) {
            prefs.edit().remove(KEY_ACTIVE_SESSION).apply()
            return
        }
        val json = JSONObject().apply {
            put("sessionId", session.sessionId)
            put("startTimeWallMs", session.startTimeWallMs)
            put("startElapsedRealtimeMs", session.startElapsedRealtimeMs)
            put("expectedDurationMs", session.expectedDurationMs)
            put("targetEndElapsedRealtimeMs", session.targetEndElapsedRealtimeMs)
            put("lastVerifiedRealtimeMs", session.lastVerifiedRealtimeMs)
            put("accumulatedElapsedBeforeRebootMs", session.accumulatedElapsedBeforeRebootMs)
            put("rebootCount", session.rebootCount)
            put("bootCount", session.bootCount)
            put("status", session.status.name)
            put("durationMinutes", session.config.durationMinutes)
            put("allowedPackages", JSONArray(session.config.allowedPackages.toList()))
        }
        prefs.edit().putString(KEY_ACTIVE_SESSION, json.toString()).apply()
    }

    companion object {
        private const val KEY_ACTIVE_SESSION = "key_active_session"
        private const val KEY_IS_PAIRED = "key_is_paired"
        private const val KEY_HMAC_SECRET = "key_hmac_secret"
        private const val KEY_LAPTOP_HOST = "key_laptop_host"
        private const val KEY_LAPTOP_PORT = "key_laptop_port"
        private const val KEY_ALLOWED_PACKAGES = "key_allowed_packages"
        private const val KEY_NONCES = "key_used_nonces"
        private const val KEY_STAT_SESSIONS = "key_stat_sessions"
        private const val KEY_STAT_MINUTES = "key_stat_minutes"
        private const val KEY_STAT_STREAK = "key_stat_streak"
        private const val KEY_STAT_LAST_DAY = "key_stat_last_day"
    }
}
