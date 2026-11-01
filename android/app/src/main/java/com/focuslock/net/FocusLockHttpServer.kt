package com.focuslock.net

import android.content.Context
import android.util.Log
import com.focuslock.FocusLockApplication
import com.focuslock.crypto.HmacSigner
import com.focuslock.data.model.SessionStatus
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject

/**
 * Embedded lightweight HTTP server running on the phone only while a study session is active.
 * Listens on LAN port 8765 for signed stop commands dispatched by the Fedora companion.
 */
class FocusLockHttpServer(
    private val context: Context,
    port: Int = 8765
) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method

        if (method == Method.GET && uri == "/api/status") {
            val app = context.applicationContext as FocusLockApplication
            val current = app.repository.activeSessionFlow.value
            val json = JSONObject().apply {
                put("status", current?.status?.name ?: "IDLE")
                put("remaining_ms", current?.remainingTimeMs(android.os.SystemClock.elapsedRealtime()) ?: 0)
            }
            return newFixedLengthResponse(Response.Status.OK, "application/json", json.toString())
        }

        if (method == Method.POST && uri == "/api/stop") {
            val files = HashMap<String, String>()
            try {
                session.parseBody(files)
                val postData = files["postData"] ?: ""

                val app = context.applicationContext as FocusLockApplication
                val secret = app.repository.pairingConfigFlow.value.hmacSecretHex

                if (secret.isBlank()) {
                    return newFixedLengthResponse(
                        Response.Status.UNAUTHORIZED,
                        "application/json",
                        "{\"error\":\"Phone is not paired with laptop.\"}"
                    )
                }

                val isValid = HmacSigner.verifyCommandPacket(secret, postData)
                if (isValid) {
                    Log.i("FocusLockServer", "Cryptographically signed stop command verified from laptop.")
                    app.repository.endSession(SessionStatus.STOPPED_BY_LAPTOP)
                    return newFixedLengthResponse(
                        Response.Status.OK,
                        "application/json",
                        "{\"success\":true,\"message\":\"Session stopped by verified laptop command.\"}"
                    )
                } else {
                    Log.w("FocusLockServer", "Rejected unauthorized stop command: bad HMAC signature or replayed nonce.")
                    return newFixedLengthResponse(
                        Response.Status.FORBIDDEN,
                        "application/json",
                        "{\"error\":\"Invalid cryptographic signature or expired timestamp.\"}"
                    )
                }
            } catch (e: Exception) {
                return newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR,
                    "application/json",
                    "{\"error\":\"${e.message}\"}"
                )
            }
        }

        return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found")
    }
}
