package com.focuslock.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.focuslock.FocusLockApplication
import com.focuslock.data.model.SessionStatus

/**
 * Listens for ACTION_TIME_CHANGED and ACTION_TIMEZONE_CHANGED.
 * Defends monotonic study sessions against manual date/time modification.
 */
class TimeChangeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.w("FocusLockTime", "System clock/timezone event detected: $action")

        val app = context.applicationContext as? FocusLockApplication ?: return
        val session = app.repository.activeSessionFlow.value ?: return

        if (session.status == SessionStatus.ACTIVE) {
            // AbsoluteSession computes remaining time exclusively using SystemClock.elapsedRealtime().
            // Wall-clock shifts are intentionally ignored to preserve strict monotonic study countdown.
            Log.i("FocusLockTime", "Ignored wall-clock shift. Monotonic timer remains anchored.")
        }
    }
}
