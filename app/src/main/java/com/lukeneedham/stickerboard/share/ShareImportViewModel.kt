package com.lukeneedham.stickerboard.share

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** Everything [ShareImportPage] needs to render: the existing packs to choose from. */
data class ShareImportUiState(val packNames: List<String> = emptyList())

/**
 * Owns the "choose a sticker pack to add shared images to" flow reached when another app shares
 * image(s) into StickerBoard: lists the packs already on disk, so the user can add to one of them
 * instead of typing a new pack name. Doesn't do any importing itself - see
 * [com.lukeneedham.stickerboard.gallery.GalleryViewModel.runPendingImport], which the Stickers page
 * runs once it's navigated to with the pack the user picked here.
 */
class ShareImportViewModel(application: Application) : AndroidViewModel(application) {
	private val internalDir = File(application.filesDir, "stickers")

	private val _uiState = MutableStateFlow(ShareImportUiState(packNames = existingPackNames()))
	val uiState: StateFlow<ShareImportUiState> = _uiState.asStateFlow()

	private fun existingPackNames(): List<String> =
		internalDir.listFiles { file -> file.isDirectory }
			?.map { it.name }
			?.sorted()
			.orEmpty()
}
