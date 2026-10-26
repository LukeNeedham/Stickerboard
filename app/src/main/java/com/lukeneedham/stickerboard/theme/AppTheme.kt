package com.lukeneedham.stickerboard.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Colors used by the keyboard IME's own Compose UI ([com.lukeneedham.stickerboard.keyboard.KeyboardView]) - independent
 * of the Material 3 color scheme the settings pages use (also set up by [AppTheme]) so restyling the settings UI can
 * never change how the keyboard itself looks.
 */
data class Colors(
	val bg: Color,
	val fg: Color,
	val accent: Color,
	val onAccent: Color,
	val pullHandle: Color,
)

internal object AppThemeConstants {
	// Exposed at internal (module) visibility, not private, so KeyboardController can look up its IME
	// window's navigation bar color directly - that's set before onCreateInputView() builds any
	// Compose UI, so it can't be read from LocalAppTheme.
	val LightColors = Colors(
		bg = Color(0xFFFAFDFC),
		fg = Color(0xFF2D3131),
		accent = Color(0xFF0FA3A2),
		onAccent = Color(0xFFE6E6E6),
		pullHandle = Color(0x66888888),
	)

	val DarkColors = Colors(
		bg = Color(0xFF191C1B),
		fg = Color(0xFFE0E3E1),
		accent = Color(0xFF0FA3A2),
		onAccent = Color(0xFFE6E6E6),
		pullHandle = Color(0x66888888),
	)

	/**
	 * A modern Material 3 tonal palette for the settings pages, seeded from the app's teal brand color but kept
	 * independent of the keyboard [Colors] above, so restyling the settings UI can never change how the keyboard looks.
	 */
	val MaterialLightColors = lightColorScheme(
		primary = Color(0xFF00696C),
		onPrimary = Color(0xFFFFFFFF),
		primaryContainer = Color(0xFF9EEFF1),
		onPrimaryContainer = Color(0xFF002022),
		secondary = Color(0xFF4A6363),
		onSecondary = Color(0xFFFFFFFF),
		secondaryContainer = Color(0xFFCCE8E7),
		onSecondaryContainer = Color(0xFF051F1F),
		background = Color(0xFFF4FAF9),
		onBackground = Color(0xFF171D1C),
		surface = Color(0xFFFFFFFF),
		onSurface = Color(0xFF171D1C),
		surfaceVariant = Color(0xFFDAE5E3),
		onSurfaceVariant = Color(0xFF3F4948),
		outline = Color(0xFF6F7978),
		outlineVariant = Color(0xFFBEC9C7),
		error = Color(0xFFBA1A1A),
		onError = Color(0xFFFFFFFF),
		errorContainer = Color(0xFFFFDAD6),
		onErrorContainer = Color(0xFF410002),
	)

	val MaterialDarkColors = darkColorScheme(
		primary = Color(0xFF83D4D3),
		onPrimary = Color(0xFF00373A),
		primaryContainer = Color(0xFF004F52),
		onPrimaryContainer = Color(0xFF9EEFF1),
		secondary = Color(0xFFB0CCCB),
		onSecondary = Color(0xFF1B3534),
		secondaryContainer = Color(0xFF324B4B),
		onSecondaryContainer = Color(0xFFCCE8E7),
		background = Color(0xFF0E1514),
		onBackground = Color(0xFFDDE4E2),
		surface = Color(0xFF151C1B),
		onSurface = Color(0xFFDDE4E2),
		surfaceVariant = Color(0xFF3F4948),
		onSurfaceVariant = Color(0xFFBFC9C7),
		outline = Color(0xFF899391),
		outlineVariant = Color(0xFF3F4948),
		error = Color(0xFFFFB4AB),
		onError = Color(0xFF690005),
		errorContainer = Color(0xFF93000A),
		onErrorContainer = Color(0xFFFFDAD6),
	)

	val MaterialTypography = Typography().let { base ->
		base.copy(
			headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
			titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
			titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
			bodyLarge = base.bodyLarge.copy(lineHeight = 22.sp),
		)
	}
}

/**
 * Defaults to [AppThemeConstants.LightColors], so a composable read outside [AppTheme] (a `@Preview`,
 * most likely) gets a sensible value instead of crashing.
 */
val LocalAppTheme = staticCompositionLocalOf { AppThemeConstants.LightColors }

/** Reads the values of the current [AppTheme]. */
object AppTheme {
	/** The keyboard colors, light/dark aware. */
	val colors: Colors
		@Composable
		@ReadOnlyComposable
		get() = LocalAppTheme.current
}

/**
 * The app's one theme, light/dark aware: it provides the keyboard [Colors] (read through [AppTheme.colors]) and the
 * Material 3 theme the settings pages' Material components use. Installed at the root of both the keyboard IME's own UI
 * ([com.lukeneedham.stickerboard.keyboard.KeyboardView]) and the settings app's UI ([com.lukeneedham.stickerboard.Root]).
 */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
	val dark = isSystemInDarkTheme()
	val colors = if (dark) AppThemeConstants.DarkColors else AppThemeConstants.LightColors
	CompositionLocalProvider(LocalAppTheme provides colors) {
		MaterialTheme(
			colorScheme = if (dark) AppThemeConstants.MaterialDarkColors else AppThemeConstants.MaterialLightColors,
			typography = AppThemeConstants.MaterialTypography,
			content = content,
		)
	}
}
