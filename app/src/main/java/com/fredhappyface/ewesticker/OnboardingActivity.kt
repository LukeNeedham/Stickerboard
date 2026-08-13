package com.fredhappyface.ewesticker

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import android.view.View
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
					updateControls(position)
				}
			},
		)

		updateControls(this.viewPager.currentItem)
	}

	/**
	 * Called on button press to advance to the next onboarding page, or finish onboarding if
	 * already on the last page
	 *
	 * @param ignoredView: View
	 */
	fun onboardingNext(ignoredView: View) {
		if (this.viewPager.currentItem >= LAST_PAGE_INDEX) {
			finishOnboarding()
			return
		}
		this.viewPager.currentItem += 1
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

	/** Updates the step label plus back/next button visibility and text for the current page */
	private fun updateControls(page: Int) {
		this.stepLabel.text = getString(R.string.onboarding_step_label, page + 1, LAST_PAGE_INDEX + 1)
		this.backButton.visibility = if (page == FIRST_PAGE_INDEX) View.INVISIBLE else View.VISIBLE
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
