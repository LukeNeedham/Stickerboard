package com.lukeneedham.stickerboard.utilities

import com.elvishew.xlog.XLog
import java.util.Collections

/**
 * Collects warning messages raised while importing stickers (e.g. an unsupported file, or a pack
 * over the size limit) so they end up in the log. The app never shows these as toasts/pop-ups -
 * the UI's own inline state (a spinner, then updated counts) is the only user-facing feedback for
 * a sticker import.
 */
class Toaster {
	// synchronizedList since setMessage is called concurrently from multiple Dispatchers.IO
	// threads by StickerImporter while importing stickers
	var messages: MutableList<String> = Collections.synchronizedList(mutableListOf())

	/**
	 * Set a message
	 **/
	fun setMessage(message: String) {
		XLog.i("Adding message: '$message' to toaster")
		this.messages.add(message)
	}
}
