package com.focuslock.crypto

import android.util.Base64
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs

/**
 * Android-side HMAC-SHA256 signature verifier and offline token parser.
 */
object HmacSigner {

    private const val MAX_CLOCK_SKEW_SECONDS = 180 // 3 minutes tolerance

    /**
     * Verifies an incoming LAN HTTP stop command.
     */
    fun verifyCommandPacket(secretHex: String, jsonPacket: String): Boolean {
        return try {
            val root = JSONObject(jsonPacket)
            val payloadObj = root.getJSONObject("payload")
            val signature = root.getString("signature")

            // 1. Verify timestamp window (replay attack mitigation)
            val timestamp = payloadObj.getLong("timestamp")
            val currentEpochSeconds = System.currentTimeMillis() / 1000L
            if (abs(currentEpochSeconds - timestamp) > MAX_CLOCK_SKEW_SECONDS) {
                return false
            }

            // 2. Canonical JSON string matching Python companion
            val canonical = canonicalizeJson(payloadObj)
            val expectedSig = computeHmacSha256(secretHex, canonical)

            expectedSig.equals(signature, ignoreCase = true)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Verifies an offline stop token typed or scanned by the user.
     */
    fun verifyOfflineToken(secretHex: String, tokenStr: String): Boolean {
        return try {
            val decodedBytes = Base64.decode(tokenStr, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
            val jsonStr = String(decodedBytes, StandardCharsets.UTF_8)
            verifyCommandPacket(secretHex, jsonStr)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun computeHmacSha256(secretHex: String, data: String): String {
        val secretBytes = hexStringToByteArray(secretHex)
        val keySpec = SecretKeySpec(secretBytes, "HmacSHA256")
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(keySpec)
        val hash = mac.doFinal(data.toByteArray(StandardCharsets.UTF_8))
        return byteArrayToHexString(hash)
    }

    private fun canonicalizeJson(obj: JSONObject): String {
        val keys = obj.keys().asSequence().toList().sorted()
        val sb = StringBuilder("{")
        for (i in keys.indices) {
            val k = keys[i]
            val v = obj.get(k)
            sb.append("\"").append(k).append("\":")
            when (v) {
                is String -> sb.append("\"").append(v).append("\"")
                is Number, is Boolean -> sb.append(v)
                else -> sb.append("\"").append(v.toString()).append("\"")
            }
            if (i < keys.size - 1) sb.append(",")
        }
        sb.append("}")
        return sb.toString()
    }

    private fun hexStringToByteArray(s: String): ByteArray {
        val len = s.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(s[i], 16) shl 4) + Character.digit(s[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    private fun byteArrayToHexString(bytes: ByteArray): String {
        val sb = java.lang.StringBuilder()
        for (b in bytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }
}
