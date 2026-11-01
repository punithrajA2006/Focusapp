package com.focuslock.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * State of the FocusLock enforcement system.
 */
enum class SessionStatus {
    IDLE,
    ACTIVE,
    COMPLETED,
    STOPPED_BY_LAPTOP
}

/**
 * Configuration selected by the user to start a session.
 */
@Parcelize
data class SessionConfig(
    val durationMinutes: Int = 60,
    val allowedPackages: Set<String> = defaultAllowedPackages,
    val pomodoroEnabled: Boolean = false,
    val studyIntervalMinutes: Int = 50,
    val breakIntervalMinutes: Int = 10,
    val breakAllowedPackages: Set<String> = emptySet(),
    val youTubeFilterConfig: YouTubeFilterConfig = YouTubeFilterConfig()
) : Parcelable {
    companion object {
        val defaultAllowedPackages = setOf(
            "com.focuslock",                  // FocusLock itself
            "com.android.dialer",             // Safety: Phone dialer
            "com.google.android.dialer",      // Safety: Pixel/OEM dialer
            "com.samsung.android.dialer",     // Safety: Samsung dialer
            "com.android.server.telecom",     // Safety: Telecom subsystem
            "com.android.phone"               // Safety: Core phone app
        )
    }
}

/**
 * Persisted state of an active session using monotonic timestamps.
 */
@Parcelize
data class ActiveSession(
    val sessionId: String,
    val startTimeWallMs: Long,
    val startElapsedRealtimeMs: Long,
    val expectedDurationMs: Long,
    val targetEndElapsedRealtimeMs: Long,
    val lastVerifiedRealtimeMs: Long,
    val accumulatedElapsedBeforeRebootMs: Long = 0L,
    val rebootCount: Int = 0,
    val status: SessionStatus = SessionStatus.ACTIVE,
    val config: SessionConfig = SessionConfig()
) : Parcelable {

    /**
     * Calculates remaining time in milliseconds using monotonic elapsed time.
     * Prevents shortening even if system wall clock is jumped.
     */
    fun remainingTimeMs(currentElapsedRealtimeMs: Long): Long {
        if (status != SessionStatus.ACTIVE) return 0L
        val elapsedInCurrentBoot = (currentElapsedRealtimeMs - startElapsedRealtimeMs).coerceAtLeast(0L)
        val totalElapsed = accumulatedElapsedBeforeRebootMs + elapsedInCurrentBoot
        return (expectedDurationMs - totalElapsed).coerceAtLeast(0L)
    }

    val isExpired: Boolean
        get() = remainingTimeMs(android.os.SystemClock.elapsedRealtime()) <= 0L
}

/**
 * History record for completed or laptop-stopped sessions.
 */
data class SessionRecord(
    val id: String,
    val dateEpochMs: Long,
    val durationMinutes: Int,
    val wasCompletedSuccessfully: Boolean,
    val stoppedByLaptop: Boolean,
    val totalFocusedMinutes: Int
)
