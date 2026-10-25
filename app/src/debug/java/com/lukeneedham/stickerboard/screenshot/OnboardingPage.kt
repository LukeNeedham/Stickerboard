package com.lukeneedham.stickerboard.screenshot

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.lukeneedham.stickerboard.settings.StickerBoardSettingsTheme
import com.lukeneedham.stickerboard.onboarding.OnboardingPageContent
import com.lukeneedham.stickerboard.onboarding.OnboardingUiState

/** Rendered by CI to docs/screenshots/<position in order.txt>-OnboardingPage.png: the first-run setup, before the keyboard is enabled. */
@Preview
@Composable
private fun OnboardingPageScreenshot() {
	StickerBoardSettingsTheme {
		OnboardingPageContent(
			state = OnboardingUiState(keyboardEnabled = false, isImporting = false, loadedStickerCount = null),
			onEnableKeyboard = {},
			onChooseDir = {},
			onFinish = {},
		)
	}
}
