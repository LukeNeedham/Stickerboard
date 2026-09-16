package com.lukeneedham.stickerboard

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.lukeneedham.stickerboard.utilities.applyStatusBarTopInset
import com.lukeneedham.stickerboard.utilities.startLogger

/**
 * Entry point for debug-only tools, reachable from the settings screen only in debug builds. Also
 * guards itself in case it's ever launched directly (e.g. via adb) in a non-debug build.
 */
class DebugActivity : AppCompatActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		if (!BuildConfig.DEBUG) {
			finish()
			return
		}

		setContentView(R.layout.activity_debug)
		startLogger(filesDir)

		findViewById<View>(R.id.debugAppBar).applyStatusBarTopInset()

		val toolbar = findViewById<MaterialToolbar>(R.id.debugToolbar)
		val navIcon = getDrawable(R.drawable.ic_back)?.mutate()
		navIcon?.setTint(getColor(R.color.app_on_primary))
		toolbar.navigationIcon = navIcon
		toolbar.setNavigationOnClickListener { finish() }
	}

	/** Called on button press to open the crashes list. */
	fun openCrashes(ignoredView: View) {
		startActivity(Intent(this, CrashesActivity::class.java))
	}
}
