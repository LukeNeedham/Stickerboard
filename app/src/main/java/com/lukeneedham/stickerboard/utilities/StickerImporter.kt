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

private const val MAX_FILES = 4096
private const val MAX_PACK_SIZE = 128
private const val BUFFER_SIZE = 64 * 1024 // 64 KB

/**
 * True if [stickerDirPath]'s contents differ from what was imported into internal storage last
 * time [StickerImporter.importStickers] ran against it - e.g. stickers were added, removed, or
 * replaced directly in that external folder since. Only walks the source tree's file metadata
 * (name/size/modified time), so it's cheap enough to call before every reimport, such as from the
 * keyboard's pull-to-refresh.
 */
fun hasStickerSourceChanged(context: Context, stickerDirPath: String): Boolean {
	val sourceStickers = walkStickers(DocumentFile.fromTreeUri(context, Uri.parse(stickerDirPath)))
	val lastSignature = AppPreferences(context).stickerDirSignature
	return signatureOf(sourceStickers) != lastSignature
}

/**
 * Re-imports from [stickerDirPath] only if [hasStickerSourceChanged] - the "pull to refresh"
 * reload shared by the keyboard's own pull-to-refresh and the Stickers page's refresh action, so
 * both mean exactly the same thing. A no-op when the source's contents already match what's
 * imported, so a refresh with nothing new to pick up doesn't pay for a full wipe-and-copy.
 */
suspend fun reimportStickersIfChanged(context: Context, toaster: Toaster, stickerDirPath: String) {
	val changed = try {
		hasStickerSourceChanged(context, stickerDirPath)
	} catch (e: Exception) {
		XLog.e("Failed to check the sticker source directory for changes")
		XLog.e(e)
		return
	}
	if (!changed) return
	XLog.i("Sticker source directory changed, reimporting...")
	StickerImporter(context, toaster).importStickers(stickerDirPath)
}

/**
 * Copies [photoUris] into [packName] inside internal storage - and, best-effort, into the matching
 * pack folder of the external sticker source directory too, so a future "Reload stickers" (which
 * wipes and re-imports the internal copy from scratch) doesn't lose them. Shared by the Stickers
 * page's "add photo" action and image share-to-StickerBoard import; marks [KeyboardRefreshSignal]
 * dirty on success so an already-running keyboard picks up the change next time it's shown. Skips
 * once [packName] reaches [MAX_PACK_SIZE] stickers total. Returns how many were actually copied.
 */
suspend fun importPhotosToPack(context: Context, packName: String, photoUris: List<Uri>): Int =
	withContext(Dispatchers.IO) {
		val packDir = File(context.filesDir, "stickers/$packName")
		packDir.mkdirs()
		var packSize = packDir.listFiles { file -> file.isFile }?.size ?: 0
		var addedCount = 0
		for (uri in photoUris) {
			if (packSize >= MAX_PACK_SIZE) break
			if (copyPhotoIntoPack(context, uri, packDir, packName)) {
				addedCount++
				packSize++
			}
		}
		if (addedCount > 0) KeyboardRefreshSignal.markDirty()
		addedCount
	}

/**
 * Copies a single photo from [uri] into [packDir] - and, best-effort, into the matching pack folder
 * of the external sticker source directory too.
 *
 * @return true if the internal copy (the one the keyboard actually reads) succeeded
 */
private fun copyPhotoIntoPack(
	context: Context,
	uri: Uri,
	packDir: File,
	packName: String,
): Boolean {
	return try {
		val mimeType = context.contentResolver.getType(uri)
		val extension = mimeType?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) } ?: "jpg"
		val fileName = "imported_${System.currentTimeMillis()}_${System.nanoTime()}.$extension"

		val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return false
		File(packDir, fileName).outputStream().use { it.write(bytes) }

		copyToExternalSourceDir(context, packName, fileName, bytes)
		true
	} catch (e: IOException) {
		XLog.e("There was an IOException when copying a photo into '$packName'!")
		XLog.e(e)
		false
	}
}

/**
 * Best-effort copy of [fileName]'s [bytes] into [packName]'s folder in the external sticker source
 * directory, if one is configured - silently does nothing/fails otherwise, since the internal copy
 * is already usable regardless.
 */
private fun copyToExternalSourceDir(
	context: Context,
	packName: String,
	fileName: String,
	bytes: ByteArray,
) {
	val path = AppPreferences(context).stickerDirPath ?: return
	try {
		val rootDir = DocumentFile.fromTreeUri(context, Uri.parse(path)) ?: return
		val packDocDir = rootDir.findFile(packName) ?: rootDir.createDirectory(packName) ?: return
		val newFile = packDocDir.createFile("application/octet-stream", fileName) ?: return
		context.contentResolver.openOutputStream(newFile.uri)?.use { it.write(bytes) }
	} catch (e: Exception) {
		XLog.e("There was an error copying a photo into the external sticker source directory!")
		XLog.e(e)
	}
}

/**
 * A sticker file found under the source tree, paired with the pack it belongs to - the name of
 * its immediate parent directory, or, for stickers nested several directories deep, the full
 * chain of directory names from just below the sticker root down to its parent, joined with "-"
 * (e.g. root/A/B/C/sticker.png becomes pack "A-B-C"). A sticker sitting directly in the sticker
 * root (no parent directory of its own) falls into a pack named after the root itself.
 */
private data class SourceSticker(val file: DocumentFile, val packName: String)

/** An order-independent fingerprint of a set of source files, sensitive to any file being added,
 * removed, resized, or having its modified time changed, or moved to a different pack. */
private fun signatureOf(sourceStickers: List<SourceSticker>): String {
	val digest = MessageDigest.getInstance("SHA-256")
	sourceStickers
		.map { "${it.packName}/${it.file.name}:${it.file.length()}:${it.file.lastModified()}" }
		.sorted()
		.forEach { digest.update(it.toByteArray()) }
	return digest.digest().joinToString("") { "%02x".format(it) }
}

/**
 * Walk every file under rootNode, however deeply nested, capped at [MAX_FILES] + 1. Each result
 * carries the pack name derived from the full chain of directories between rootNode and the file.
 */
private fun walkStickers(rootNode: DocumentFile?): List<SourceSticker> {
	if (rootNode == null) return emptyList()
	val rootName = rootNode.name ?: "__default__"
	val sourceStickers = mutableListOf<SourceSticker>()
	// Each stack entry is a directory paired with the pack name that any sticker directly inside
	// it belongs to.
	val stack = ArrayDeque<Pair<DocumentFile, String>>()
	stack.addLast(rootNode to rootName)
	while (stack.isNotEmpty() && sourceStickers.size < MAX_FILES) {
		val (currentDir, packName) = stack.removeLast()
		currentDir.listFiles().forEach { file ->
			if (file.isFile) {
				sourceStickers.add(SourceSticker(file, packName))
				if (sourceStickers.size > MAX_FILES) return sourceStickers
			} else if (file.isDirectory) {
				val childDirName = file.name ?: "__default__"
				val childPackName = if (currentDir == rootNode) childDirName else "$packName-$childDirName"
				stack.addLast(file to childPackName)
			}
		}
	}
	return sourceStickers
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
