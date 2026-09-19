package com.lukeneedham.stickerboard.settings

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
* A modern Material 3 tonal palette for the settings app (MainActivity and the pages it opens),
* seeded from the app's teal brand color but kept independent of the raw color/dimen resources the
* keyboard IME itself reads (color/accent, color/bg, color/fg, color/onAccent, dimen/corner) so
* restyling the settings UI can never change how the keyboard looks.
*/
private val LightColors = lightColorScheme(
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

private val DarkColors = darkColorScheme(
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

private val SettingsTypography = Typography().let { base ->
	base.copy(
		headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
		titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
		titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
		bodyLarge = base.bodyLarge.copy(lineHeight = 22.sp),
	)
}

/** Wraps settings-app content in its own modern Material 3 theme, light/dark aware. */
@Composable
fun StickerBoardSettingsTheme(content: @Composable () -> Unit) {
	MaterialTheme(
		colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
		typography = SettingsTypography,
		content = content,
	)
}
