package com.lukeneedham.stickerboard.utilities

import java.util.concurrent.atomic.AtomicBoolean

/**
 * In-process flag marking that stickers were written to internal storage from somewhere other than
 * the keyboard itself - the Stickers page's "add photo" action, or an image shared into
 * StickerBoard. The running keyboard's [com.lukeneedham.stickerboard.keyboard.KeyboardModel]
 * instance persists for the whole lifetime of the IME service and otherwise has no way to notice
 * files that appear underneath it. Consumed each time the keyboard becomes visible, so newly added
 * stickers show up without the user having to pull-to-refresh inside the keyboard itself.
 */
object KeyboardRefreshSignal {
	private val dirty = AtomicBoolean(false)

	fun markDirty() {
		dirty.set(true)
	}

	/** Returns whether [markDirty] was called since the last [consumeDirty], clearing the flag. */
	fun consumeDirty(): Boolean = dirty.getAndSet(false)
}
