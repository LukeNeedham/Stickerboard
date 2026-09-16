package com.lukeneedham.stickerboard

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import com.elvishew.xlog.XLog
import com.lukeneedham.stickerboard.utilities.startLogger

/**
 * The app's single activity. Every screen - settings, onboarding, the sticker gallery, and the
 * debug/crash tools - is a Nav3 destination hosted here; see [StickerBoardApp] for the nav host
 * and back stack, and [com.lukeneedham.stickerboard.navigation.Route] for the destinations.
 */
class MainActivity : AppCompatActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		startLogger(filesDir)

		XLog.i("=".repeat(80))
		XLog.i("Loaded $packageName:$localClassName")

		setContent {
			StickerBoardApp()
		}
	}
}
