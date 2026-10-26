package com.lukeneedham.stickerboard.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Colors used by the keyboard IME's own Compose UI ([com.lukeneedham.stickerboard.keyboard.KeyboardView]) - independent
 * of the [AppColors] the settings pages use, so restyling the settings UI can never change how the keyboard itself
 * looks.
 */
@Immutable
data class KeyboardColors(
	val bg: Color,
	val fg: Color,
	val accent: Color,
	val onAccent: Color,
	val pullHandle: Color,
) {
	companion object {
		// Public, not private, so KeyboardController can look up its IME window's navigation bar color directly -
		// that's set before onCreateInputView() builds any Compose UI, so it can't be read from AppTheme.
		val Light = KeyboardColors(
			bg = Color(0xFFFAFDFC),
			fg = Color(0xFF2D3131),
			accent = Color(0xFF0FA3A2),
			onAccent = Color(0xFFE6E6E6),
			pullHandle = Color(0x66888888),
		)

		val Dark = KeyboardColors(
			bg = Color(0xFF191C1B),
			fg = Color(0xFFE0E3E1),
			accent = Color(0xFF0FA3A2),
			onAccent = Color(0xFFE6E6E6),
			pullHandle = Color(0x66888888),
		)
	}
}
