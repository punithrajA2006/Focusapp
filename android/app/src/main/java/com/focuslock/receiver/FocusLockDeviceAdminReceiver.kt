package com.focuslock.receiver

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Handles Device Administration state changes and Device Owner provisioning hooks.
 */
class FocusLockDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i("FocusLockAdmin", "FocusLock Device Admin successfully enabled.")
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        // Warning shown if user attempts to remove Device Admin in settings
        return "Warning: Disabling FocusLock Device Admin will terminate active study enforcement. This is restricted during active sessions."
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.w("FocusLockAdmin", "FocusLock Device Admin was disabled.")
    }

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        super.onProfileProvisioningComplete(context, intent)
        Log.i("FocusLockAdmin", "FocusLock Device Owner provisioning complete.")
    }
}
