package com.lukeneedham.stickerboard.utilities

import java.io.File

interface StickerClickListener {
	fun onStickerClicked(sticker: File)
	fun onStickerLongClicked(sticker: File)
}
