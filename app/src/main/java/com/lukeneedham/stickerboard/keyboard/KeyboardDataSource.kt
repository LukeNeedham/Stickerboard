package com.lukeneedham.stickerboard.keyboard

import androidx.compose.runtime.State
import com.lukeneedham.stickerboard.model.BoardItem
import java.io.File

/** A single icon in the keyboard's top pack-navigation bar. A null [thumbnail] is the "recent" icon. */
data class PackNavIcon(val packName: String, val thumbnail: File?)

/**
 * Bridges the Compose "View" ([KeyboardScreen]) to the "Controller"
 * ([com.lukeneedham.stickerboard.ImageKeyboard]), which implements this interface by reading from
 * and delegating to [KeyboardModel] (the "Model") and its own InputMethodService-only side effects
 * (sending a sticker, closing the keyboard). The View owns ephemeral view state of its own (current
 * mode, search text, scroll position); this interface is the boundary for everything backed by
 * disk/prefs instead.
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

	/**
	 * Re-scan stickers from disk, picking up packs/stickers added since launch. If a sticker source
	 * directory is configured (Settings > Choose sticker source directory) and its contents have
	 * changed since it was last imported, this first re-imports from it - mirroring the settings
	 * screen's "Reload stickers" action - before rescanning.
	 */
	suspend fun refreshStickers()
	fun onKeyboardHeightChanged(heightPx: Int)
	fun onKeyboardHeightSettled(heightPx: Int)
	fun onActivePackChanged(packName: String)
	fun onStickerSend(sticker: File)
	fun onClose()

	/** Open the app's settings screen, e.g. from a button in the keyboard's pull bar. */
	fun onOpenSettings()

	/**
	 * Transient status text (e.g. "Cannot send image") to show as a snackbar-style banner over the
	 * keyboard, or null when none is pending.
	 */
	val statusMessage: State<String?>

	/** Dismiss the current [statusMessage], e.g. once its on-screen timeout elapses. */
	fun onStatusMessageShown()
}
