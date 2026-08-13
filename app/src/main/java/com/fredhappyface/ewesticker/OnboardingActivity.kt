package com.fredhappyface.ewesticker

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.ViewFlipper
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.fredhappyface.ewesticker.utilities.StickerImporter
import com.fredhappyface.ewesticker.utilities.Toaster
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

private const val FIRST_PAGE_INDEX = 0
private const val LAST_PAGE_INDEX = 2

/**
 * OnboardingActivity walks first-time users through what EweSticker is, how to enable it as a
 * keyboard, and how to choose a sticker source directory, before handing off to MainActivity.
 * Shown once - see MainActivity.isOnboardingComplete for the check that skips it afterwards.
 */
class OnboardingActivity : AppCompatActivity() {
	private lateinit var sharedPreferences: SharedPreferences
	private lateinit var toaster: Toaster
	private lateinit var flipper: ViewFlipper
	private lateinit var stepLabel: TextView
	private lateinit var backButton: Button
	private lateinit var nextButton: Button
	private lateinit var skipButton: Button

	/**
	 * Sets up content view, shared prefs, and wires up the onboarding pages
	 *
	 * @param savedInstanceState saved state
	 */
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_onboarding)

		this.sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
		this.toaster = Toaster(baseContext)

		this.flipper = findViewById(R.id.onboardingFlipper)
		this.stepLabel = findViewById(R.id.onboardingStepLabel)
		this.backButton = findViewById(R.id.onboardingBackBtn)
		this.nextButton = findViewById(R.id.onboardingNextBtn)
		this.skipButton = findViewById(R.id.onboardingSkipBtn)

		updateControls()
	}

	/**
	 * Called on button press to advance to the next onboarding page, or finish onboarding if
	 * already on the last page
	 *
	 * @param ignoredView: View
	 */
	fun onboardingNext(ignoredView: View) {
		if (this.flipper.displayedChild >= LAST_PAGE_INDEX) {
			finishOnboarding()
			return
		}
		this.flipper.showNext()
		updateControls()
	}

	/**
	 * Called on button press to return to the previous onboarding page
	 *
	 * @param ignoredView: View
	 */
	fun onboardingBack(ignoredView: View) {
		this.flipper.showPrevious()
		updateControls()
	}

	/**
	 * Called on button press to skip the rest of onboarding and go straight to the home page
	 *
	 * @param ignoredView: View
	 */
	fun onboardingSkip(ignoredView: View) {
		finishOnboarding()
	}

	/**
	 * Called on button press to launch settings so the user can enable the EweSticker keyboard
	 *
	 * @param ignoredView: View
	 */
	fun enableKeyboard(ignoredView: View) {
		startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
	}

	/**
	 * Called on button press to choose a sticker source directory
	 *
	 * @param ignoredView: View
	 */
	fun chooseDir(ignoredView: View) {
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
				importStickers(stickerDirPath)
			}
		}

	/** Import files from storage to internal directory - mirrors MainActivity.importStickers */
	private fun importStickers(stickerDirPath: String) {
		toaster.toast(getString(R.string.imported_010))
		val chooseDirButton = findViewById<Button>(R.id.onboardingChooseDirBtn)
		val progressBar = findViewById<LinearProgressIndicator>(R.id.onboardingProgressIndicator)
		chooseDirButton.isEnabled = false

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
				chooseDirButton.isEnabled = true
			}
		}
	}

	/** Updates the step label plus back/next/skip button visibility and text for the current page */
	private fun updateControls() {
		val page = this.flipper.displayedChild
		this.stepLabel.text = getString(R.string.onboarding_step_label, page + 1, LAST_PAGE_INDEX + 1)
		this.backButton.visibility = if (page == FIRST_PAGE_INDEX) View.INVISIBLE else View.VISIBLE
		this.skipButton.visibility = if (page == LAST_PAGE_INDEX) View.INVISIBLE else View.VISIBLE
		this.nextButton.text = if (page == LAST_PAGE_INDEX) {
			getString(R.string.onboarding_finish_button)
		} else {
			getString(R.string.onboarding_next_button)
		}
	}

	/** Marks onboarding as complete and hands off to MainActivity */
	private fun finishOnboarding() {
		val editor = this.sharedPreferences.edit()
		editor.putBoolean("onboardingComplete", true)
		editor.apply()
		startActivity(Intent(this, MainActivity::class.java))
		finish()
	}
}
