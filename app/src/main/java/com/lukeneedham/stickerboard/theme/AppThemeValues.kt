package com.lukeneedham.stickerboard.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.font.FontFamily

/**
 * The colours and typeface the whole UI is drawn with. Light and dark are decided in [AppTheme]. There are no text styles:
 * each composable sets its own font size and line height, and only the typeface comes from here.
 */
@Immutable
class AppThemeValues(
	val colors: AppColors,
	val keyboardColors: KeyboardColors,
	val fontFamily: FontFamily,
)
