package com.lukeneedham.stickerboard.theme

import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable

/** The colors of an outlined text field. */
@Composable
fun appTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
	focusedTextColor = AppTheme.colors.onSurface,
	unfocusedTextColor = AppTheme.colors.onSurface,
	errorTextColor = AppTheme.colors.onSurface,
	cursorColor = AppTheme.colors.accent,
	errorCursorColor = AppTheme.colors.error,
	focusedBorderColor = AppTheme.colors.accent,
	unfocusedBorderColor = AppTheme.colors.outline,
	errorBorderColor = AppTheme.colors.error,
	focusedLabelColor = AppTheme.colors.accent,
	unfocusedLabelColor = AppTheme.colors.textMuted,
	errorLabelColor = AppTheme.colors.error,
	focusedSupportingTextColor = AppTheme.colors.textMuted,
	unfocusedSupportingTextColor = AppTheme.colors.textMuted,
	errorSupportingTextColor = AppTheme.colors.error,
)
