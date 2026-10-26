package com.lukeneedham.stickerboard.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontFamily

private val LocalAppTheme = staticCompositionLocalOf<AppThemeValues> { error("No AppTheme provided") }

/** Reads the values of the current [AppTheme]. There is no `MaterialTheme`: every Material component is given its colours. */
object AppTheme {
	val current: AppThemeValues
		@Composable
		get() = LocalAppTheme.current
}

/**
 * The app's one theme, light or dark as the system is: it provides the [AppColors] of the settings pages, the
 * [KeyboardColors] of the keyboard and the typeface, plus the defaults of the composition (content colour, ripple, text
 * selection) that `MaterialTheme` would otherwise set. Installed at the root of both the keyboard IME's own UI
 * ([com.lukeneedham.stickerboard.keyboard.KeyboardView]) and the settings app's UI ([com.lukeneedham.stickerboard.Root]).
 */
@Composable
fun AppTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
	val values = AppThemeValues(
		colors = if (darkTheme) AppColors.Dark else AppColors.Light,
		keyboardColors = if (darkTheme) KeyboardColors.Dark else KeyboardColors.Light,
		fontFamily = FontFamily.Default,
	)
	val indication = ripple()
	CompositionLocalProvider(
		LocalAppTheme provides values,
		LocalContentColor provides values.colors.onBackground,
		LocalIndication provides indication,
		LocalTextSelectionColors provides TextSelectionColors(
			handleColor = values.colors.accent,
			backgroundColor = values.colors.accent.copy(alpha = 0.4f),
		),
		content = content,
	)
}
