package com.lukeneedham.stickerboard.keyboard

import com.lukeneedham.stickerboard.model.BoardItem
import java.io.File

/** A single icon in the keyboard's top pack-navigation bar. A null [thumbnail] is the "recent" icon. */
data class PackNavIcon(val packName: String, val thumbnail: File?)

/**
 * Bridges the Compose keyboard UI ([com.lukeneedham.stickerboard.keyboard.KeyboardScreen]) to
 * [com.lukeneedham.stickerboard.ImageKeyboard]'s pack/cache state and side effects (sending a
 * sticker, persisting preferences). The UI layer owns ephemeral view state (current mode, search
 * text, scroll position); this owns everything backed by disk/prefs.
 */
interface KeyboardDataSource {
	fun boardItems(): List<BoardItem>
	fun packNavIcons(): List<PackNavIcon>
	fun sectionIndex(packName: String): Int?
	fun sectionAt(itemIndex: Int): String?
	fun previousSection(current: String): String?
	fun nextSection(current: String): String?
	fun searchStickers(query: String): List<File>
	fun changeIconsPerX(delta: Int): Int

	/** Re-scan the sticker source directory from disk, picking up packs/stickers added since launch. */
	fun refreshStickers()
	fun onKeyboardHeightChanged(heightPx: Int)
	fun onKeyboardHeightSettled(heightPx: Int)
	fun onActivePackChanged(packName: String)
	fun onStickerSend(sticker: File)
	fun onClose()
}
