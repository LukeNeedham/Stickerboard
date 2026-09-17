package com.lukeneedham.stickerboard.utilities

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceManager
import com.elvishew.xlog.XLog
import com.lukeneedham.stickerboard.R

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

private const val MAX_FILES = 4096
private const val MAX_PACK_SIZE = 128
private const val BUFFER_SIZE = 64 * 1024 // 64 KB

/** SharedPreferences key holding a fingerprint of the sticker source directory as of the most
 * recent successful [StickerImporter.importStickers] call - see [hasStickerSourceChanged]. */
private const val SOURCE_SIGNATURE_PREF_KEY = "stickerDirSignature"

/**
 * True if [stickerDirPath]'s contents differ from what was imported into internal storage last
 * time [StickerImporter.importStickers] ran against it - e.g. stickers were added, removed, or
 * replaced directly in that external folder since. Only walks the source tree's file metadata
 * (name/size/modified time), so it's cheap enough to call before every reimport, such as from the
 * keyboard's pull-to-refresh.
 */
fun hasStickerSourceChanged(context: Context, stickerDirPath: String): Boolean {
	val leafNodes = walkLeafFiles(DocumentFile.fromTreeUri(context, Uri.parse(stickerDirPath)))
	val lastSignature =
		PreferenceManager.getDefaultSharedPreferences(context).getString(SOURCE_SIGNATURE_PREF_KEY, null)
	return signatureOf(leafNodes) != lastSignature
}

/** An order-independent fingerprint of a set of source files, sensitive to any file being added,
 * removed, resized, or having its modified time changed. */
private fun signatureOf(leafNodes: Set<DocumentFile>): String {
	val digest = MessageDigest.getInstance("SHA-256")
	leafNodes
		.map { "${it.parentFile?.name}/${it.name}:${it.length()}:${it.lastModified()}" }
		.sorted()
		.forEach { digest.update(it.toByteArray()) }
	return digest.digest().joinToString("") { "%02x".format(it) }
}

/**
 * Get a set of leaf (file, not directory) DocumentFiles under rootNode, capped at [MAX_FILES] + 1.
 */
private fun walkLeafFiles(rootNode: DocumentFile?): Set<DocumentFile> {
	val leafNodes = mutableSetOf<DocumentFile>()
	val stack = ArrayDeque<DocumentFile?>()
	rootNode?.let { stack.addLast(it) }
	while (stack.isNotEmpty() && leafNodes.size < MAX_FILES) {
		val currentFile = stack.removeLast()
		currentFile?.listFiles()?.forEach { file ->
			if (file.isFile) {
				leafNodes.add(file)
				if (leafNodes.size > MAX_FILES) return leafNodes
			} else if (file.isDirectory) {
				stack.addLast(file)
			}
		}
	}
	return leafNodes
}

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
	private val supportedMimes = Utils.getSupportedMimes()

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
		val leafNodes = walkLeafFiles(DocumentFile.fromTreeUri(context, Uri.parse(stickerDirPath)))
		if (leafNodes.size > MAX_FILES) {
			XLog.w("Found more than $MAX_FILES stickers, notify user")
			toaster.setMessage(context.getString(R.string.imported_031, MAX_FILES))
		}
		PreferenceManager.getDefaultSharedPreferences(context).edit()
			.putString(SOURCE_SIGNATURE_PREF_KEY, signatureOf(leafNodes))
			.apply()

		withContext(Dispatchers.Main) {
			progressBar?.isIndeterminate = false
		}

		// Perform concurrent file copy operations
		XLog.i("Perform concurrent file copy operations...")
		withContext(Dispatchers.IO) {
			leafNodes.take(MAX_FILES).mapIndexed { index, file ->
				async {
					importSticker(file)
					mainHandler.post {
						updateProgressBar(index + 1, leafNodes.size)
					}
				}
			}.awaitAll()
		}

		withContext(Dispatchers.Main) {
			progressBar?.visibility = View.GONE
		}

		XLog.i("Copied ${totalStickers.get()} / ${leafNodes.size}")

		return totalStickers.get()
	}

	/**
	 * Copies stickers from source to internal storage
	 *
	 * @param sticker sticker to copy over
	 *
	 * @return 1 if sticker imported successfully else 0
	 */
	private suspend fun importSticker(sticker: DocumentFile) {
		val parentDir = sticker.parentFile?.name ?: "__default__"
		val packSize = packSizes[parentDir] ?: 0
		if (packSize > MAX_PACK_SIZE) {
			XLog.w("Found more than $MAX_PACK_SIZE stickers in '$parentDir', notify user")
			toaster.setMessage(context.getString(R.string.imported_032, MAX_PACK_SIZE, parentDir))
			return
		}
		if (sticker.type !in supportedMimes) {
			XLog.w("'$parentDir/${sticker.name}' is not a supported mimetype (${sticker.type}), notify user")
			toaster.setMessage(
				context.getString(
					R.string.imported_033,
					sticker.type,
					parentDir,
					sticker.name
				)
			)
			return
		}
		packSizes[parentDir] = packSize + 1

		val contentResolver = context.contentResolver
		try {
			val inputStream = contentResolver.openInputStream(sticker.uri)
			if (inputStream != null) {
				val destSticker = File(context.filesDir, "stickers/$parentDir/${sticker.name}")
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
			XLog.e("There was an IOException when copying '${parentDir}/${sticker.name}'!")
			XLog.e(e)
		}
	}
}
