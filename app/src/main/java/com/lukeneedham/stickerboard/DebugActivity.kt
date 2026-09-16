package com.lukeneedham.stickerboard

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import com.lukeneedham.stickerboard.debug.DebugScreen
import com.lukeneedham.stickerboard.settings.StickerBoardSettingsTheme
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

		startLogger(filesDir)

		setContent {
			StickerBoardSettingsTheme {
				DebugScreen(
					onBack = { finish() },
					onOpenCrashes = { startActivity(Intent(this, CrashesActivity::class.java)) },
				)
			}
		}
	}
}
