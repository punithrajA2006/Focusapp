package com.focuslock.data.model

import android.os.Parcelable
import android.os.SystemClock
import kotlinx.parcelize.Parcelize

/**
 * Enforcement mode for the session.
 */
enum class LockMode {
    /**
     * Strict Whitelist: Everything is blocked EXCEPT the explicitly allowed apps.
     * (Recommended for high-stakes study sessions).
     */
    STRICT_WHITELIST,

    /**
     * Selective Blacklist: Only chosen distraction apps (e.g., social media, games) are blocked.
     * Useful when working on development tools or varied reference materials.
     */
    SELECTIVE_BLACKLIST,

    /**
     * Partial Lock: Allows non-distracting multitasking with a persistent floating HUD,
     * throttles distraction app launches with a soft delay, and enforces timed Pomodoro pauses.
     */
    PARTIAL_LOCK
}

/**
 * YouTube study filter policies.
 */
@Parcelize
data class YouTubeStudyFilter(
    val isEnabled: Boolean = true,
    val allowedChannels: Set<String> = setOf(
        "UC_x5XG1OV2P6uZZ5FSM9Ttw", // Google Developers
        "UCsBjURrPoezykLs9EqgamOA", // Fireship
        "UCWN3xxRkmTPmbKwht9FuE5A", // 3Blue1Brown
        "UCEBb1b_L6zDS3xTUrIALZOw", // MIT OpenCourseWare
        "UC4a-Gbdw7vOaccHmFo40b9g"  // Khan Academy
    ),
    val allowedKeywords: Set<String> = setOf(
        "lecture", "tutorial", "algorithm", "calculus", "machine learning",
        "deep learning", "kotlin", "linux", "compiler", "data structures",
        "python", "database", "linear algebra"
    ),
    val blockedKeywords: Set<String> = setOf(
        "shorts", "vlog", "gaming", "prank", "reaction", "trailer",
        "entertainment", "meme", "gossip", "unboxing", "tiktok"
    ),
    val blockShorts: Boolean = true,
    val blockComments: Boolean = true,
    val blockRecommendations: Boolean = true,
    val enforceStudyModeSearchOnly: Boolean = true
) : Parcelable

/**
 * Comprehensive configuration chosen before launching a session.
 */
@Parcelize
data class AbsoluteSessionConfig(
    val targetDurationMs: Long = 60 * 60 * 1000L, // 60 minutes default
    val lockMode: LockMode = LockMode.STRICT_WHITELIST,
    /**
     * Under STRICT_WHITELIST: Packages that ARE permitted.
     * Under SELECTIVE_BLACKLIST: Packages that ARE blocked.
     */
    val targetPackages: Set<String> = emptySet(),
    /**
     * Essential safety packages always exempted from blocking.
     */
    val safetyExemptPackages: Set<String> = SessionConfig.defaultAllowedPackages,
    /**
     * Partial lock features
     */
    val partialLockAllowsNotifications: Boolean = true,
    val partialLockShowFloatingTimer: Boolean = true,
    val partialLockGracePeriodSeconds: Int = 30,
    /**
     * YouTube study content filtering
     */
    val youTubeFilter: YouTubeStudyFilter = YouTubeStudyFilter()
) : Parcelable

/**
 * Tamper-proof session model that reconciles elapsed study time across device reboots,
 * completely immune to user manipulation of system date, time, or timezone.
 *
 * Security Rationale:
 * 1. android.os.SystemClock.elapsedRealtime() is monotonic and hardware-backed. It ticks
 *    during CPU deep sleep, but resets to 0 on reboot.
 * 2. System.currentTimeMillis() (wall clock) is user-controllable and NOT trusted for elapsed time.
 * 3. We record proven elapsed ticks periodically (lastPersistedElapsedRealtime).
 * 4. On reboot, proven elapsed time is accumulated, and the hardware clock is re-anchored.
 * 5. If the user manipulates the wall clock to jump into the future or past before reboot,
 *    FocusLock ignores wall clock shifts and ONLY credits proven monotonic ticks.
 */
@Parcelize
data class AbsoluteSession(
    val sessionId: String,
    val targetDurationMs: Long,
    val startElapsedRealtimeMs: Long,
    val lastPersistedElapsedRealtimeMs: Long,
    val accumulatedProvenElapsedMs: Long = 0L,
    val startWallClockMs: Long,
    val lastPersistedWallClockMs: Long,
    val bootCounter: Int = 0,
    val config: AbsoluteSessionConfig,
    val status: SessionStatus = SessionStatus.ACTIVE
) : Parcelable {

    /**
     * Calculates the remaining study time with zero trust in system wall clock.
     *
     * @param currentElapsedRealtimeMs Current monotonic hardware time (SystemClock.elapsedRealtime())
     * @return Remaining milliseconds in the study session (clamped >= 0)
     */
    fun computeRemainingMs(currentElapsedRealtimeMs: Long): Long {
        if (status != SessionStatus.ACTIVE) return 0L

        // Monotonic time elapsed in the current boot cycle
        val currentBootElapsed = (currentElapsedRealtimeMs - startElapsedRealtimeMs).coerceAtLeast(0L)
        val totalElapsed = accumulatedProvenElapsedMs + currentBootElapsed
        return (targetDurationMs - totalElapsed).coerceAtLeast(0L)
    }

    /**
     * Returns true if the target study time has elapsed.
     */
    fun isFinished(currentElapsedRealtimeMs: Long): Boolean {
        return computeRemainingMs(currentElapsedRealtimeMs) <= 0L
    }

    /**
     * Reconciles session state upon device boot (called inside BootCompletedReceiver).
     *
     * Handles the case where the user manually set the system date forward by months/years
     * before rebooting to fool the timer.
     *
     * @param newBootElapsedRealtimeMs Fresh monotonic clock (typically 0-30000ms after boot)
     * @param currentWallClockMs The current wall clock reported by the OS after boot
     * @param systemBootCount Monotonic boot count from Settings.Global.BOOT_COUNT (if available)
     */
    fun reconcileAfterReboot(
        newBootElapsedRealtimeMs: Long,
        currentWallClockMs: Long,
        systemBootCount: Int = bootCounter + 1
    ): AbsoluteSession {
        // Monotonic time proven to have elapsed before the reboot occurred
        val preRebootDelta = (lastPersistedElapsedRealtimeMs - startElapsedRealtimeMs).coerceAtLeast(0L)
        val newAccumulated = accumulatedProvenElapsedMs + preRebootDelta

        // Wall clock difference during the reboot
        val wallDeltaMs = currentWallClockMs - lastPersistedWallClockMs

        // Anti-Tampering Check:
        // A normal phone reboot takes 15 seconds to 3 minutes (15,000ms - 180,000ms).
        // If wallDeltaMs is negative (user rolled clock back) OR wallDeltaMs is > 10 minutes (user skipped forward),
        // we detect tampering. Under tampering, we credit ZERO offline downtime, ensuring the user cannot
        // skip the session by manipulating system date/time.
        val maxAcceptableRebootDowntimeMs = 10 * 60 * 1000L // 10 minutes
        val isWallClockSuspect = wallDeltaMs < 0 || wallDeltaMs > maxAcceptableRebootDowntimeMs

        val offlineCreditMs = if (isWallClockSuspect) {
            0L // Zero credit granted for untrusted wall time
        } else {
            wallDeltaMs.coerceIn(0L, 3 * 60 * 1000L) // At most 3 minutes of realistic reboot time credited
        }

        val totalProven = newAccumulated + offlineCreditMs
        val remaining = (targetDurationMs - totalProven).coerceAtLeast(0L)

        return this.copy(
            startElapsedRealtimeMs = newBootElapsedRealtimeMs,
            lastPersistedElapsedRealtimeMs = newBootElapsedRealtimeMs,
            accumulatedProvenElapsedMs = totalProven,
            lastPersistedWallClockMs = currentWallClockMs,
            bootCounter = systemBootCount,
            status = if (remaining <= 0L) SessionStatus.COMPLETED else SessionStatus.ACTIVE
        )
    }

    /**
     * Determines whether a target package should be blocked based on the chosen LockMode.
     */
    fun isPackageBlocked(packageName: String): Boolean {
        if (status != SessionStatus.ACTIVE) return false

        // Emergency and system safety packages are ALWAYS permitted
        if (config.safetyExemptPackages.contains(packageName)) return false
        if (isSystemInfrastructure(packageName)) return false

        return when (config.lockMode) {
            LockMode.STRICT_WHITELIST -> {
                // In Whitelist mode: Block everything unless explicitly included in targetPackages
                !config.targetPackages.contains(packageName)
            }
            LockMode.SELECTIVE_BLACKLIST -> {
                // In Blacklist mode: Block ONLY the apps user specifically chose to lock
                config.targetPackages.contains(packageName)
            }
            LockMode.PARTIAL_LOCK -> {
                // In Partial lock mode: Block chosen distraction apps, but permit quick utility lookups
                config.targetPackages.contains(packageName)
            }
        }
    }

    private fun isSystemInfrastructure(packageName: String): Boolean {
        return packageName.startsWith("com.android.systemui") ||
                packageName.startsWith("com.google.android.inputmethod") ||
                packageName.startsWith("com.samsung.android.honeyboard") ||
                packageName.contains("inputmethod") ||
                packageName == "android"
    }
}
