package com.lukeneedham.stickerboard.theme

import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable

/** The colors of an outlined text field. */
@Composable
fun appTextFieldColors(): TextFieldColors {
	val colors = AppTheme.current.colors
	return OutlinedTextFieldDefaults.colors(
		focusedTextColor = colors.onSurface,
		unfocusedTextColor = colors.onSurface,
		errorTextColor = colors.onSurface,
		cursorColor = colors.accent,
		errorCursorColor = colors.error,
		focusedBorderColor = colors.accent,
		unfocusedBorderColor = colors.outline,
		errorBorderColor = colors.error,
		focusedLabelColor = colors.accent,
		unfocusedLabelColor = colors.textMuted,
		errorLabelColor = colors.error,
		focusedSupportingTextColor = colors.textMuted,
		unfocusedSupportingTextColor = colors.textMuted,
		errorSupportingTextColor = colors.error,
	)
}
