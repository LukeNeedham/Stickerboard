package com.lukeneedham.stickerboard.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import com.lukeneedham.stickerboard.BuildConfig
import com.lukeneedham.stickerboard.utilities.KeyboardStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Owns [SettingsPageContent]'s state - the try-it-out media received so far, and whether the keyboard is
 * currently enabled in system settings. Enabling the keyboard and navigating elsewhere are one-off
 * platform actions with no state of their own, so they stay as callbacks on [SettingsPage]
 * instead. */
class SettingsViewModel(private val keyboardStatus: KeyboardStatus) : ViewModel() {
	private val _uiState = MutableStateFlow(
		SettingsUiState(
			keyboardEnabled = keyboardStatus.isEnabled(),
			showDebugCard = BuildConfig.DEBUG,
		),
	)
	val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

	private val stopObserving = keyboardStatus.observe { refreshKeyboardEnabled() }

	override fun onCleared() {
		stopObserving()
	}

	fun refreshKeyboardEnabled() {
		_uiState.update { it.copy(keyboardEnabled = keyboardStatus.isEnabled()) }
	}

	fun onTryItOutMediaReceived(uri: Uri) {
		_uiState.update { it.copy(tryItOutLastMedia = uri) }
	}
}
