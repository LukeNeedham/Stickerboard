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

/** Operations on sticker files and the external sticker source directory. */
object StickerFiles {
	internal const val MAX_FILES = 4096
	internal const val MAX_PACK_SIZE = 128
	internal const val BUFFER_SIZE = 64 * 1024 // 64 KB

	/**
	 * True if [stickerDirPath]'s contents differ from what was imported into internal storage last
	 * time [StickerImporter.importStickers] ran against it - e.g. stickers were added, removed, or
	 * replaced directly in that external folder since. Only walks the source tree's file metadata
	 * (name/size/modified time), so it's cheap enough to call before every reimport, such as from the
	 * keyboard's pull-to-refresh.
	 */
	fun hasSourceChanged(context: Context, stickerDirPath: String): Boolean {
		val sourceStickers = walkStickers(DocumentFile.fromTreeUri(context, Uri.parse(stickerDirPath)))
		val lastSignature = AppPreferences(context).stickerDirSignature
		return signatureOf(sourceStickers) != lastSignature
	}

	/**
	 * Re-imports from [stickerDirPath] only if [hasSourceChanged] - the "pull to refresh"
	 * reload shared by the keyboard's own pull-to-refresh and the Stickers page's refresh action, so
	 * both mean exactly the same thing. A no-op when the source's contents already match what's
	 * imported, so a refresh with nothing new to pick up doesn't pay for a full wipe-and-copy.
	 */
	suspend fun reimportIfChanged(context: Context, toaster: Toaster, stickerDirPath: String) {
		val changed = try {
			hasSourceChanged(context, stickerDirPath)
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
	 * dirty on success so an already-running keyboard picks up the change next time it's shown, and
	 * resyncs the stored source-directory signature (see [resyncStickerDirSignature]) so a later
	 * deletion of one of these stickers is still noticed by [hasSourceChanged]. Skips once
	 * [packName] reaches [MAX_PACK_SIZE] stickers total. Returns the internal-storage copies that were
	 * actually created, in the order [photoUris] was given.
	 */
	suspend fun importPhotosToPack(
		context: Context,
		packName: String,
		photoUris: List<Uri>,
	): List<File> =
		withContext(Dispatchers.IO) {
			val packDir = File(context.filesDir, "stickers/$packName")
			packDir.mkdirs()
			var packSize = packDir.listFiles { file -> file.isFile }?.size ?: 0
			val addedFiles = mutableListOf<File>()
			for (uri in photoUris) {
				if (packSize >= MAX_PACK_SIZE) break
				val addedFile = copyPhotoIntoPack(context, uri, packDir, packName)
				if (addedFile != null) {
					addedFiles.add(addedFile)
					packSize++
				}
			}
			if (addedFiles.isNotEmpty()) {
				KeyboardRefreshSignal.markDirty()
				resyncStickerDirSignature(context)
			}
			addedFiles
		}

	/**
	 * Deletes [files] (internal-storage sticker files) from disk - and, best-effort, their matching
	 * copies from the external sticker source directory too, so a future "Reload stickers" (which
	 * wipes and re-imports the internal copy from scratch) doesn't bring them back. Shared by the
	 * Stickers page's single-sticker delete (full-screen preview) and its bulk multi-select delete;
	 * marks [KeyboardRefreshSignal] dirty on success and resyncs the stored source-directory signature
	 * the same way [importPhotosToPack] does for additions, so a later "Reload stickers" doesn't
	 * re-copy a deleted sticker back in from the external folder. Returns the number of files actually
	 * deleted.
	 */
	suspend fun delete(context: Context, files: Collection<File>): Int =
		withContext(Dispatchers.IO) {
			var deletedCount = 0
			for (file in files) {
				val packName = file.parentFile?.name ?: continue
				if (file.delete()) {
					deletedCount++
					deleteFromExternalSourceDir(context, packName, file.name)
				}
			}
			if (deletedCount > 0) {
				KeyboardRefreshSignal.markDirty()
				resyncStickerDirSignature(context)
			}
			deletedCount
		}

	/**
	 * The file [file] would become if renamed to [newBaseName] - keeping its extension, since the user
	 * edits just the name part. Null if [newBaseName] is blank or contains a path separator.
	 */
	fun renamed(file: File, newBaseName: String): File? {
		val baseName = newBaseName.trim()
		if (baseName.isEmpty() || baseName.contains('/') || baseName.contains('\u0000')) return null
		val extension = file.extension
		val newName = if (extension.isEmpty()) baseName else "$baseName.$extension"
		return File(file.parentFile ?: return null, newName)
	}

	/**
	 * Renames [file] on disk to [newBaseName] (keeping its extension) - and, best-effort, its matching
	 * copy in the external sticker source directory, so a future "Reload stickers" doesn't bring the old
	 * name back. Marks [KeyboardRefreshSignal] dirty and resyncs the stored source-directory signature
	 * the same way [delete] does. Returns the renamed file, or null if the name is invalid,
	 * already taken, or the rename failed.
	 */
	suspend fun rename(context: Context, file: File, newBaseName: String): File? =
		withContext(Dispatchers.IO) {
			val target = renamed(file, newBaseName) ?: return@withContext null
			if (target == file) return@withContext file
			val modified = file.lastModified()
			if (target.exists() || !file.renameTo(target)) return@withContext null
			// Stickers are ordered by modified time, so keep it to leave the sticker where it was.
			target.setLastModified(modified)
			file.parentFile?.name?.let { renameInExternalSourceDir(context, it, file.name, target.name) }
			KeyboardRefreshSignal.markDirty()
			resyncStickerDirSignature(context)
			target
		}

	/** Best-effort rename of [oldName] to [newName] in [packName]'s external source folder, if any. */
	private fun renameInExternalSourceDir(
		context: Context,
		packName: String,
		oldName: String,
		newName: String,
	) {
		val path = AppPreferences(context).stickerDirPath ?: return
		try {
			val rootDir = DocumentFile.fromTreeUri(context, Uri.parse(path)) ?: return
			val packDocDir = rootDir.findFile(packName) ?: return
			packDocDir.findFile(oldName)?.renameTo(newName)
		} catch (e: Exception) {
			XLog.e("There was an error renaming a photo in the external sticker source directory!")
			XLog.e(e)
		}
	}

	/**
	 * Best-effort deletion of [fileName] from [packName]'s folder in the external sticker source
	 * directory, if one is configured - silently does nothing/fails otherwise, since the internal
	 * copy is already gone regardless.
	 */
	private fun deleteFromExternalSourceDir(context: Context, packName: String, fileName: String) {
		val path = AppPreferences(context).stickerDirPath ?: return
		try {
			val rootDir = DocumentFile.fromTreeUri(context, Uri.parse(path)) ?: return
			val packDocDir = rootDir.findFile(packName) ?: return
			packDocDir.findFile(fileName)?.delete()
		} catch (e: Exception) {
			XLog.e("There was an error deleting a photo from the external sticker source directory!")
			XLog.e(e)
		}
	}

	/**
	 * Re-computes and persists [AppPreferences.stickerDirSignature] from the external sticker source
	 * directory's current contents, if one is configured. Without this, a sticker copied there by
	 * [importPhotosToPack] (rather than by a full [StickerImporter.importStickers] pass) would never be
	 * reflected in the stored signature - so if that sticker were later deleted directly from the
	 * external folder, its contents would return to exactly matching the older, pre-addition signature,
	 * [hasSourceChanged] would see no difference, and the stale internal copy would never get
	 * cleaned up by a "Reload stickers"/pull-to-refresh, no matter how many times it's tried.
	 */
	private fun resyncStickerDirSignature(context: Context) {
		val prefs = AppPreferences(context)
		val path = prefs.stickerDirPath ?: return
		try {
			val sourceStickers = walkStickers(DocumentFile.fromTreeUri(context, Uri.parse(path)))
			prefs.stickerDirSignature = signatureOf(sourceStickers)
		} catch (e: Exception) {
			XLog.e("Failed to resync the sticker source directory signature after an add-photo import")
			XLog.e(e)
		}
	}

	/**
	 * Copies a single photo from [uri] into [packDir] - and, best-effort, into the matching pack folder
	 * of the external sticker source directory too.
	 *
	 * @return the internal copy (the one the keyboard actually reads), or null if it failed
	 */
	private fun copyPhotoIntoPack(
		context: Context,
		uri: Uri,
		packDir: File,
		packName: String,
	): File? {
		return try {
			val mimeType = context.contentResolver.getType(uri)
			val extension = mimeType?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) } ?: "jpg"
			val fileName = "imported_${System.currentTimeMillis()}_${System.nanoTime()}.$extension"

			val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
			val destFile = File(packDir, fileName)
			destFile.outputStream().use { it.write(bytes) }

			copyToExternalSourceDir(context, packName, fileName, bytes)
			destFile
		} catch (e: IOException) {
			XLog.e("There was an IOException when copying a photo into '$packName'!")
			XLog.e(e)
			null
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
	internal data class SourceSticker(val file: DocumentFile, val packName: String)

	/** An order-independent fingerprint of a set of source files, sensitive to any file being added,
	 * removed, resized, or having its modified time changed, or moved to a different pack. */
	internal fun signatureOf(sourceStickers: List<SourceSticker>): String {
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
	internal fun walkStickers(rootNode: DocumentFile?): List<SourceSticker> {
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
}
