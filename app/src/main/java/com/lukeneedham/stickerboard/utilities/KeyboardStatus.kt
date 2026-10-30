package com.lukeneedham.stickerboard.utilities

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings

/** [SetupStatus] for the ViewModels: whether the keyboard is enabled, and a way to hear when that changes. */
class KeyboardStatus(private val context: Context) {
	fun isEnabled(): Boolean = SetupStatus.isKeyboardEnabled(context)

	/**
	 * Calls [onChange] as soon as Android reports the enabled-keyboards list changed, rather than only on
	 * resume - the security-warning dialog on the keyboard settings screen means users can return
	 * via several back presses, and multi-window/split-screen users may never leave this app at all.
	 * Returns the function that stops listening.
	 */
	fun observe(onChange: () -> Unit): () -> Unit {
		val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
			override fun onChange(selfChange: Boolean) = onChange()
		}
		context.contentResolver.registerContentObserver(
			Settings.Secure.getUriFor(Settings.Secure.ENABLED_INPUT_METHODS),
			false,
			observer,
		)
		return { context.contentResolver.unregisterContentObserver(observer) }
	}
}
