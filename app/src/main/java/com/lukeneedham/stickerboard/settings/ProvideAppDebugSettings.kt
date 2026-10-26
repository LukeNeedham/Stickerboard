package com.lukeneedham.stickerboard.settings

import com.lukeneedham.stickerboard.theme.AppTheme
import androidx.compose.runtime.Composable
import com.lukeneedham.common.ui.DebugTheme
import com.lukeneedham.common.ui.ProvideDebugSettings

/**
 * [ProvideDebugSettings] with the shared library's debug pages themed to match the app's
 * theme. Place it inside the app's theme, since it reads the colours and font from there.
 */
@Composable
fun ProvideAppDebugSettings(content: @Composable () -> Unit) {
	val theme = AppTheme.current
	val colors = theme.colors
	ProvideDebugSettings(
		theme = DebugTheme(
			background = colors.background,
			onBackground = colors.onBackground,
			surface = colors.placeholder,
			onSurface = colors.textMuted,
			primary = colors.primaryButton.background,
			onPrimary = colors.primaryButton.content,
			outline = colors.outline,
			error = colors.error,
			warning = colors.warning,
			fontFamily = theme.fontFamily,
		),
		content = content,
	)
}
