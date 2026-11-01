package com.focuslock.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.focuslock.FocusLockApplication
import com.focuslock.MainActivity
import com.focuslock.data.model.ActiveSession
import com.focuslock.data.model.SessionStatus
import com.focuslock.overlay.BlockOverlayManager
import com.focuslock.worker.SessionWatchdogWorker
import kotlinx.coroutines.*

class SessionEnforcementService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var tickerJob: Job? = null
    private lateinit var overlayManager: BlockOverlayManager

    override fun onCreate() {
        super.onCreate()
        overlayManager = BlockOverlayManager(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as FocusLockApplication
        val action = intent?.action ?: ACTION_START_SESSION

        when (action) {
            ACTION_START_SESSION -> {
                val session = app.repository.activeSessionFlow.value
                if (session != null && session.status == SessionStatus.ACTIVE) {
                    startForeground(NOTIFICATION_ID, buildNotification(session))
                    startTicker(app)
                    SessionWatchdogWorker.scheduleWatchdog(this)
                } else {
                    stopSelf()
                }
            }
            ACTION_STOP_SESSION -> {
                overlayManager.dismissBlockOverlay()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        // START_STICKY instructs the OS to recreate the service if killed under memory pressure
        return START_STICKY
    }

    private fun startTicker(app: FocusLockApplication) {
        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            while (isActive) {
                val session = app.repository.activeSessionFlow.value
                if (session == null || session.status != SessionStatus.ACTIVE) {
                    overlayManager.dismissBlockOverlay()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    break
                }

                val nowElapsed = SystemClock.elapsedRealtime()
                val remainingMs = session.remainingTimeMs(nowElapsed)

                if (remainingMs <= 0) {
                    // Timer finished automatically!
                    app.repository.endSession(SessionStatus.COMPLETED)
                    overlayManager.dismissBlockOverlay()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    break
                }

                // Update notification text every tick
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(NOTIFICATION_ID, buildNotification(session))

                delay(1000L)
            }
        }
    }

    private fun buildNotification(session: ActiveSession): Notification {
        val remainingMinutes = (session.remainingTimeMs(SystemClock.elapsedRealtime()) / 60000L).toInt()
        val contentText = "Study lockdown active: ~${remainingMinutes}m remaining. Phone cannot bypass."

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FocusLock Active")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pendingIntent)
            .setOngoing(true) // Cannot be swiped away by user
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "FocusLock Active Study Lock",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Displays the active study session timer and tamper protection status."
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        tickerJob?.cancel()
        serviceScope.cancel()
        overlayManager.dismissBlockOverlay()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "focuslock_enforcement_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START_SESSION = "com.focuslock.action.START_SESSION"
        const val ACTION_STOP_SESSION = "com.focuslock.action.STOP_SESSION"

        fun start(context: Context) {
            val intent = Intent(context, SessionEnforcementService::class.java).apply {
                action = ACTION_START_SESSION
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, SessionEnforcementService::class.java).apply {
                action = ACTION_STOP_SESSION
            }
            context.startService(intent)
        }
    }
}
