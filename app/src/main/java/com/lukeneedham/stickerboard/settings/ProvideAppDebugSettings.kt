package com.lukeneedham.stickerboard.settings

import com.lukeneedham.stickerboard.theme.AppTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import com.lukeneedham.common.ui.DebugTheme
import com.lukeneedham.common.ui.ProvideDebugSettings

/**
 * [ProvideDebugSettings] with the shared library's debug pages themed to match the app's
 * theme. Place it inside the app's theme, since it reads the colours and font from there.
 */
@Composable
fun ProvideAppDebugSettings(content: @Composable () -> Unit) {
	val colors = AppTheme.colors
	ProvideDebugSettings(
		theme = DebugTheme(
			background = colors.background,
			onBackground = colors.onBackground,
			surface = colors.placeholder,
			onSurface = colors.textMuted,
			primary = colors.accent,
			onPrimary = colors.onAccent,
			outline = colors.outline,
			error = colors.error,
			warning = colors.warning,
			fontFamily = AppTheme.typography.bodyMedium.fontFamily ?: FontFamily.Default,
		),
		content = content,
	)
}
