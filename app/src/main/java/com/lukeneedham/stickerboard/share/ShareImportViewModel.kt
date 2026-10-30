package com.lukeneedham.stickerboard.share

import androidx.lifecycle.ViewModel
import com.lukeneedham.stickerboard.utilities.StickerStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns the "choose a sticker pack to add shared images to" flow reached when another app shares
 * image(s) into StickerBoard: lists the packs already on disk, so the user can add to one of them
 * instead of typing a new pack name. Doesn't do any importing itself - see
 * [com.lukeneedham.stickerboard.gallery.GalleryViewModel.runPendingImport], which the Stickers page
 * runs once it's navigated to with the pack the user picked here.
 */
class ShareImportViewModel(private val stickerStorage: StickerStorage) : ViewModel() {
	private val _uiState = MutableStateFlow(ShareImportUiState(packNames = existingPackNames()))
	val uiState: StateFlow<ShareImportUiState> = _uiState.asStateFlow()

	private fun existingPackNames(): List<String> =
		stickerStorage.internalDir.listFiles { file -> file.isDirectory }
			?.map { it.name }
			?.sorted()
			.orEmpty()
}
