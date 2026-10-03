package com.lukeneedham.stickerboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.elvishew.xlog.XLog
import com.lukeneedham.stickerboard.utilities.ShareIntents
import com.lukeneedham.stickerboard.utilities.StartLogger

/**
 * The app's single activity. Every page - settings, onboarding, the sticker gallery, and the
 * debug/crash tools - is a Nav3 destination hosted here; see [StickerBoardApp] for the nav host
 * and back stack, and [com.lukeneedham.stickerboard.navigation.Route] for the destinations.
 *
 * Also the target of another app's "Share" action for image(s) (see the extra intent-filters on
 * this activity in the manifest) - [ShareIntents.extractImageUris] pulls the shared image URIs out of
 * that intent, and [StickerBoardApp] routes straight to [com.lukeneedham.stickerboard.share.ShareImportRoute]
 * when there are any.
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
		// No status/navigation bar color is themed (see styles.xml) - the app draws fully
		// edge-to-edge, with each page's own composable responsible for padding around system bars.
		enableEdgeToEdge()
		StartLogger.start(filesDir)

		XLog.i("=".repeat(80))
		XLog.i("Loaded $packageName:$localClassName")

		val sharedImageUris = ShareIntents.extractImageUris(intent)

		setContent {
			StickerBoardApp(
				sharedImageUris = sharedImageUris,
				onCancelShareImport = { finish() },
			)
		}
	}
}
