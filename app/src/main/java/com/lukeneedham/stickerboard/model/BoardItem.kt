package com.lukeneedham.stickerboard.model

import java.io.File

/** A single row in the unified, vertically-scrolling sticker board. */
sealed class BoardItem {
	/** A full-width section header naming the pack that follows it. */
	data class Header(val packName: String, val displayName: String) : BoardItem()

	/** A single sticker belonging to [packName]. */
	data class Sticker(val file: File, val packName: String) : BoardItem()

	/** A full-width placeholder shown in [packName]'s section when it has no stickers. */
	data class EmptyMessage(val packName: String, val message: String) : BoardItem()

	/** A grid cell, shown last in [packName]'s section, that lets the user add more stickers to it. */
	data class AddPhoto(val packName: String) : BoardItem()
}
