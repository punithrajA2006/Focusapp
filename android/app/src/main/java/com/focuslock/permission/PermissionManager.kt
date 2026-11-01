package com.focuslock.permission

import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import com.focuslock.receiver.FocusLockDeviceAdminReceiver
import com.focuslock.service.FocusLockAccessibilityService

/**
 * Encapsulates status checks and direct intent navigation for all powerful permissions
 * required by FocusLock to establish an unbreakable study lock.
 */
object PermissionManager {

    data class PermissionStatus(
        val overlayGranted: Boolean,
        val accessibilityGranted: Boolean,
        val usageStatsGranted: Boolean,
        val deviceAdminGranted: Boolean,
        val deviceOwnerGranted: Boolean,
        val batteryOptimizationsIgnored: Boolean,
        val notificationsGranted: Boolean
    ) {
        val isReadyForSession: Boolean
            get() = overlayGranted && accessibilityGranted && usageStatsGranted && deviceAdminGranted
    }

    /**
     * Checks all required permissions across the device runtime.
     */
    fun checkAllPermissions(context: Context): PermissionStatus {
        return PermissionStatus(
            overlayGranted = canDrawOverlays(context),
            accessibilityGranted = isAccessibilityServiceEnabled(context),
            usageStatsGranted = hasUsageStatsPermission(context),
            deviceAdminGranted = isDeviceAdminActive(context),
            deviceOwnerGranted = isDeviceOwner(context),
            batteryOptimizationsIgnored = isIgnoringBatteryOptimizations(context),
            notificationsGranted = hasNotificationPermission(context)
        )
    }

    // 1. Overlay Permission (SYSTEM_ALERT_WINDOW)
    fun canDrawOverlays(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun requestOverlayIntent(context: Context): Intent {
        return Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
    }

    // 2. Accessibility Service (Instant Foreground Interception)
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expectedComponentName = ComponentName(context, FocusLockAccessibilityService::class.java).flattenToString()
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabledServices.split(":").any { it.equals(expectedComponentName, ignoreCase = true) }
    }

    fun requestAccessibilityIntent(): Intent {
        return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    }

    // 3. Usage Stats Access (PACKAGE_USAGE_STATS)
    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun requestUsageStatsIntent(): Intent {
        return Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
    }

    // 4. Device Admin (Tamper Resistance & Lock Enforcement)
    fun isDeviceAdminActive(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val componentName = ComponentName(context, FocusLockDeviceAdminReceiver::class.java)
        return dpm.isAdminActive(componentName)
    }

    fun requestDeviceAdminIntent(context: Context): Intent {
        val componentName = ComponentName(context, FocusLockDeviceAdminReceiver::class.java)
        return Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "FocusLock requires Device Administrator privileges to lock down distractions and prevent task bypass during active sessions."
            )
        }
    }

    // 5. Device Owner (Ultimate Lockdown: Blocks Safe Boot, Disables Uninstall)
    fun isDeviceOwner(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isDeviceOwnerApp(context.packageName)
    }

    // 6. Battery Optimization Exemption (Prevent OS Doze Kill)
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun requestIgnoreBatteryOptimizationsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }

    // 7. Notification Permission (Android 13+)
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}
