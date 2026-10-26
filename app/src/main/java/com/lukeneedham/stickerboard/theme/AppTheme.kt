package com.lukeneedham.stickerboard.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/** Defaults to the light values, so a composable read outside [AppTheme] (a `@Preview`, most likely) gets a sensible value instead of crashing. */
private val LocalAppColors = staticCompositionLocalOf { AppColors.Light }

private val LocalAppTypography = staticCompositionLocalOf { AppTypography.Default }

private val LocalKeyboardColors = staticCompositionLocalOf { KeyboardColors.Light }

/** Reads the values of the current [AppTheme]. There is no `MaterialTheme`: every Material component is given its colors. */
object AppTheme {
	/** The colors of the settings pages and everything else that is not the keyboard, light/dark aware. */
	val colors: AppColors
		@Composable
		@ReadOnlyComposable
		get() = LocalAppColors.current

	/** The text styles of the settings pages and everything else that is not the keyboard. */
	val typography: AppTypography
		@Composable
		@ReadOnlyComposable
		get() = LocalAppTypography.current

	/** The keyboard colors, light/dark aware. */
	val keyboardColors: KeyboardColors
		@Composable
		@ReadOnlyComposable
		get() = LocalKeyboardColors.current
}

/**
 * The app's one theme, light/dark aware: it provides the [AppColors] and [AppTypography] of the settings pages and the
 * [KeyboardColors] of the keyboard, plus the defaults of the composition (text color and style, ripple, text selection)
 * that `MaterialTheme` would otherwise set. Installed at the root of both the keyboard IME's own UI
 * ([com.lukeneedham.stickerboard.keyboard.KeyboardView]) and the settings app's UI ([com.lukeneedham.stickerboard.Root]).
 */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
	val dark = isSystemInDarkTheme()
	val colors = if (dark) AppColors.Dark else AppColors.Light
	val keyboardColors = if (dark) KeyboardColors.Dark else KeyboardColors.Light
	val typography = AppTypography.Default
	val indication = ripple()
	CompositionLocalProvider(
		LocalAppColors provides colors,
		LocalAppTypography provides typography,
		LocalKeyboardColors provides keyboardColors,
		LocalContentColor provides colors.onBackground,
		LocalIndication provides indication,
		LocalTextSelectionColors provides TextSelectionColors(
			handleColor = colors.accent,
			backgroundColor = colors.accent.copy(alpha = 0.4f),
		),
	) {
		ProvideTextStyle(typography.bodyLarge, content)
	}
}
