package com.fredhappyface.ewesticker.model

import java.io.File

/** A single row in the unified, vertically-scrolling sticker board. */
sealed class BoardItem {
	/** A full-width section header naming the pack that follows it. */
	data class Header(val packName: String, val displayName: String) : BoardItem()

	/** A single sticker belonging to [packName]. */
	data class Sticker(val file: File, val packName: String) : BoardItem()
}
