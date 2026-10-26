package com.lukeneedham.stickerboard.screenshot

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.lukeneedham.stickerboard.theme.AppTheme
import com.lukeneedham.stickerboard.settings.SettingsPageContent
import com.lukeneedham.stickerboard.settings.SettingsUiState

/** Rendered by CI to docs/screenshots/<position in order.txt>-SettingsPage.png: the home page with the keyboard enabled. */
@Preview
@Composable
private fun SettingsPageScreenshot() {
	AppTheme {
		SettingsPageContent(
			state = SettingsUiState(keyboardEnabled = true),
			onEnableKeyboard = {},
			onTryItOutMediaReceived = {},
			onViewStickers = {},
			onOpenDebug = {},
		)
	}
}
