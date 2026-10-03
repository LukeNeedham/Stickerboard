package com.lukeneedham.stickerboard.utilities

import android.content.Context
import com.lukeneedham.stickerboard.data.AppPreferences

object OnboardingStatus {
	/**
	 * Checks whether the user has completed the onboarding flow. Installs that already had a sticker
	 * directory configured before onboarding existed are treated as already onboarded, so existing
	 * users aren't sent through it retroactively.
	 */
	fun isComplete(context: Context): Boolean {
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
