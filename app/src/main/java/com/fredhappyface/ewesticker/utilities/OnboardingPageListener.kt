package com.fredhappyface.ewesticker.utilities

import android.widget.Button
import com.google.android.material.progressindicator.LinearProgressIndicator

/** Callbacks for the interactive buttons on the onboarding keyboard and folder pages */
interface OnboardingPageListener {
	/** Called when the user taps the button to open keyboard settings */
	fun onEnableKeyboardClick()

	/**
	 * Called when the user taps the button to choose a sticker source directory
	 *
	 * @param chooseDirButton the button that was tapped, disabled while stickers are importing
	 * @param progressBar progress indicator to report import progress on
	 */
	fun onChooseDirClick(chooseDirButton: Button, progressBar: LinearProgressIndicator)
}
