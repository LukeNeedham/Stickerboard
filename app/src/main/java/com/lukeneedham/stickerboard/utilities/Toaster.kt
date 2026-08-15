package com.lukeneedham.stickerboard.utilities

import android.content.Context
import android.widget.Toast
import com.elvishew.xlog.XLog
import java.util.Collections

/**
 * The Toaster class provides a simplified interface to android.widget.Toast. Pass in the
 * android.content.Context to the constructor and call the 'toast' function (others as below)
 * toaster.state keeps track of an error state or similar.
 *
 * @property context: android.content.Context. e.g. baseContext
 */
class Toaster(private val context: Context) {
	// synchronizedList since setMessage is called concurrently from multiple Dispatchers.IO
	// threads by StickerImporter while importing stickers
	var messages: MutableList<String> = Collections.synchronizedList(mutableListOf())

	/**
	 * Call toaster.toast with some string to always create a toast notification. Context is set when
	 * Toaster is instantiated. Duration is determined based on text length
	 *
	 * @param string: String. Message to output
	 */
	fun toast(string: String) {
		Toast.makeText(
			this.context,
			string,
			if (string.length > 60) {
				Toast.LENGTH_LONG
			} else {
				Toast.LENGTH_SHORT
			},
		)
			.show()
	}

	/**
	 *
	 **/
	fun toastOnMessages() {
		XLog.i("Messages: [${this.messages.joinToString(", ")}]")
		for (idx in this.messages.take(3).indices) {
			this.toast(messages[idx])
		}
		this.messages = Collections.synchronizedList(mutableListOf())
	}

	/**
	 * Set a message
	 **/
	fun setMessage(message: String) {
		XLog.i("Adding message: '$message' to toaster")
		this.messages.add(message)
	}
}
