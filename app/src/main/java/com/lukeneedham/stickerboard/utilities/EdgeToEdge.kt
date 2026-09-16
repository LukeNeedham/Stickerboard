package com.lukeneedham.stickerboard.utilities

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Pads this view's top by the actual system status bar inset, replacing a fixed dp guess that
 * under- or over-shoots on devices whose status bar isn't the "typical" height - a problem made
 * worse now the app targets API 35, where edge-to-edge is enforced and content draws under the
 * status bar by default.
 */
fun View.applyStatusBarTopInset() {
	val initialPaddingLeft = paddingLeft
	val initialPaddingRight = paddingRight
	val initialPaddingBottom = paddingBottom
	ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
		val statusBarInset = insets.getInsets(WindowInsetsCompat.Type.statusBars())
		view.setPadding(initialPaddingLeft, statusBarInset.top, initialPaddingRight, initialPaddingBottom)
		insets
	}
	ViewCompat.requestApplyInsets(this)
}
