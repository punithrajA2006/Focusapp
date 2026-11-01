package com.focuslock.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.focuslock.FocusLockApplication
import com.focuslock.data.model.SessionStatus
import com.focuslock.service.SessionEnforcementService

/**
 * Resurrects the lockdown immediately when Android finishes booting or direct-boot unlocks.
 * Reconciles the monotonic clock across hardware power cycles.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.i("FocusLockBoot", "Received system boot broadcast: $action")

        val app = context.applicationContext as? FocusLockApplication ?: return
        val currentSession = app.repository.activeSessionFlow.value ?: return

        if (currentSession.status == SessionStatus.ACTIVE) {
            val nowElapsed = SystemClock.elapsedRealtime()

            // Calculate how much monotonic time elapsed before this reboot
            val elapsedBeforeReboot = (currentSession.lastVerifiedRealtimeMs - currentSession.startElapsedRealtimeMs).coerceAtLeast(0L)
            val updatedAccumulated = currentSession.accumulatedElapsedBeforeRebootMs + elapsedBeforeReboot

            val updatedSession = currentSession.copy(
                startElapsedRealtimeMs = nowElapsed,
                lastVerifiedRealtimeMs = nowElapsed,
                accumulatedElapsedBeforeRebootMs = updatedAccumulated,
                rebootCount = currentSession.rebootCount + 1
            )

            // If remaining time is still positive, restart the enforcement service immediately
            if (updatedSession.remainingTimeMs(nowElapsed) > 0) {
                app.repository.startSession(
                    (updatedSession.remainingTimeMs(nowElapsed) / 60000L).toInt().coerceAtLeast(1),
                    updatedSession.config.allowedPackages
                )
                SessionEnforcementService.start(context)
                Log.i("FocusLockBoot", "Resumed active session after reboot: remaining ${updatedSession.remainingTimeMs(nowElapsed)}ms")
            } else {
                app.repository.endSession(SessionStatus.COMPLETED)
                Log.i("FocusLockBoot", "Session expired during device downtime.")
            }
        }
    }
}
