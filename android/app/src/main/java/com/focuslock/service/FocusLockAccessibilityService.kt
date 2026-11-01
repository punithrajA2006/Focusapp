package com.focuslock.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.focuslock.FocusLockApplication
import com.focuslock.data.model.SessionStatus
import com.focuslock.engine.SessionEngine
import com.focuslock.overlay.BlockOverlayManager

/**
 * Intercepts window changes during a session. Anything not on the whitelist is covered by the
 * overlay and bounced to the home screen. Launchers and call screens are exempt so Home never loops.
 */
class FocusLockAccessibilityService : AccessibilityService() {

    private lateinit var overlayManager: BlockOverlayManager
    private var systemExempt: Set<String> = emptySet()

    override fun onCreate() {
        super.onCreate()
        overlayManager = BlockOverlayManager(this)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        systemExempt = discoverLaunchers() + CALL_SURFACES
    }

    private fun discoverLaunchers(): Set<String> {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return packageManager.queryIntentActivities(home, PackageManager.MATCH_DEFAULT_ONLY)
            .map { it.activityInfo.packageName }
            .toSet()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val packageName = event.packageName?.toString() ?: return
        val isWindowChange = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        val isContentChange = event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        if (!isWindowChange && !isContentChange) return

        val app = application as FocusLockApplication
        val session = app.repository.activeSessionFlow.value
        if (session == null || session.status != SessionStatus.ACTIVE) {
            overlayManager.dismissBlockOverlay()
            return
        }

        val remainingMin = (session.remainingTimeMs(SystemClock.elapsedRealtime()) / 60000L).toInt() + 1

        // Content-change events are noisy: only inspect them for Settings, where tampering happens.
        if (packageName == "com.android.settings") {
            if (isSettingsTamperingAttempt(rootInActiveWindow)) {
                overlayManager.showBlockOverlay("System settings", remainingMin)
                performGlobalAction(GLOBAL_ACTION_HOME)
                return
            }
        }
        if (isContentChange) return

        if (systemExempt.isEmpty()) systemExempt = discoverLaunchers() + CALL_SURFACES

        if (SessionEngine.shouldBlockPackage(session, packageName, systemExempt)) {
            overlayManager.showBlockOverlay(packageName, remainingMin)
            performGlobalAction(GLOBAL_ACTION_HOME)
        } else {
            overlayManager.dismissBlockOverlay()
        }
    }

    private fun isSettingsTamperingAttempt(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        val sb = StringBuilder()
        collectText(root, sb)
        val s = sb.toString().lowercase()

        if (s.contains("date & time") || s.contains("set date") || s.contains("set time") ||
            s.contains("use network-provided time") || s.contains("automatic date")
        ) return true
        if (s.contains("developer options") || s.contains("usb debugging")) return true
        if (s.contains("focuslock") && (s.contains("force stop") || s.contains("uninstall") ||
                s.contains("storage") || s.contains("accessibility") || s.contains("device admin"))
        ) return true
        // The Accessibility and Device-admin lists themselves, where the lock could be switched off.
        if (s.contains("installed apps") && s.contains("focuslock") && s.contains("accessibility")) return true
        return false
    }

    private fun collectText(node: AccessibilityNodeInfo, sb: StringBuilder) {
        node.text?.let { sb.append(it).append(' ') }
        node.contentDescription?.let { sb.append(it).append(' ') }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectText(it, sb) }
        }
    }

    override fun onInterrupt() {
        overlayManager.dismissBlockOverlay()
    }

    override fun onDestroy() {
        overlayManager.dismissBlockOverlay()
        super.onDestroy()
    }

    private companion object {
        val CALL_SURFACES = setOf(
            "com.android.incallui", "com.samsung.android.incallui",
            "com.android.server.telecom", "com.android.phone",
            "com.google.android.dialer", "com.android.dialer", "com.samsung.android.dialer"
        )
    }
}
