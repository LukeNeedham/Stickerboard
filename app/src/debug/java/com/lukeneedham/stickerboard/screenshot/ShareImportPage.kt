package com.lukeneedham.stickerboard.screenshot

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.lukeneedham.stickerboard.settings.StickerBoardSettingsTheme
import com.lukeneedham.stickerboard.share.ShareImportPageContent

/** Rendered by CI to docs/screenshots/<position in order.txt>-ShareImportPage.png: choosing a pack for a shared image. */
@Preview
@Composable
private fun ShareImportPageScreenshot() {
	StickerBoardSettingsTheme {
		ShareImportPageContent(
			imageUris = emptyList(),
			packNames = listOf("Cats", "Memes", "Reactions"),
			onBack = {},
			onPackSelected = {},
		)
	}
}
