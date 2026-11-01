package com.focuslock.crypto

import android.util.Base64
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs

/** Result of checking a stop command that claims to come from the laptop. */
data class StopVerdict(val accepted: Boolean, val nonce: String = "", val reason: String = "")

/**
 * HMAC-SHA256 signing and verification, byte-for-byte compatible with companion/focuslock_crypto.py
 * (sorted keys, compact JSON).
 */
object HmacSigner {

    private const val MAX_CLOCK_SKEW_SECONDS = 300L

    fun buildPollRequest(secretHex: String, sessionCode: String, remainingSeconds: Long): String {
        val payload = JSONObject().apply {
            put("action", "POLL")
            put("session_id", sessionCode)
            put("remaining_s", remainingSeconds)
            put("timestamp", System.currentTimeMillis() / 1000L)
            put("nonce", newNonce())
        }
        val signature = computeHmacSha256(secretHex, canonicalizeJson(payload))
        return JSONObject().put("payload", payload).put("signature", signature).toString()
    }

    /**
     * Accepts a stop only if: valid signature, action STOP_SESSION, bound to THIS session,
     * timestamp inside the skew window, and a nonce that was never accepted before.
     */
    fun verifyStopPacket(
        secretHex: String,
        jsonPacket: String,
        expectedSessionCode: String,
        isNonceUsed: (String) -> Boolean
    ): StopVerdict {
        return try {
            val root = JSONObject(jsonPacket)
            val payload = root.getJSONObject("payload")
            val signature = root.getString("signature")

            val expected = computeHmacSha256(secretHex, canonicalizeJson(payload))
            if (!MessageDigest.isEqual(expected.toByteArray(), signature.lowercase().toByteArray())) {
                return StopVerdict(false, reason = "bad signature")
            }
            if (payload.optString("action") != "STOP_SESSION") return StopVerdict(false, reason = "wrong action")
            if (payload.optString("session_id").lowercase() != expectedSessionCode.lowercase()) {
                return StopVerdict(false, reason = "different session")
            }
            val now = System.currentTimeMillis() / 1000L
            if (abs(now - payload.getLong("timestamp")) > MAX_CLOCK_SKEW_SECONDS) {
                return StopVerdict(false, reason = "expired")
            }
            val nonce = payload.getString("nonce")
            if (isNonceUsed(nonce)) return StopVerdict(false, reason = "replayed")
            StopVerdict(true, nonce)
        } catch (e: Exception) {
            StopVerdict(false, reason = "malformed")
        }
    }

    /** Offline token = Base64Url(JSON{payload, signature}) typed in from the laptop. */
    fun verifyOfflineToken(
        secretHex: String,
        token: String,
        expectedSessionCode: String,
        isNonceUsed: (String) -> Boolean
    ): StopVerdict {
        return try {
            val clean = token.trim()
            val padded = clean + "=".repeat((4 - clean.length % 4) % 4)
            val json = String(Base64.decode(padded, Base64.URL_SAFE), StandardCharsets.UTF_8)
            verifyStopPacket(secretHex, json, expectedSessionCode, isNonceUsed)
        } catch (e: Exception) {
            StopVerdict(false, reason = "malformed")
        }
    }

    fun computeHmacSha256(secretHex: String, data: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(hexToBytes(secretHex), "HmacSHA256"))
        return mac.doFinal(data.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun newNonce(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun canonicalizeJson(obj: JSONObject): String {
        val keys = obj.keys().asSequence().toList().sorted()
        return keys.joinToString(separator = ",", prefix = "{", postfix = "}") { k ->
            val v = obj.get(k)
            val rendered = if (v is Number || v is Boolean) v.toString() else "\"$v\""
            "\"$k\":$rendered"
        }
    }

    private fun hexToBytes(s: String): ByteArray {
        val out = ByteArray(s.length / 2)
        for (i in out.indices) {
            out[i] = ((Character.digit(s[2 * i], 16) shl 4) + Character.digit(s[2 * i + 1], 16)).toByte()
        }
        return out
    }
}
