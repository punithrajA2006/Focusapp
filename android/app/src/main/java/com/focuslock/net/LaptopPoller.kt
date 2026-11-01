package com.focuslock.net

import android.os.SystemClock
import com.focuslock.crypto.HmacSigner
import com.focuslock.data.model.ActiveSession
import com.focuslock.data.model.PairingConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

sealed class PollResult {
    object NoStop : PollResult()
    data class Stop(val packetJson: String) : PollResult()
    object Rejected : PollResult()
    object Offline : PollResult()
}

/**
 * The phone asks the laptop "is a stop armed?" with a signed request. The laptop never needs the
 * phone's IP and the phone never opens a listening port. A stop only arrives if the laptop owner
 * armed one, and it is verified again on the phone (signature, session, freshness, nonce).
 */
object LaptopPoller {

    suspend fun poll(config: PairingConfig, session: ActiveSession): PollResult = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val remainingSeconds = session.remainingTimeMs(SystemClock.elapsedRealtime()) / 1000L
            val body = HmacSigner.buildPollRequest(config.hmacSecretHex, session.sessionCode, remainingSeconds)

            conn = (URL("http://${config.laptopHost}:${config.laptopPort}/poll").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 3000
                readTimeout = 3000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
            conn.outputStream.use { it.write(body.toByteArray()) }

            if (conn.responseCode != 200) {
                PollResult.Rejected
            } else {
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                val stop = JSONObject(text).opt("stop")
                if (stop is JSONObject) PollResult.Stop(stop.toString()) else PollResult.NoStop
            }
        } catch (e: Exception) {
            PollResult.Offline
        } finally {
            conn?.disconnect()
        }
    }
}
