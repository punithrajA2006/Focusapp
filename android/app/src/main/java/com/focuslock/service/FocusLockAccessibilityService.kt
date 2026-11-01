package com.focuslock.service

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.focuslock.FocusLockApplication
import com.focuslock.data.model.SessionStatus
import com.focuslock.engine.SessionEngine
import com.focuslock.overlay.BlockOverlayManager

/**
 * High-speed accessibility service intercepting window transitions and system tampering attempts.
 * Intercepts unapproved apps, Date & Time adjustments, and Settings App Details within 20-50ms.
 */
class FocusLockAccessibilityService : AccessibilityService() {

    private lateinit var overlayManager: BlockOverlayManager

    override fun onCreate() {
        super.onCreate()
        overlayManager = BlockOverlayManager(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return

        val packageName = event.packageName?.toString() ?: return
        val app = application as FocusLockApplication
        val activeSession = app.repository.activeSessionFlow.value

        if (activeSession == null || activeSession.status != SessionStatus.ACTIVE) {
            overlayManager.dismissBlockOverlay()
            return
        }

        // Hardened Settings Defense:
        // If user opens Android Settings, inspect node content to block Date & Time, Dev Options, and Force Stop
        if (packageName == "com.android.settings") {
            if (isSettingsTamperingAttempt(rootInActiveWindow)) {
                val remainingMin = (activeSession.remainingTimeMs(SystemClock.elapsedRealtime()) / 60000L).toInt().coerceAtLeast(1)
                overlayManager.showBlockOverlay("System Settings Tamper Intercept", remainingMin)
                performGlobalAction(GLOBAL_ACTION_HOME)
                return
            }
        }

        // App Whitelist / Blacklist check
        val shouldBlock = SessionEngine.shouldBlockPackage(activeSession, packageName)
        if (shouldBlock) {
            val remainingMinutes = (activeSession.remainingTimeMs(SystemClock.elapsedRealtime()) / 60000L).toInt().coerceAtLeast(1)
            overlayManager.showBlockOverlay(packageName, remainingMinutes)
            performGlobalAction(GLOBAL_ACTION_HOME)
        } else {
            overlayManager.dismissBlockOverlay()
        }
    }

    private fun isSettingsTamperingAttempt(rootNode: AccessibilityNodeInfo?): Boolean {
        if (rootNode == null) return false
        val textDump = StringBuilder()
        collectText(rootNode, textDump)
        val str = textDump.toString().lowercase()

        // Block Date & Time tampering
        if (str.contains("date & time") || str.contains("set date") || str.contains("set time") || str.contains("use network-provided time")) {
            return true
        }
        // Block Developer Options tampering (USB debugging toggles)
        if (str.contains("developer options") || str.contains("usb debugging")) {
            return true
        }
        // Block FocusLock App Info (Force stop, Uninstall, Clear storage)
        if (str.contains("focuslock") && (str.contains("force stop") || str.contains("uninstall") || str.contains("storage & cache"))) {
            return true
        }

        return false
    }

    private fun collectText(node: AccessibilityNodeInfo, sb: StringBuilder) {
        node.text?.let { sb.append(it).append(" ") }
        node.contentDescription?.let { sb.append(it).append(" ") }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                collectText(child, sb)
                child.recycle()
            }
        }
    }

    override fun onInterrupt() {
        overlayManager.dismissBlockOverlay()
    }

    override fun onDestroy() {
        overlayManager.dismissBlockOverlay()
        super.onDestroy()
    }
}
