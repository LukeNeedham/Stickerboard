package com.lukeneedham.stickerboard.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Every page reachable from [com.lukeneedham.stickerboard.MainActivity]'s single-activity nav host. */

@Serializable
data object OnboardingNavKey : NavKey

@Serializable
data object SettingsNavKey : NavKey

/** [pendingImportPackName]/[pendingImportUris] carry a share-import that hasn't happened yet - the
 * pack picked on the ShareImport screen, and the image URIs to copy into it - so this page can
 * show its own loading indicator while running the copy itself, then scroll to what it added.
 * Both empty/null (the default) for an ordinary visit here. */
@Serializable
data class GalleryNavKey(
	val pendingImportPackName: String? = null,
	val pendingImportUris: List<String> = emptyList(),
) : NavKey

/** Reached when another app shares image(s) into StickerBoard - lets the user pick which sticker
 * pack (existing or new) to import [imageUris] into. */
@Serializable
data class ShareImportNavKey(val imageUris: List<String>) : NavKey

/** The app's own debug page, which opens [DebugNavKey]. */
@Serializable
data object AppDebugNavKey : NavKey

/** The shared debug library's page (animation speed, links to its logs and crashes pages). */
@Serializable
data object DebugNavKey : NavKey

@Serializable
data object LogsNavKey : NavKey

@Serializable
data object CrashesNavKey : NavKey
