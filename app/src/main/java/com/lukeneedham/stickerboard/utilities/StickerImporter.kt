package com.lukeneedham.stickerboard.utilities

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.elvishew.xlog.XLog
import com.lukeneedham.stickerboard.R
import com.lukeneedham.stickerboard.data.AppPreferences

import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import com.lukeneedham.stickerboard.utilities.StickerFiles.BUFFER_SIZE
import com.lukeneedham.stickerboard.utilities.StickerFiles.MAX_FILES
import com.lukeneedham.stickerboard.utilities.StickerFiles.MAX_PACK_SIZE
import com.lukeneedham.stickerboard.utilities.StickerFiles.signatureOf
import com.lukeneedham.stickerboard.utilities.StickerFiles.walkStickers

/**
 * The StickerImporter class includes a helper function to import stickers from a user-selected
 * stickerDirPath (see importStickers). The class requires the application baseContext and an
 * instance of Toaster (in turn requiring the application baseContext)
 *
 * @property context: application baseContext
 * @property toaster: an instance of Toaster (used to store an error state for later reporting to the
 * user)
 * @property progressBar: LinearProgressIndicator that we update as we import stickers, or null
 * when there's no such UI to drive (e.g. importing from the keyboard's pull-to-refresh)
 */
class StickerImporter(
	private val context: Context,
	private val toaster: Toaster,
	private val progressBar: LinearProgressIndicator? = null,
) {
	private val supportedMimes = StickerMedia.getSupportedMimes()

	// Written concurrently from multiple Dispatchers.IO threads (one per in-flight sticker import
	// in importStickers), so plain mutable collections/counters aren't safe here
	private val packSizes: MutableMap<String, Int> = ConcurrentHashMap()
	private val totalStickers = AtomicInteger(0)

	private val mainHandler = Handler(Looper.getMainLooper())

	private fun updateProgressBar(currentProgress: Int, totalStickers: Int) {
		val progressPercentage = (currentProgress.toFloat() / totalStickers.toFloat()) * 100
		progressBar?.progress = progressPercentage.toInt()
	}

	/**
	 * Used by the ACTION_OPEN_DOCUMENT_TREE handler function to copy stickers from a
	 * stickerDirPath to the application internal storage for access later on by the
	 * keyboard
	 *
	 * @param stickerDirPath a URI to the stickers directory to import into StickerBoard
	 */
	suspend fun importStickers(stickerDirPath: String): Int {
		XLog.i("Removing old stickers...")
		File(context.filesDir, "stickers").deleteRecursively()
		withContext(Dispatchers.Main) {
			progressBar?.visibility = View.VISIBLE
			progressBar?.isIndeterminate = true
		}

		XLog.i("Walking $stickerDirPath...")
		val sourceStickers = walkStickers(DocumentFile.fromTreeUri(context, Uri.parse(stickerDirPath)))
		if (sourceStickers.size > MAX_FILES) {
			XLog.w("Found more than $MAX_FILES stickers, notify user")
			toaster.setMessage(context.getString(R.string.imported_031, MAX_FILES))
		}
		AppPreferences(context).stickerDirSignature = signatureOf(sourceStickers)

		withContext(Dispatchers.Main) {
			progressBar?.isIndeterminate = false
		}

		// Perform concurrent file copy operations
		XLog.i("Perform concurrent file copy operations...")
		withContext(Dispatchers.IO) {
			sourceStickers.take(MAX_FILES).mapIndexed { index, sourceSticker ->
				async {
					importSticker(sourceSticker)
					mainHandler.post {
						updateProgressBar(index + 1, sourceStickers.size)
					}
				}
			}.awaitAll()
		}

		withContext(Dispatchers.Main) {
			progressBar?.visibility = View.GONE
		}

		XLog.i("Copied ${totalStickers.get()} / ${sourceStickers.size}")

		return totalStickers.get()
	}

	/**
	 * Copies a sticker from source to internal storage, into the directory for its pack
	 *
	 * @param sourceSticker sticker to copy over, and the pack it belongs to
	 *
	 * @return 1 if sticker imported successfully else 0
	 */
	private suspend fun importSticker(sourceSticker: SourceSticker) {
		val sticker = sourceSticker.file
		val packName = sourceSticker.packName
		val packSize = packSizes[packName] ?: 0
		if (packSize > MAX_PACK_SIZE) {
			XLog.w("Found more than $MAX_PACK_SIZE stickers in '$packName', notify user")
			toaster.setMessage(context.getString(R.string.imported_032, MAX_PACK_SIZE, packName))
			return
		}
		if (sticker.type !in supportedMimes) {
			XLog.w("'$packName/${sticker.name}' is not a supported mimetype (${sticker.type}), notify user")
			toaster.setMessage(
				context.getString(
					R.string.imported_033,
					sticker.type,
					packName,
					sticker.name
				)
			)
			return
		}
		packSizes[packName] = packSize + 1

		val contentResolver = context.contentResolver
		try {
			val inputStream = contentResolver.openInputStream(sticker.uri)
			if (inputStream != null) {
				val destSticker = File(context.filesDir, "stickers/$packName/${sticker.name}")
				destSticker.parentFile?.mkdirs()

				withContext(Dispatchers.IO) {
					inputStream.buffered(BUFFER_SIZE).use { input ->
						destSticker.outputStream().buffered(BUFFER_SIZE).use { output ->
							input.copyTo(output)
						}
					}
				}
				withContext(Dispatchers.IO) {
					inputStream.close()
				}
				totalStickers.incrementAndGet()
			}
		} catch (e: IOException) {
			XLog.e("There was an IOException when copying '${packName}/${sticker.name}'!")
			XLog.e(e)
		}
	}
}
