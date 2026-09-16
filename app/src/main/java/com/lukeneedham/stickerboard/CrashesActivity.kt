package com.lukeneedham.stickerboard

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lukeneedham.stickerboard.crash.CrashRecord
import com.lukeneedham.stickerboard.crash.CrashStore
import com.lukeneedham.stickerboard.crash.CrashesScreen
import com.lukeneedham.stickerboard.settings.StickerBoardSettingsTheme
import com.lukeneedham.stickerboard.utilities.startLogger

/** Shows every crash recorded by the app or the keyboard, most recent first. */
class CrashesActivity : AppCompatActivity() {
	private lateinit var crashStore: CrashStore
	private var crashes by mutableStateOf(emptyList<CrashRecord>())

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		startLogger(filesDir)

		this.crashStore = CrashStore(this)

		setContent {
			StickerBoardSettingsTheme {
				CrashesScreen(
					crashes = crashes,
					onBack = { finish() },
					onCrashClick = { crash -> openCrashDetail(crash.id) },
				)
			}
		}
	}

	/** Re-scan crashes in case one was just recorded while this activity wasn't in the foreground. */
	override fun onResume() {
		super.onResume()
		crashes = crashStore.list()
	}

	private fun openCrashDetail(crashId: String) {
		val intent = Intent(this, CrashDetailActivity::class.java)
		intent.putExtra(CrashDetailActivity.EXTRA_CRASH_ID, crashId)
		startActivity(intent)
	}
}
