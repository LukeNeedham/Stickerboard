package com.lukeneedham.stickerboard.utilities

import android.content.Context
import android.view.inputmethod.InputMethodManager
import com.lukeneedham.stickerboard.data.AppPreferences

/** Whether the user has finished setting the app up: keyboard enabled and onboarding done. */
object SetupStatus {
	/** Whether the StickerBoard keyboard is enabled in the system's input method settings. */
	fun isKeyboardEnabled(context: Context): Boolean {
		val inputMethodManager =
			context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
		return inputMethodManager.enabledInputMethodList.any { it.packageName == context.packageName }
	}

	/**
	 * Checks whether the user has completed the onboarding flow. Installs that already had a sticker
	 * directory configured before onboarding existed are treated as already onboarded, so existing
	 * users aren't sent through it retroactively.
	 */
	fun isOnboardingComplete(context: Context): Boolean {
		val prefs = AppPreferences(context)
		if (prefs.onboardingComplete) {
			return true
		}
		if (prefs.stickerDirPath != null) {
			prefs.onboardingComplete = true
			return true
		}
		return false
	}
}
