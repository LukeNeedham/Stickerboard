package com.lukeneedham.stickerboard.theme

import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable

/** The colors of a filled button. */
@Composable
fun appButtonColors(): ButtonColors = ButtonDefaults.buttonColors(
	containerColor = AppTheme.colors.accent,
	contentColor = AppTheme.colors.onAccent,
	disabledContainerColor = AppTheme.colors.onSurface.copy(alpha = 0.12f),
	disabledContentColor = AppTheme.colors.onSurface.copy(alpha = 0.38f),
)

/** The colors of a tonal button. */
@Composable
fun appTonalButtonColors(): ButtonColors = ButtonDefaults.filledTonalButtonColors(
	containerColor = AppTheme.colors.tonal,
	contentColor = AppTheme.colors.onTonal,
	disabledContainerColor = AppTheme.colors.onSurface.copy(alpha = 0.12f),
	disabledContentColor = AppTheme.colors.onSurface.copy(alpha = 0.38f),
)

/** The colors of a text button. */
@Composable
fun appTextButtonColors(): ButtonColors = ButtonDefaults.textButtonColors(
	contentColor = AppTheme.colors.accent,
	disabledContentColor = AppTheme.colors.onSurface.copy(alpha = 0.38f),
)
