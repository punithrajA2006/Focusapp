package com.focuslock.engine

import android.content.Context
import com.focuslock.data.model.ActiveSession
import com.focuslock.data.model.SessionConfig
import com.focuslock.data.model.SessionStatus
import com.focuslock.data.repository.FocusLockRepository

/**
 * High-speed decision engine to evaluate whether a package is permitted
 * during an active study session.
 */
object SessionEngine {

    /**
     * Determines whether the given package should be blocked.
     * Guaranteed: Emergency dialer and FocusLock core are never blocked.
     */
    fun shouldBlockPackage(
        activeSession: ActiveSession?,
        targetPackageName: String,
        extraExempt: Set<String> = emptySet()
    ): Boolean {
        // If no session is active or session has expired, do not block
        if (activeSession == null || activeSession.status != SessionStatus.ACTIVE) {
            return false
        }

        // System UI, Keyguard, and Input Methods (keyboards) must remain operational
        if (isSystemInfrastructure(targetPackageName)) {
            return false
        }

        // Launchers and other runtime-discovered system surfaces
        if (extraExempt.contains(targetPackageName)) {
            return false
        }

        // Safety lifelines: FocusLock itself and phone dialers are ALWAYS permitted
        if (SessionConfig.defaultAllowedPackages.contains(targetPackageName)) {
            return false
        }

        // Check against user-configured allowed apps for this session
        return !activeSession.config.allowedPackages.contains(targetPackageName)
    }

    /**
     * Packages necessary for base device responsiveness (lockscreen, soft keyboards).
     */
    fun isSystemInfrastructure(packageName: String): Boolean {
        return packageName.startsWith("com.android.systemui") ||
                packageName.startsWith("com.google.android.inputmethod") ||
                packageName.startsWith("com.samsung.android.honeyboard") ||
                packageName.contains("inputmethod") ||
                packageName == "android" // core system framework
    }
}
