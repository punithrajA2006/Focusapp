package com.focuslock.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.focuslock.FocusLockApplication
import com.focuslock.data.model.SessionStatus
import com.focuslock.service.SessionEnforcementService

/**
 * Restarts enforcement after a reboot. The repository has already reconciled the timer
 * (monotonic clock + boot counter) while loading, so this only needs to bring the service back.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? FocusLockApplication ?: return
        val session = app.repository.activeSessionFlow.value ?: return
        if (session.status == SessionStatus.ACTIVE) {
            SessionEnforcementService.start(context)
        }
    }
}
