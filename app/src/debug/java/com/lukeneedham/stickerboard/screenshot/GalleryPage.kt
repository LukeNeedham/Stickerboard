package com.lukeneedham.stickerboard.screenshot

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.lukeneedham.stickerboard.settings.StickerBoardSettingsTheme
import com.lukeneedham.stickerboard.gallery.GalleryPageContent
import com.lukeneedham.stickerboard.model.BoardItem

/** Rendered by CI to docs/screenshots/<position in order.txt>-GalleryPage.png: the sticker gallery with two packs. */
@Preview
@Composable
private fun GalleryPageScreenshot() {
	StickerBoardSettingsTheme {
		GalleryPageContent(
			items = listOf(
				BoardItem.Header("Cats", "Cats"),
				BoardItem.EmptyMessage("Cats", "No stickers in this pack"),
				BoardItem.AddPhoto("Cats"),
				BoardItem.Header("Memes", "Memes"),
				BoardItem.EmptyMessage("Memes", "No stickers in this pack"),
				BoardItem.AddPhoto("Memes"),
			),
			columns = 3,
			stickerDirDisplayName = "Stickers",
			lastUpdateDate = "Today",
			isRefreshing = false,
			onBack = {},
			onOpenFolder = {},
			onOpenPackFolder = {},
			onChangeDirectory = {},
			onRefresh = {},
			onAddPhotoClick = {},
			onDeleteStickers = {},
			onRenameSticker = { _, _, _ -> },
			deletingStickers = emptySet(),
		)
	}
}
