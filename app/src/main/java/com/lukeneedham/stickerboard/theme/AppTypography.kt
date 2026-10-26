package com.lukeneedham.stickerboard.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** The text styles the app uses. */
@Immutable
data class AppTypography(
	val headlineSmall: TextStyle,
	val titleLarge: TextStyle,
	val titleMedium: TextStyle,
	val bodyLarge: TextStyle,
	val bodyMedium: TextStyle,
	val bodySmall: TextStyle,
	val labelLarge: TextStyle,
	val labelMedium: TextStyle,
	val labelSmall: TextStyle,
) {
	companion object {
		val Default = AppTypography(
			headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
			titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
			titleMedium = TextStyle(
				fontSize = 18.sp,
				lineHeight = 24.sp,
				letterSpacing = 0.15.sp,
				fontWeight = FontWeight.SemiBold,
			),
			bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.5.sp),
			bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp),
			bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
			labelLarge = TextStyle(
				fontSize = 14.sp,
				lineHeight = 20.sp,
				letterSpacing = 0.1.sp,
				fontWeight = FontWeight.Medium,
			),
			labelMedium = TextStyle(
				fontSize = 12.sp,
				lineHeight = 16.sp,
				letterSpacing = 0.5.sp,
				fontWeight = FontWeight.Medium,
			),
			labelSmall = TextStyle(
				fontSize = 11.sp,
				lineHeight = 16.sp,
				letterSpacing = 0.5.sp,
				fontWeight = FontWeight.Medium,
			),
		)
	}
}
