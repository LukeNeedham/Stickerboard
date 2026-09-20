package com.lukeneedham.stickerboard.keyboard

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Colors used by the keyboard IME itself - [KeyboardView]'s Compose UI, and KeyboardController's
 * window chrome (the IME window's own navigation bar, set directly rather than through Compose).
 * Deliberately independent of the settings app's Material 3 theme (settings/SettingsTheme.kt) so
 * restyling the settings UI can never change how the keyboard itself looks.
 */
object KeyboardColors {
	private val bgLight = Color(0xFFFAFDFC)
	private val bgDark = Color(0xFF191C1B)
	private val fgLight = Color(0xFF2D3131)
	private val fgDark = Color(0xFFE0E3E1)
	val accent = Color(0xFF0FA3A2)
	val onAccent = Color(0xFFE6E6E6)
	val pullHandle = Color(0x66888888)

	@Composable
	fun bg(): Color = if (isSystemInDarkTheme()) bgDark else bgLight

	@Composable
	fun fg(): Color = if (isSystemInDarkTheme()) fgDark else fgLight

	/**
	 * Non-Compose lookup for KeyboardController, which sets its IME window's navigation bar color
	 * directly on the window rather than through a composable.
	 */
	fun bg(context: Context): Color {
		val nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
		return if (nightMode == Configuration.UI_MODE_NIGHT_YES) bgDark else bgLight
	}
}
