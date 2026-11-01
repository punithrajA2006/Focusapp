package com.focuslock.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.focuslock.FocusLockApplication
import com.focuslock.MainActivity
import com.focuslock.admin.DeviceOwnerManager
import com.focuslock.crypto.HmacSigner
import com.focuslock.data.model.ActiveSession
import com.focuslock.data.model.SessionStatus
import com.focuslock.net.LaptopPoller
import com.focuslock.net.PollResult
import com.focuslock.overlay.BlockOverlayManager
import com.focuslock.worker.SessionWatchdogWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Keeps the lock alive. A session ends in exactly two ways: the timer reaches zero, or the laptop
 * sends a signed stop (picked up by [startPoller]). Nothing on the phone can end it early.
 */
class SessionEnforcementService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var tickerJob: Job? = null
    private var pollerJob: Job? = null
    private lateinit var overlayManager: BlockOverlayManager
    private lateinit var deviceOwner: DeviceOwnerManager

    override fun onCreate() {
        super.onCreate()
        overlayManager = BlockOverlayManager(this)
        deviceOwner = DeviceOwnerManager(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as FocusLockApplication

        when (intent?.action ?: ACTION_START_SESSION) {
            ACTION_START_SESSION -> {
                val session = app.repository.activeSessionFlow.value
                // Always enter the foreground first, otherwise Android kills the app for not doing so in time.
                startForeground(NOTIFICATION_ID, buildNotification(session))
                if (session != null && session.status == SessionStatus.ACTIVE) {
                    deviceOwner.applyStrictLockRestrictions(session.config.allowedPackages)
                    startTicker(app)
                    startPoller(app)
                    SessionWatchdogWorker.scheduleWatchdog(this)
                } else {
                    shutDown()
                }
            }
            ACTION_STOP_SESSION -> shutDown()
        }
        return START_STICKY
    }

    private fun startTicker(app: FocusLockApplication) {
        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            var ticks = 0
            while (isActive) {
                val session = app.repository.activeSessionFlow.value
                if (session == null || session.status != SessionStatus.ACTIVE) {
                    shutDown()
                    break
                }
                if (session.remainingTimeMs(SystemClock.elapsedRealtime()) <= 0L) {
                    app.repository.endSession(SessionStatus.COMPLETED)
                    shutDown()
                    break
                }
                if (ticks % 5 == 0) app.repository.touchVerified()
                if (ticks % 15 == 0) {
                    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.notify(NOTIFICATION_ID, buildNotification(session))
                }
                ticks++
                delay(1000L)
            }
        }
    }

    private fun startPoller(app: FocusLockApplication) {
        pollerJob?.cancel()
        pollerJob = serviceScope.launch {
            val repo = app.repository
            while (isActive) {
                val session = repo.activeSessionFlow.value
                if (session == null || session.status != SessionStatus.ACTIVE) break

                val config = repo.pairingConfigFlow.value
                if (config.isPaired && config.hmacSecretHex.isNotBlank()) {
                    when (val result = LaptopPoller.poll(config, session)) {
                        is PollResult.Stop -> {
                            val verdict = HmacSigner.verifyStopPacket(
                                config.hmacSecretHex, result.packetJson, session.sessionCode
                            ) { repo.isNonceUsed(it) }
                            if (verdict.accepted) {
                                repo.rememberNonce(verdict.nonce)
                                repo.endSession(SessionStatus.STOPPED_BY_LAPTOP) // ticker cleans up next second
                            }
                            repo.setLaptopOnline(true)
                        }
                        PollResult.NoStop -> repo.setLaptopOnline(true)
                        PollResult.Rejected, PollResult.Offline -> repo.setLaptopOnline(false)
                    }
                }
                delay(5000L)
            }
        }
    }

    private fun shutDown() {
        overlayManager.dismissBlockOverlay()
        deviceOwner.clearRestrictions()
        SessionWatchdogWorker.cancelWatchdog(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(session: ActiveSession?): Notification {
        val minutes = session?.let { (it.remainingTimeMs(SystemClock.elapsedRealtime()) / 60000L).toInt() + 1 } ?: 0
        val text = if (session != null) "$minutes min left. Ends with the timer or from your laptop." else "Starting..."
        val pending = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FocusLock is on")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "FocusLock study lock", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows the running study session."
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        // Do not clear device-owner restrictions here: the OS may kill us mid-session and they must survive.
        tickerJob?.cancel()
        pollerJob?.cancel()
        serviceScope.cancel()
        overlayManager.dismissBlockOverlay()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "focuslock_enforcement_channel_v2"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START_SESSION = "com.focuslock.action.START_SESSION"
        const val ACTION_STOP_SESSION = "com.focuslock.action.STOP_SESSION"

        fun start(context: Context) {
            val intent = Intent(context, SessionEnforcementService::class.java).apply { action = ACTION_START_SESSION }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, SessionEnforcementService::class.java).apply { action = ACTION_STOP_SESSION })
        }
    }
}
