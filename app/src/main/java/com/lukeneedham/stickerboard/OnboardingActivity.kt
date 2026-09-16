package com.lukeneedham.stickerboard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.elvishew.xlog.XLog
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.lukeneedham.stickerboard.onboarding.OnboardingScreen
import com.lukeneedham.stickerboard.onboarding.OnboardingUiState
import com.lukeneedham.stickerboard.settings.StickerBoardSettingsTheme
import com.lukeneedham.stickerboard.utilities.StickerImporter
import com.lukeneedham.stickerboard.utilities.Toaster
import com.lukeneedham.stickerboard.utilities.startLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * OnboardingActivity walks first-time users through what StickerBoard is, how to enable it as a
 * keyboard, and how to choose a sticker source directory, before handing off to MainActivity.
 * Shown once - see MainActivity.isOnboardingComplete for the check that skips it afterwards.
 */
class OnboardingActivity : AppCompatActivity() {
	private lateinit var sharedPreferences: SharedPreferences
	private lateinit var toaster: Toaster

	// Assigned once by ProgressIndicatorView's AndroidView factory, which runs during the first
	// composition - well before any button press can trigger importStickers().
	private lateinit var progressBar: LinearProgressIndicator

	private var uiState by mutableStateOf(
		OnboardingUiState(keyboardEnabled = false, folderChosen = false, isImporting = false),
	)

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		startLogger(filesDir)

		this.sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
		this.toaster = Toaster(baseContext)
		refreshRequirements()

		setContent {
			StickerBoardSettingsTheme {
				OnboardingScreen(
					state = uiState,
					onEnableKeyboard = ::enableKeyboard,
					onChooseDir = ::chooseDir,
					onRequirementUnmet = { message -> toaster.toast(message) },
					onFinish = ::finishOnboarding,
					progressIndicator = { ProgressIndicatorView() },
				)
			}
		}
	}

	/** Hosts the real Material [LinearProgressIndicator] that [StickerImporter] mutates directly. */
	@Composable
	private fun ProgressIndicatorView() {
		AndroidView(
			modifier = Modifier.fillMaxWidth(),
			factory = { context ->
				LinearProgressIndicator(context).apply {
					visibility = View.GONE
					progressBar = this
				}
			},
		)
	}

	/** Re-checks the current page's requirement, e.g. after returning from keyboard settings */
	override fun onResume() {
		super.onResume()
		refreshRequirements()
	}

	private fun refreshRequirements() {
		uiState = uiState.copy(
			keyboardEnabled = isKeyboardEnabled(),
			folderChosen = hasChosenStickerDir(),
		)
	}

	/** Called when the user taps the button to launch settings to enable the StickerBoard keyboard */
	private fun enableKeyboard() {
		startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
	}

	/** Called when the user taps the button to choose a sticker source directory */
	private fun chooseDir() {
		val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
		intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
		intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
		chooseDirResultLauncher.launch(intent)
	}

	/**
	 * Handles ACTION_OPEN_DOCUMENT_TREE result and adds stickerDirPath, lastUpdateDate to
	 * this.sharedPreferences and resets recentCache, compatCache - mirrors
	 * MainActivity.chooseDirResultLauncher so the choice is ready to use once onboarding finishes
	 */
	private val chooseDirResultLauncher =
		registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
			if (result.resultCode == Activity.RESULT_OK) {
				val editor = this.sharedPreferences.edit()
				val uri = result.data?.data
				val stickerDirPath = result.data?.data.toString()
				val contentResolver = applicationContext.contentResolver

				val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
					Intent.FLAG_GRANT_WRITE_URI_PERMISSION
				if (uri != null) {
					contentResolver.takePersistableUriPermission(uri, takeFlags)
				}

				editor.putString("stickerDirPath", stickerDirPath)
				editor.putString("lastUpdateDate", Calendar.getInstance().time.toString())
				editor.putString("recentCache", "")
				editor.putString("compatCache", "")
				editor.apply()
				refreshRequirements()
				importStickers(stickerDirPath)
			}
		}

	/** Import files from storage to internal directory - mirrors MainActivity.importStickers */
	private fun importStickers(stickerDirPath: String) {
		toaster.toast(getString(R.string.imported_010))
		uiState = uiState.copy(isImporting = true)

		lifecycleScope.launch(Dispatchers.IO) {
			val totalStickers =
				StickerImporter(baseContext, toaster, progressBar).importStickers(stickerDirPath)

			withContext(Dispatchers.Main) {
				if (toaster.messages.size > 0) {
					toaster.toastOnMessages()
				} else {
					toaster.toast(getString(R.string.imported_020, totalStickers))
				}

				val editor = sharedPreferences.edit()
				editor.putInt("numStickersImported", totalStickers)
				editor.apply()
				uiState = uiState.copy(isImporting = false)
			}
		}
	}

	/**
	 * Checks whether the StickerBoard keyboard is enabled in the system's input method settings
	 *
	 * @return Boolean true if enabled
	 */
	private fun isKeyboardEnabled(): Boolean {
		val inputMethodManager =
			getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
		return inputMethodManager.enabledInputMethodList.any { it.packageName == packageName }
	}

	/**
	 * Checks whether a sticker source directory has been chosen
	 *
	 * @return Boolean true if chosen
	 */
	private fun hasChosenStickerDir(): Boolean = this.sharedPreferences.contains("stickerDirPath")

	/** Marks onboarding as complete and hands off to MainActivity */
	private fun finishOnboarding() {
		val editor = this.sharedPreferences.edit()
		editor.putBoolean("onboardingComplete", true)
		editor.apply()
		startActivity(Intent(this, MainActivity::class.java))
		finish()
	}
}
