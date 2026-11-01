package com.focuslock.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class AppWhitelistItem(
    val packageName: String,
    val appName: String,
    val isEssential: Boolean = false, // Phone dialer, FocusLock
    val isEnabled: Boolean = false,
    val category: String = "Productivity" // e.g. "Core Safety", "Study Tools", "Reference"
) : Parcelable
