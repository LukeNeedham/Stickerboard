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

	/** [pendingImportPackName]/[pendingImportUris] carry a share-import that hasn't happened yet - the
	 * pack picked on the ShareImport screen, and the image URIs to copy into it - so this page can
	 * show its own loading indicator while running the copy itself, then scroll to what it added.
	 * Both empty/null (the default) for an ordinary visit here. */
	@Serializable
	data class Gallery(
		val pendingImportPackName: String? = null,
		val pendingImportUris: List<String> = emptyList(),
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
