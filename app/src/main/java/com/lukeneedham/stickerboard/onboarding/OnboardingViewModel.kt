package com.lukeneedham.stickerboard.onboarding

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lukeneedham.stickerboard.data.AppPreferences
import com.lukeneedham.stickerboard.utilities.KeyboardStatus
import com.lukeneedham.stickerboard.utilities.StickerStorage
import com.lukeneedham.stickerboard.utilities.Toaster
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns [OnboardingPageContent]'s state and every side effect it triggers - checking/watching whether
 * the keyboard is enabled, and importing stickers from a chosen source directory.
 */
class OnboardingViewModel(
	private val prefs: AppPreferences,
	private val keyboardStatus: KeyboardStatus,
	private val stickerStorage: StickerStorage,
) : ViewModel() {
	private val toaster = Toaster()

	private val _uiState = MutableStateFlow(
		OnboardingUiState(
			keyboardEnabled = keyboardStatus.isEnabled(),
			isImporting = false,
			loadedStickerCount = if (prefs.stickerDirPath != null) prefs.numStickersImported else null,
		),
	)
	val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

	private val stopObserving = keyboardStatus.observe { refreshRequirements() }

	override fun onCleared() {
		stopObserving()
	}

	fun refreshRequirements() {
		_uiState.update { it.copy(keyboardEnabled = keyboardStatus.isEnabled()) }
	}

	/**
	 * Takes persistable permission on the newly chosen source directory (if any), then persists it
	 * and starts importing from it. [uri] may be null if the picker was cancelled/failed, matching
	 * how the underlying ActivityResultContract reports that.
	 */
	fun onDirectorySelected(uri: Uri?) {
		if (uri != null) stickerStorage.takePersistableAccess(uri)
		val stickerDirPath = uri.toString()
		prefs.stickerDirPath = stickerDirPath
		prefs.lastUpdateEpochMillis = System.currentTimeMillis()
		prefs.recentCache = ""
		prefs.compatCache = ""
		refreshRequirements()
		importStickers(stickerDirPath)
	}

	// No toasts here by design - progress is shown inline on the page itself (spinner while
	// isImporting, then the loaded count), and the finish button stays disabled until
	// loadedStickerCount is set.
	private fun importStickers(stickerDirPath: String) {
		_uiState.update { it.copy(isImporting = true, loadedStickerCount = null) }
		viewModelScope.launch(Dispatchers.IO) {
			val totalStickers = stickerStorage.importStickers(toaster, stickerDirPath)
			withContext(Dispatchers.Main) {
				prefs.numStickersImported = totalStickers
				_uiState.update { it.copy(isImporting = false, loadedStickerCount = totalStickers) }
			}
		}
	}

	fun onFinish() {
		prefs.onboardingComplete = true
	}
}
