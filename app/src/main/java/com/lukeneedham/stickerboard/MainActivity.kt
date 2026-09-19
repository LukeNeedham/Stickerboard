package com.lukeneedham.stickerboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.elvishew.xlog.XLog
import com.lukeneedham.stickerboard.utilities.startLogger

/**
 * The app's single activity. Every page - settings, onboarding, the sticker gallery, and the
 * debug/crash tools - is a Nav3 destination hosted here; see [StickerBoardApp] for the nav host
 * and back stack, and [com.lukeneedham.stickerboard.navigation.Route] for the destinations.
 *
 * Plain ComponentActivity, not AppCompatActivity: NavDisplay's predictive-back handling looks up
 * a NavigationEventDispatcher via the view tree, and AppCompatActivity's own decor-view setup
 * doesn't propagate that owner correctly, causing an immediate crash on launch
 * ("No NavigationEventDispatcher was provided via LocalNavigationEventDispatcherOwner"). Nothing
 * here uses AppCompat-specific APIs (no action bar, no fragments), so nothing else depends on it.
 */
class MainActivity : ComponentActivity() {
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
