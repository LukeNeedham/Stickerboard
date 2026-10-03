package com.lukeneedham.stickerboard.utilities

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat

object ShareIntents {
	/** Every image URI carried by a share-to-StickerBoard [intent] (ACTION_SEND or
	 * ACTION_SEND_MULTIPLE with an image MIME type, matching this activity's manifest intent-filters) -
	 * empty if [intent] is neither, or carries no images. */
	fun extractImageUris(intent: Intent?): List<Uri> {
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
}
