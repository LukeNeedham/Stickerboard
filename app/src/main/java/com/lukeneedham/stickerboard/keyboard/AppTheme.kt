package com.lukeneedham.stickerboard.keyboard

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Colors used by the keyboard IME's own Compose UI ([KeyboardView]) - independent of the settings
 * app's Material 3 theme (settings/SettingsTheme.kt) so restyling the settings UI can never change
 * how the keyboard itself looks.
 */
data class Colors(
	val bg: Color,
	val fg: Color,
	val accent: Color,
	val onAccent: Color,
	val pullHandle: Color,
)

// Exposed at internal (module) visibility, not private, so KeyboardController can look up its IME
// window's navigation bar color directly - that's set before onCreateInputView() builds any
// Compose UI, so it can't be read from LocalAppTheme.
internal val LightColors = Colors(
	bg = Color(0xFFFAFDFC),
	fg = Color(0xFF2D3131),
	accent = Color(0xFF0FA3A2),
	onAccent = Color(0xFFE6E6E6),
	pullHandle = Color(0x66888888),
)

internal val DarkColors = Colors(
	bg = Color(0xFF191C1B),
	fg = Color(0xFFE0E3E1),
	accent = Color(0xFF0FA3A2),
	onAccent = Color(0xFFE6E6E6),
	pullHandle = Color(0x66888888),
)

/**
 * Defaults to [LightColors], so a composable read outside [AppTheme] - a `@Preview`, most likely -
 * gets a sensible value instead of crashing.
 */
val LocalAppTheme = staticCompositionLocalOf { LightColors }

/** Provides [LocalAppTheme], light/dark aware. Installed at the root of both the keyboard IME's own
 * UI ([KeyboardView]) and the settings app's UI ([com.lukeneedham.stickerboard.StickerBoardApp]). */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
	val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
	CompositionLocalProvider(LocalAppTheme provides colors, content = content)
}
