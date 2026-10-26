package com.lukeneedham.stickerboard.screenshot

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.lukeneedham.stickerboard.theme.AppTheme
import com.lukeneedham.stickerboard.share.ShareImportPageContent

/** Rendered by CI to docs/screenshots/<position in order.txt>-ShareImportPage.png: choosing a pack for a shared image. */
@Preview
@Composable
private fun ShareImportPageScreenshot() {
	AppTheme {
		ShareImportPageContent(
			imageUris = emptyList(),
			packNames = listOf("Cats", "Memes", "Reactions"),
			onBack = {},
			onPackSelected = {},
		)
	}
}
