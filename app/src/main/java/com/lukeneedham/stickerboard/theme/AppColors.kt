package com.lukeneedham.stickerboard.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * The colors of the settings pages and everything else that is not the keyboard, named by what they are used for. A
 * palette seeded from the app's teal brand color, kept independent of [KeyboardColors] so restyling the settings UI can
 * never change how the keyboard looks.
 *
 * @property background the page background, and [onBackground] the text and icons on it.
 * @property surface cards, sheets and dialogs, and [onSurface] the text on them.
 * @property placeholder the fill behind thumbnails, pack tiles and other boxes on a [surface] or [background].
 * @property textMuted secondary text and icons.
 * @property accent the brand color: icons, progress, headings and the primary button, with [onAccent] on top of it.
 * @property tonal the quieter button fill, with [onTonal] on top of it.
 * @property outline the border of a text field.
 * @property error destructive actions and errors.
 * @property warning warnings (only the shared debug pages show it).
 * @property scrim what dims the page behind a sheet.
 */
@Immutable
data class AppColors(
	val background: Color,
	val onBackground: Color,
	val surface: Color,
	val onSurface: Color,
	val placeholder: Color,
	val textMuted: Color,
	val accent: Color,
	val onAccent: Color,
	val tonal: Color,
	val onTonal: Color,
	val outline: Color,
	val error: Color,
	val warning: Color,
	val scrim: Color,
) {
	companion object {
		val Light = AppColors(
			background = Color(0xFFF4FAF9),
			onBackground = Color(0xFF171D1C),
			surface = Color(0xFFFFFFFF),
			onSurface = Color(0xFF171D1C),
			placeholder = Color(0xFFDAE5E3),
			textMuted = Color(0xFF3F4948),
			accent = Color(0xFF00696C),
			onAccent = Color(0xFFFFFFFF),
			tonal = Color(0xFFCCE8E7),
			onTonal = Color(0xFF051F1F),
			outline = Color(0xFF6F7978),
			error = Color(0xFFBA1A1A),
			warning = Color(0xFF7D5260),
			scrim = Color(0x52000000),
		)

		val Dark = AppColors(
			background = Color(0xFF0E1514),
			onBackground = Color(0xFFDDE4E2),
			surface = Color(0xFF151C1B),
			onSurface = Color(0xFFDDE4E2),
			placeholder = Color(0xFF3F4948),
			textMuted = Color(0xFFBFC9C7),
			accent = Color(0xFF83D4D3),
			onAccent = Color(0xFF00373A),
			tonal = Color(0xFF324B4B),
			onTonal = Color(0xFFCCE8E7),
			outline = Color(0xFF899391),
			error = Color(0xFFFFB4AB),
			warning = Color(0xFFEFB8C8),
			scrim = Color(0x52000000),
		)
	}
}
