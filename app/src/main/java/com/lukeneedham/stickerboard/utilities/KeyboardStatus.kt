package com.lukeneedham.stickerboard.utilities

import android.content.Context
import android.view.inputmethod.InputMethodManager

object KeyboardStatus {
	/** Whether the StickerBoard keyboard is enabled in the system's input method settings. */
	fun isEnabled(context: Context): Boolean {
		val inputMethodManager =
			context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
		return inputMethodManager.enabledInputMethodList.any { it.packageName == context.packageName }
	}
}
