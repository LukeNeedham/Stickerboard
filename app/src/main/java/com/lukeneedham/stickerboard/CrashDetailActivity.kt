package com.lukeneedham.stickerboard

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import com.lukeneedham.stickerboard.crash.CrashDetailScreen
import com.lukeneedham.stickerboard.crash.CrashStore
import com.lukeneedham.stickerboard.settings.StickerBoardSettingsTheme
import com.lukeneedham.stickerboard.utilities.startLogger

/** Shows a single crash's full, copyable stack trace. */
class CrashDetailActivity : AppCompatActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		startLogger(filesDir)

		val crashId = intent.getStringExtra(EXTRA_CRASH_ID)
		val crash = crashId?.let { CrashStore(this).get(it) }

		if (crash == null) {
			finish()
			return
		}

		setContent {
			StickerBoardSettingsTheme {
				CrashDetailScreen(crash = crash, onBack = { finish() })
			}
		}
	}

	companion object {
		const val EXTRA_CRASH_ID = "crash_id"
	}
}
