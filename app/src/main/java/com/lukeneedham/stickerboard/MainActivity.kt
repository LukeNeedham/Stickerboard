package com.lukeneedham.stickerboard

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.IntentCompat
import com.elvishew.xlog.XLog
import com.lukeneedham.stickerboard.utilities.startLogger

/**
 * The app's single activity. Every page - settings, onboarding, the sticker gallery, and the
 * debug/crash tools - is a Nav3 destination hosted here; see [StickerBoardApp] for the nav host
 * and back stack, and [com.lukeneedham.stickerboard.navigation.Route] for the destinations.
 *
 * Also the target of another app's "Share" action for image(s) (see the extra intent-filters on
 * this activity in the manifest) - [extractSharedImageUris] pulls the shared image URIs out of
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
		startLogger(filesDir)

		XLog.i("=".repeat(80))
		XLog.i("Loaded $packageName:$localClassName")

		val sharedImageUris = extractSharedImageUris(intent)

		setContent {
			StickerBoardApp(
				sharedImageUris = sharedImageUris,
				onCancelShareImport = { finish() },
			)
		}
	}
}

/** Every image URI carried by a share-to-StickerBoard [intent] (ACTION_SEND or
 * ACTION_SEND_MULTIPLE with an image MIME type, matching this activity's manifest intent-filters) -
 * empty if [intent] is neither, or carries no images. */
fun extractSharedImageUris(intent: Intent?): List<Uri> {
	if (intent == null || intent.type?.startsWith("image/") != true) return emptyList()
	return when (intent.action) {
		Intent.ACTION_SEND ->
			IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
				?.let { listOf(it) }
				.orEmpty()

		Intent.ACTION_SEND_MULTIPLE ->
			IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
				.orEmpty()

		else -> emptyList()
	}
}
