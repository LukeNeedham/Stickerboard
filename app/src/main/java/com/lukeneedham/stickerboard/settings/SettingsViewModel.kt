package com.lukeneedham.stickerboard.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import com.lukeneedham.stickerboard.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Owns [SettingsPage]'s state - just the try-it-out media received so far, since everything
 * else it shows is static. Enabling the keyboard and navigating elsewhere are one-off platform
 * actions with no state of their own, so they stay as callbacks on [SettingsRoute] instead. */
class SettingsViewModel : ViewModel() {
	private val _uiState = MutableStateFlow(SettingsUiState(showDebugCard = BuildConfig.DEBUG))
	val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

	fun onTryItOutMediaReceived(uri: Uri) {
		_uiState.update { it.copy(tryItOutMedia = listOf(uri) + it.tryItOutMedia) }
	}
}
