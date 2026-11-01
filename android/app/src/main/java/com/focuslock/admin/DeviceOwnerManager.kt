package com.focuslock.admin

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.UserManager
import com.focuslock.receiver.FocusLockDeviceAdminReceiver

/**
 * Manages Device Owner and Device Admin restrictions:
 * - DISALLOW_SAFE_BOOT: Prevents booting into safe mode to disable services.
 * - DISALLOW_FACTORY_RESET: Prevents hardware wipe from OS menu.
 * - DISALLOW_UNINSTALL_APPS & setUninstallBlocked: Locks FocusLock package.
 * - LockTask: Kiosk pinning for approved study applications.
 */
class DeviceOwnerManager(private val context: Context) {

    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val adminComponent = ComponentName(context, FocusLockDeviceAdminReceiver::class.java)

    val isDeviceOwner: Boolean
        get() = dpm.isDeviceOwnerApp(context.packageName)

    val isDeviceAdmin: Boolean
        get() = dpm.isAdminActive(adminComponent)

    /**
     * Activates strict lockdown policies when session starts.
     */
    fun applyStrictLockRestrictions(allowedPackages: Set<String>) {
        if (!isDeviceOwner) return

        try {
            // Block Safe Boot
            dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_SAFE_BOOT)
            // Block Factory Reset
            dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_FACTORY_RESET)
            // Block force-stop / clear-data of apps, date & time changes, and USB debugging
            dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_APPS_CONTROL)
            dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_CONFIG_DATE_TIME)
            dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_DEBUGGING_FEATURES)
            // Prevent uninstalling FocusLock
            dpm.setUninstallBlocked(adminComponent, context.packageName, true)
            // Configure lock task mode packages
            dpm.setLockTaskPackages(adminComponent, (allowedPackages + context.packageName).toTypedArray())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Relaxes restrictions after session has legitimately concluded.
     */
    fun clearRestrictions() {
        if (!isDeviceOwner) return

        try {
            dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_SAFE_BOOT)
            dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_FACTORY_RESET)
            dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_APPS_CONTROL)
            dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_CONFIG_DATE_TIME)
            dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_DEBUGGING_FEATURES)
            dpm.setUninstallBlocked(adminComponent, context.packageName, false)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
