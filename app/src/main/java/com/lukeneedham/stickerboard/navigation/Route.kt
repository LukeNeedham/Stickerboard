package com.lukeneedham.stickerboard.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Every screen reachable from [com.lukeneedham.stickerboard.MainActivity]'s single-activity nav host. */
@Serializable
sealed interface Route : NavKey {
	@Serializable
	data object Onboarding : Route

	@Serializable
	data object Settings : Route

	@Serializable
	data object Gallery : Route

	@Serializable
	data object Debug : Route

	@Serializable
	data object Crashes : Route

	@Serializable
	data class CrashDetail(val crashId: String) : Route
}
