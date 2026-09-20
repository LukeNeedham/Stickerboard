package com.lukeneedham.stickerboard.settings

import android.app.Application
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import com.lukeneedham.stickerboard.BuildConfig
import com.lukeneedham.stickerboard.utilities.isKeyboardEnabled
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Owns [SettingsPage]'s state - the try-it-out media received so far, and whether the keyboard is
 * currently enabled in system settings. Enabling the keyboard and navigating elsewhere are one-off
 * platform actions with no state of their own, so they stay as callbacks on [SettingsRoute]
 * instead. */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {
	private val _uiState = MutableStateFlow(
		SettingsUiState(
			keyboardEnabled = isKeyboardEnabled(application),
			showDebugCard = BuildConfig.DEBUG,
		),
	)
	val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

	// Refreshes as soon as Android reports the enabled-keyboards list changed, rather than only on
	// resume - the security-warning dialog on the keyboard settings screen means users can return
	// via several back presses, and multi-window/split-screen users may never leave this app at all.
	private val inputMethodObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
		override fun onChange(selfChange: Boolean) = refreshKeyboardEnabled()
	}

	init {
		application.contentResolver.registerContentObserver(
			Settings.Secure.getUriFor(Settings.Secure.ENABLED_INPUT_METHODS),
			false,
			inputMethodObserver,
		)
	}

	override fun onCleared() {
		getApplication<Application>().contentResolver.unregisterContentObserver(inputMethodObserver)
	}

	fun refreshKeyboardEnabled() {
		_uiState.update { it.copy(keyboardEnabled = isKeyboardEnabled(getApplication())) }
	}

	fun onTryItOutMediaReceived(uri: Uri) {
		_uiState.update { it.copy(tryItOutMedia = listOf(uri) + it.tryItOutMedia) }
	}
}
