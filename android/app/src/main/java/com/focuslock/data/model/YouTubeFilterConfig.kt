package com.focuslock.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Rules and evaluation engine for the in-app study YouTube browser.
 */
@Parcelize
data class YouTubeFilterConfig(
    val isEnabled: Boolean = true,
    val allowedChannelIds: Set<String> = setOf(
        "UC_x5XG1OV2P6uZZ5FSM9Ttw", // Google Developers
        "UCsBjURrPoezykLs9EqgamOA", // Fireship
        "UCWN3xxRkmTPmbKwht9FuE5A", // 3Blue1Brown
        "UCEBb1b_L6zDS3xTUrIALZOw", // MIT OpenCourseWare
        "UC4a-Gbdw7vOaccHmFo40b9g"  // Khan Academy
    ),
    val allowedKeywords: Set<String> = setOf(
        "lecture", "tutorial", "algorithm", "calculus", "machine learning",
        "deep learning", "kotlin", "linux", "compiler", "data structure",
        "python", "operating systems", "linear algebra"
    ),
    val blockedKeywords: Set<String> = setOf(
        "shorts", "vlog", "gaming", "prank", "reaction", "trailer",
        "entertainment", "meme", "gossip", "unboxing", "tiktok"
    ),
    val blockShorts: Boolean = true,
    val blockComments: Boolean = true,
    val blockRecommendations: Boolean = true
) : Parcelable {

    /**
     * Evaluates whether a video is strictly study-compliant.
     */
    fun isVideoAllowed(title: String, channelId: String? = null, isShort: Boolean = false): Boolean {
        if (!isEnabled) return false

        // 1. Shorts check
        if (isShort && blockShorts) return false

        val lowerTitle = title.lowercase()

        // 2. Blocked keywords check (e.g. gaming, prank)
        if (blockedKeywords.any { lowerTitle.contains(it) }) {
            return false
        }

        // 3. Approved channel check
        if (channelId != null && allowedChannelIds.contains(channelId)) {
            return true
        }

        // 4. Approved academic keyword match
        return allowedKeywords.any { lowerTitle.contains(it) }
    }
}
