package com.lukeneedham.stickerboard.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** The colours of one button style; the same four apply to every button of that style. */
@Immutable
class AppButtonColors(
	val background: Color,
	val content: Color,
	val disabledBackground: Color,
	val disabledContent: Color,
)

/**
 * Every colour the settings pages and everything else that is not the keyboard use, named for where it is used. A palette
 * seeded from the app's teal brand color, kept independent of [KeyboardColors] so restyling the settings UI can never change
 * how the keyboard looks. Nothing reads a generic slot: a new use gets a new name here.
 */
@Immutable
class AppColors(
	/** The page behind everything, and the text and icons on it. */
	val background: Color,
	val onBackground: Color,
	/** Cards, sheets and dialogs, and the text on them. */
	val surface: Color,
	val onSurface: Color,
	/** The fill behind thumbnails, pack tiles and other boxes on a [surface] or [background]. */
	val placeholder: Color,
	/** Secondary text and icons. */
	val textMuted: Color,
	/** The brand color: icons, progress and headings. */
	val accent: Color,
	/** The border of a text field. */
	val outline: Color,
	val error: Color,
	/** Warnings (only the shared debug pages show it). */
	val warning: Color,
	/** Dims the page behind a sheet. */
	val scrim: Color,

	/** The main action on a page. */
	val primaryButton: AppButtonColors,
	/** Everything else that can be pressed. */
	val secondaryButton: AppButtonColors,
	/** A quiet action with no fill. */
	val textButton: AppButtonColors,
	/** A quiet action that deletes something. */
	val destructiveButton: AppButtonColors,
) {
	fun button(style: AppButtonStyle): AppButtonColors = when (style) {
		AppButtonStyle.Primary -> primaryButton
		AppButtonStyle.Secondary -> secondaryButton
		AppButtonStyle.Text -> textButton
		AppButtonStyle.Destructive -> destructiveButton
	}

	companion object {
		private fun button(background: Color, content: Color, onSurface: Color) = AppButtonColors(
			background = background,
			content = content,
			disabledBackground = if (background == Color.Transparent) background else onSurface.copy(alpha = 0.12f),
			disabledContent = onSurface.copy(alpha = 0.38f),
		)

		val Light = Color(0xFF171D1C).let { onSurface ->
			AppColors(
				background = Color(0xFFF4FAF9),
				onBackground = onSurface,
				surface = Color(0xFFFFFFFF),
				onSurface = onSurface,
				placeholder = Color(0xFFDAE5E3),
				textMuted = Color(0xFF3F4948),
				accent = Color(0xFF00696C),
				outline = Color(0xFF6F7978),
				error = Color(0xFFBA1A1A),
				warning = Color(0xFF7D5260),
				scrim = Color(0x52000000),
				primaryButton = button(Color(0xFF00696C), Color(0xFFFFFFFF), onSurface),
				secondaryButton = button(Color(0xFFCCE8E7), Color(0xFF051F1F), onSurface),
				textButton = button(Color.Transparent, Color(0xFF00696C), onSurface),
				destructiveButton = button(Color.Transparent, Color(0xFFBA1A1A), onSurface),
			)
		}

		val Dark = Color(0xFFDDE4E2).let { onSurface ->
			AppColors(
				background = Color(0xFF0E1514),
				onBackground = onSurface,
				surface = Color(0xFF151C1B),
				onSurface = onSurface,
				placeholder = Color(0xFF3F4948),
				textMuted = Color(0xFFBFC9C7),
				accent = Color(0xFF83D4D3),
				outline = Color(0xFF899391),
				error = Color(0xFFFFB4AB),
				warning = Color(0xFFEFB8C8),
				scrim = Color(0x52000000),
				primaryButton = button(Color(0xFF83D4D3), Color(0xFF00373A), onSurface),
				secondaryButton = button(Color(0xFF324B4B), Color(0xFFCCE8E7), onSurface),
				textButton = button(Color.Transparent, Color(0xFF83D4D3), onSurface),
				destructiveButton = button(Color.Transparent, Color(0xFFFFB4AB), onSurface),
			)
		}
	}
}
