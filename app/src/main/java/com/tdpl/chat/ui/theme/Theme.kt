package com.tdpl.chat.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// Deliberately dark-only: the app's identity is a low-light "ink and ember"
// reading room, not a light/dark toggle on top of default Material tones.
private val AppColorScheme = darkColorScheme(
    primary = EmberCore,
    onPrimary = InkVoid,
    secondary = SignalCore,
    onSecondary = InkVoid,
    background = InkVoid,
    onBackground = TextPrimary,
    surface = InkSurface,
    onSurface = TextPrimary,
    surfaceVariant = InkSurfaceRaised,
    onSurfaceVariant = TextSecondary,
    outline = InkBorder,
    error = DangerCore,
)

@Composable
fun TDPLChatTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography = AppTypography,
        content = content
    )
}
