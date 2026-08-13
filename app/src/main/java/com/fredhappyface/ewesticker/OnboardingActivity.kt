package com.fredhappyface.ewesticker

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.viewpager2.widget.ViewPager2
import com.fredhappyface.ewesticker.adapter.OnboardingPageAdapter
import com.fredhappyface.ewesticker.utilities.OnboardingPageListener
import com.fredhappyface.ewesticker.utilities.StickerImporter
import com.fredhappyface.ewesticker.utilities.Toaster
import com.fredhappyface.ewesticker.utilities.startLogger
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

private const val PAGE_WELCOME = 0
private const val PAGE_KEYBOARD = 1
private const val PAGE_FOLDER = 2
private const val LAST_PAGE_INDEX = PAGE_FOLDER

/**
 * OnboardingActivity walks first-time users through what EweSticker is, how to enable it as a
 * keyboard, and how to choose a sticker source directory, before handing off to MainActivity.
 * Shown once - see MainActivity.isOnboardingComplete for the check that skips it afterwards.
 */
class OnboardingActivity : AppCompatActivity(), OnboardingPageListener {
	private lateinit var sharedPreferences: SharedPreferences
	private lateinit var toaster: Toaster
	private lateinit var viewPager: ViewPager2
	private lateinit var stepLabel: TextView
	private lateinit var backButton: Button
	private lateinit var nextButton: Button

	// Bound when the choose-directory button is tapped, and used again once the picker result and
	// sticker import complete - the folder page stays alive across that gap since the picker is a
	// foreground activity launched on top of this one.
	private var chooseDirButton: Button? = null
	private var progressBar: LinearProgressIndicator? = null

	/**
	 * Sets up content view, shared prefs, and wires up the onboarding pager
	 *
	 * @param savedInstanceState saved state
	 */
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_onboarding)

		startLogger(filesDir)

		this.sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
		this.toaster = Toaster(baseContext)

		this.viewPager = findViewById(R.id.onboardingPager)
		this.stepLabel = findViewById(R.id.onboardingStepLabel)
		this.backButton = findViewById(R.id.onboardingBackBtn)
		this.nextButton = findViewById(R.id.onboardingNextBtn)

		this.viewPager.adapter = OnboardingPageAdapter(this)
		this.viewPager.registerOnPageChangeCallback(
			object : ViewPager2.OnPageChangeCallback() {
				override fun onPageSelected(position: Int) {
					// Block swiping onto a page whose predecessor's required step isn't done yet
					if (position > PAGE_WELCOME && !isPageRequirementMet(position - 1)) {
						viewPager.setCurrentItem(position - 1, true)
						toaster.toast(requirementMessage(position - 1))
						return
					}
					updateControls(position)
				}
			},
		)

		updateControls(this.viewPager.currentItem)
	}

	/** Re-checks the current page's requirement, e.g. after returning from keyboard settings */
	override fun onResume() {
		super.onResume()
		updateControls(this.viewPager.currentItem)
	}

	/**
	 * Called on button press to advance to the next onboarding page, or finish onboarding if
	 * already on the last page
	 *
	 * @param ignoredView: View
	 */
	fun onboardingNext(ignoredView: View) {
		val page = this.viewPager.currentItem
		if (!isPageRequirementMet(page)) {
			toaster.toast(requirementMessage(page))
			return
		}
		if (page >= LAST_PAGE_INDEX) {
			finishOnboarding()
			return
		}
		this.viewPager.currentItem = page + 1
	}

	/**
	 * Called on button press to return to the previous onboarding page
	 *
	 * @param ignoredView: View
	 */
	fun onboardingBack(ignoredView: View) {
		this.viewPager.currentItem -= 1
	}

	/** Called when the user taps the button to launch settings to enable the EweSticker keyboard */
	override fun onEnableKeyboardClick() {
		startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
	}

	/** Called when the user taps the button to choose a sticker source directory */
	override fun onChooseDirClick(chooseDirButton: Button, progressBar: LinearProgressIndicator) {
		this.chooseDirButton = chooseDirButton
		this.progressBar = progressBar

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
				updateControls(this.viewPager.currentItem)
				importStickers(stickerDirPath)
			}
		}

	/** Import files from storage to internal directory - mirrors MainActivity.importStickers */
	private fun importStickers(stickerDirPath: String) {
		val chooseDirButton = this.chooseDirButton ?: return
		val progressBar = this.progressBar ?: return

		toaster.toast(getString(R.string.imported_010))
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

	/**
	 * Updates the step label plus back/next button visibility, text and enabled state for the
	 * current page - next is disabled until that page's required step is completed
	 */
	private fun updateControls(page: Int) {
		this.stepLabel.text = getString(R.string.onboarding_step_label, page + 1, LAST_PAGE_INDEX + 1)
		this.backButton.visibility = if (page == PAGE_WELCOME) View.INVISIBLE else View.VISIBLE
		this.nextButton.text = if (page == LAST_PAGE_INDEX) {
			getString(R.string.onboarding_finish_button)
		} else {
			getString(R.string.onboarding_next_button)
		}
		this.nextButton.isEnabled = isPageRequirementMet(page)
	}

	/**
	 * Checks whether the required step for a given onboarding page has been completed - the
	 * welcome page has no requirement
	 *
	 * @param page page index to check
	 * @return Boolean true if the user may move on from this page
	 */
	private fun isPageRequirementMet(page: Int): Boolean = when (page) {
		PAGE_KEYBOARD -> isKeyboardEnabled()
		PAGE_FOLDER -> hasChosenStickerDir()
		else -> true
	}

	/**
	 * Gets the message to show when the user tries to move on without completing a page's required
	 * step
	 *
	 * @param page page index the message is for
	 */
	private fun requirementMessage(page: Int): String = when (page) {
		PAGE_KEYBOARD -> getString(R.string.onboarding_keyboard_required)
		PAGE_FOLDER -> getString(R.string.onboarding_folder_required)
		else -> ""
	}

	/**
	 * Checks whether the EweSticker keyboard is enabled in the system's input method settings
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
