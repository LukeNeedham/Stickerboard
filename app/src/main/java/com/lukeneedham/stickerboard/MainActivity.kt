package com.lukeneedham.stickerboard

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
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
import com.lukeneedham.stickerboard.settings.SettingsScreen
import com.lukeneedham.stickerboard.settings.SettingsUiState
import com.lukeneedham.stickerboard.settings.StickerBoardSettingsTheme
import com.lukeneedham.stickerboard.utilities.StickerImporter
import com.lukeneedham.stickerboard.utilities.Toaster
import com.lukeneedham.stickerboard.utilities.startLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

/** MainActivity - the settings app's root screen, rebuilt in Compose. */
class MainActivity : AppCompatActivity() {
	private lateinit var sharedPreferences: SharedPreferences
	private lateinit var toaster: Toaster

	// Assigned once by ProgressIndicatorView's AndroidView factory, which runs during the first
	// composition - well before any button press can trigger importStickers().
	private lateinit var progressBar: LinearProgressIndicator

	private var uiState by mutableStateOf(
		SettingsUiState(
			stickerDirPath = "",
			lastUpdateDate = "",
			numStickersImported = 0,
			isImporting = false,
			showDebugCard = BuildConfig.DEBUG,
		),
	)

	/**
	 * Sets up content view, shared prefs, etc.
	 *
	 * @param savedInstanceState saved state
	 */
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		this.sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
		if (!isOnboardingComplete()) {
			startActivity(Intent(this, OnboardingActivity::class.java))
			finish()
			return
		}

		startLogger(filesDir)

		XLog.i("=".repeat(80))
		XLog.i("Loaded $packageName:$localClassName")

		this.toaster = Toaster(baseContext)

		XLog.i("Loading shared preferences: ${this.sharedPreferences.all}")
		refreshStickerDirPath()

		setContent {
			StickerBoardSettingsTheme {
				SettingsScreen(
					state = uiState,
					onEnableKeyboard = ::enableKeyboard,
					onTryItOutMediaReceived = ::onTryItOutMediaReceived,
					onChooseDir = ::chooseDir,
					onReloadStickers = ::reloadStickers,
					onViewStickers = ::viewStickers,
					onOpenDebug = ::openDebug,
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

	/**
	 * Handles ACTION_OPEN_DOCUMENT_TREE result and adds stickerDirPath, lastUpdateDate to
	 * this.sharedPreferences and resets recentCache, compatCache
	 */
	private val chooseDirResultLauncher =
		registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
			if (result.resultCode == Activity.RESULT_OK) {
				val editor = sharedPreferences.edit()
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
				refreshStickerDirPath()
				importStickers(stickerDirPath)
			}
		}

	/** Called on button press to launch settings */
	private fun enableKeyboard() {
		startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
	}

	/** Called on button press to choose a new directory */
	private fun chooseDir() {
		val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
		intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
		intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
		chooseDirResultLauncher.launch(intent)
	}

	/**
	 * Called when a user taps the reload stickers button. If we have a set stickerDirPath, call
	 * importStickers()
	 */
	private fun reloadStickers() {
		val stickerDirPath = this.sharedPreferences.getString(
			"stickerDirPath",
			null,
		)
		if (stickerDirPath != null) {
			importStickers(stickerDirPath)
		} else {
			this.toaster.toast(
				getString(R.string.imported_034),
			)
		}
	}

	/**
	 * Called on button press to open the sticker gallery, where the user can browse their packs and
	 * add photos from the device's photo gallery to any of them.
	 */
	private fun viewStickers() {
		startActivity(Intent(this, StickerGalleryActivity::class.java))
	}

	/**
	 * Called on button press to open the debug tools screen. Only reachable when [BuildConfig.DEBUG]
	 * is true, since the button that calls this is hidden otherwise.
	 */
	private fun openDebug() {
		startActivity(Intent(this, DebugActivity::class.java))
	}

	/** Import files from storage to internal directory */
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
				refreshStickerDirPath()
				uiState = uiState.copy(isImporting = false)
			}
		}
	}

	/**
	 * Lets the "Try It Out" field on the settings screen receive stickers sent via
	 * [android.view.inputmethod.InputConnection.commitContent] - the same mechanism
	 * [com.lukeneedham.stickerboard.utilities.StickerSender] uses to deliver stickers to any other
	 * app - by prepending each received item to the try-it-out gallery shown beneath the field.
	 */
	private fun onTryItOutMediaReceived(uri: Uri) {
		uiState = uiState.copy(tryItOutMedia = listOf(uri) + uiState.tryItOutMedia)
	}

	/** Reads saved sticker dir path from preferences */
	private fun refreshStickerDirPath() {
		uiState = uiState.copy(
			stickerDirPath = this.sharedPreferences.getString(
				"stickerDirPath", resources.getString(R.string.update_sticker_pack_info_path),
			) ?: resources.getString(R.string.update_sticker_pack_info_path),
			lastUpdateDate = this.sharedPreferences.getString(
				"lastUpdateDate", resources.getString(R.string.update_sticker_pack_info_date),
			) ?: resources.getString(R.string.update_sticker_pack_info_date),
			numStickersImported = this.sharedPreferences.getInt("numStickersImported", 0),
		)
	}

	/**
	 * Checks whether the user has completed the onboarding flow. Installs that already had a
	 * sticker directory configured before onboarding existed are treated as already onboarded, so
	 * existing users aren't sent through it retroactively.
	 *
	 * @return Boolean true if onboarding should be skipped
	 */
	private fun isOnboardingComplete(): Boolean {
		if (this.sharedPreferences.getBoolean("onboardingComplete", false)) {
			return true
		}
		if (this.sharedPreferences.contains("stickerDirPath")) {
			this.sharedPreferences.edit().putBoolean("onboardingComplete", true).apply()
			return true
		}
		return false
	}
}
