package com.lukeneedham.stickerboard.share

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lukeneedham.stickerboard.data.AppPreferences
import com.lukeneedham.stickerboard.utilities.importPhotosToPack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/** How a finished import went - the pack it landed in, how many images were added, and the file
 * name of the last one, so the Stickers page can scroll straight to it. */
data class ShareImportResult(val packName: String, val count: Int, val lastFileName: String?)

/** Everything [ShareImportPage] needs to render: the existing packs to choose from, whether an
 * import is currently running, and the outcome once it finishes. */
data class ShareImportUiState(
	val packNames: List<String> = emptyList(),
	val isImporting: Boolean = false,
	val result: ShareImportResult? = null,
)

/**
 * Owns the "choose a sticker pack to add shared images to" flow reached when another app shares
 * image(s) into StickerBoard: lists the packs already on disk (so the user can add to one of
 * them), lets them type a new pack name instead, then copies the shared images into internal
 * storage exactly like the Stickers page's own "add photo" action does - see [importPhotosToPack],
 * which also flags the running keyboard to pick up the change next time it's shown.
 */
class ShareImportViewModel(application: Application) : AndroidViewModel(application) {
	private val prefs = AppPreferences(application)
	private val internalDir = File(application.filesDir, "stickers")

	private val _uiState = MutableStateFlow(ShareImportUiState(packNames = existingPackNames()))
	val uiState: StateFlow<ShareImportUiState> = _uiState.asStateFlow()

	private fun existingPackNames(): List<String> =
		internalDir.listFiles { file -> file.isDirectory }
			?.map { it.name }
			?.sorted()
			.orEmpty()

	/** Imports [uris] into [packName] (created fresh on disk if it doesn't exist yet), then reports
	 * the outcome via [ShareImportUiState.result]. A no-op while already importing. */
	fun importInto(packName: String, uris: List<Uri>) {
		if (_uiState.value.isImporting || uris.isEmpty()) return
		_uiState.update { it.copy(isImporting = true) }
		viewModelScope.launch {
			val addedFiles = importPhotosToPack(getApplication(), packName, uris)
			prefs.numStickersImported += addedFiles.size
			val result = ShareImportResult(packName, addedFiles.size, addedFiles.lastOrNull()?.name)
			_uiState.update { it.copy(isImporting = false, result = result) }
		}
	}
}
