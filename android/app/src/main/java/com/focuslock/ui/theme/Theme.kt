package com.focuslock.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val DarkSlateBg = Color(0xFF090D16)
val SurfaceCardBg = Color(0xFF131B2E)
val BorderHighlight = Color(0xFF222F4C)
val ElectricBlue = Color(0xFF38BDF8)
val AccentEmerald = Color(0xFF10B981)
val DangerCrimson = Color(0xFFEF4444)
val WarningAmber = Color(0xFFF59E0B)
val TextPrimary = Color(0xFFF8FAFC)
val TextMuted = Color(0xFF94A3B8)

private val FocusLockColorScheme = darkColorScheme(
    primary = ElectricBlue,
    onPrimary = Color.Black,
    surface = SurfaceCardBg,
    onSurface = TextPrimary,
    background = DarkSlateBg,
    onBackground = TextPrimary,
    error = DangerCrimson,
    onError = Color.White
)

@Composable
fun FocusLockTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = FocusLockColorScheme,
        content = content
    )
}
