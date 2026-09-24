package com.lukeneedham.stickerboard.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Every page reachable from [com.lukeneedham.stickerboard.MainActivity]'s single-activity nav host. */
@Serializable
sealed interface Route : NavKey {
	@Serializable
	data object Onboarding : Route

	@Serializable
	data object Settings : Route

	/** [scrollToPackName]/[scrollToFileName] name a sticker to scroll to as soon as the grid loads -
	 * e.g. one just imported via a share - or null (the default) to just open at the top. */
	@Serializable
	data class Gallery(
		val scrollToPackName: String? = null,
		val scrollToFileName: String? = null,
	) : Route

	/** Reached when another app shares image(s) into StickerBoard - lets the user pick which sticker
	 * pack (existing or new) to import [imageUris] into. */
	@Serializable
	data class ShareImport(val imageUris: List<String>) : Route

	@Serializable
	data object Debug : Route

	@Serializable
	data object Crashes : Route

	@Serializable
	data class CrashDetail(val crashId: String) : Route
}
