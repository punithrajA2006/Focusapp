package com.focuslock.worker

import android.app.ActivityManager
import android.content.Context
import androidx.work.*
import com.focuslock.FocusLockApplication
import com.focuslock.data.model.SessionStatus
import com.focuslock.service.SessionEnforcementService
import java.util.concurrent.TimeUnit

/**
 * Watchdog background worker scheduled via WorkManager.
 * Periodically wakes up to confirm SessionEnforcementService is alive during active sessions.
 */
class SessionWatchdogWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    override fun doWork(): Result {
        val app = context.applicationContext as? FocusLockApplication ?: return Result.success()
        val session = app.repository.activeSessionFlow.value

        if (session != null && session.status == SessionStatus.ACTIVE) {
            // Verify if service is running; if killed by OS, resurrect it
            if (!isServiceRunning(context, SessionEnforcementService::class.java)) {
                SessionEnforcementService.start(context)
            }
        }

        return Result.success()
    }

    private fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }

    companion object {
        private const val WATCHDOG_WORK_TAG = "focuslock_watchdog_work"

        fun scheduleWatchdog(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(false)
                .build()

            val request = PeriodicWorkRequestBuilder<SessionWatchdogWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .addTag(WATCHDOG_WORK_TAG)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WATCHDOG_WORK_TAG,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancelWatchdog(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WATCHDOG_WORK_TAG)
        }
    }
}
