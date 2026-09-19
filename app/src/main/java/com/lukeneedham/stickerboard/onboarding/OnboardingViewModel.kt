package com.lukeneedham.stickerboard.onboarding

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.lukeneedham.stickerboard.utilities.StickerImporter
import com.lukeneedham.stickerboard.utilities.Toaster
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns [OnboardingPage]'s state and every side effect it triggers - checking/watching whether
 * the keyboard is enabled, and importing stickers from a chosen source directory.
 */
class OnboardingViewModel(application: Application) : AndroidViewModel(application) {
	private val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(application)
	private val toaster = Toaster()

	private val _uiState = MutableStateFlow(
		OnboardingUiState(
			keyboardEnabled = isKeyboardEnabled(application),
			isImporting = false,
			loadedStickerCount = if (hasChosenStickerDir(sharedPreferences)) {
				sharedPreferences.getInt("numStickersImported", 0)
			} else {
				null
			},
		),
	)
	val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

	// Refreshes as soon as Android reports the enabled-keyboards list changed, rather than only on
	// resume - the security-warning dialog on the keyboard settings screen means users can return
	// via several back presses, and multi-window/split-screen users may never leave this app at all.
	private val inputMethodObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
		override fun onChange(selfChange: Boolean) = refreshRequirements()
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

	fun refreshRequirements() {
		_uiState.update { it.copy(keyboardEnabled = isKeyboardEnabled(getApplication())) }
	}

	/**
	 * Takes persistable permission on the newly chosen source directory (if any), then persists it
	 * and starts importing from it. [uri] may be null if the picker was cancelled/failed, matching
	 * how the underlying ActivityResultContract reports that.
	 */
	fun onDirectorySelected(uri: Uri?) {
		val context = getApplication<Application>()
		if (uri != null) {
			val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
			context.contentResolver.takePersistableUriPermission(uri, takeFlags)
		}
		val stickerDirPath = uri.toString()
		sharedPreferences.edit()
			.putString("stickerDirPath", stickerDirPath)
			.putLong("lastUpdateEpochMillis", System.currentTimeMillis())
			.putString("recentCache", "")
			.putString("compatCache", "")
			.apply()
		refreshRequirements()
		importStickers(stickerDirPath)
	}

	// No toasts here by design - progress is shown inline on the page itself (spinner while
	// isImporting, then the loaded count), and the finish button stays disabled until
	// loadedStickerCount is set.
	private fun importStickers(stickerDirPath: String) {
		_uiState.update { it.copy(isImporting = true, loadedStickerCount = null) }
		viewModelScope.launch(Dispatchers.IO) {
			val totalStickers = StickerImporter(getApplication(), toaster).importStickers(stickerDirPath)
			withContext(Dispatchers.Main) {
				sharedPreferences.edit().putInt("numStickersImported", totalStickers).apply()
				_uiState.update { it.copy(isImporting = false, loadedStickerCount = totalStickers) }
			}
		}
	}

	fun onFinish() {
		sharedPreferences.edit().putBoolean("onboardingComplete", true).apply()
	}
}

/** Whether the StickerBoard keyboard is enabled in the system's input method settings. */
private fun isKeyboardEnabled(context: Context): Boolean {
	val inputMethodManager =
		context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
	return inputMethodManager.enabledInputMethodList.any { it.packageName == context.packageName }
}

/** Whether a sticker source directory has been chosen. */
private fun hasChosenStickerDir(sharedPreferences: SharedPreferences): Boolean =
	sharedPreferences.contains("stickerDirPath")
