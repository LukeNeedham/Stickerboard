package com.lukeneedham.stickerboard.utilities

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import java.io.File

/** The stickers on disk and the work that changes them, for the ViewModels: [StickerFiles] with the [Context] held here. */
class StickerStorage(private val context: Context) {
	/** Where every pack's folder lives, in the app's internal storage. */
	val internalDir = File(context.filesDir, "stickers")

	fun getString(@StringRes id: Int): String = context.getString(id)

	/** Grants this app lasting access to the source directory [uri] the user picked. */
	fun takePersistableAccess(uri: Uri) {
		val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
		context.contentResolver.takePersistableUriPermission(uri, takeFlags)
	}

	suspend fun importPhotosToPack(packName: String, uris: List<Uri>): List<File> =
		StickerFiles.importPhotosToPack(context, packName, uris)

	suspend fun delete(files: Collection<File>): Int = StickerFiles.delete(context, files)

	suspend fun rename(file: File, newBaseName: String): File? = StickerFiles.rename(context, file, newBaseName)

	suspend fun reimportIfChanged(toaster: Toaster, stickerDirPath: String) =
		StickerFiles.reimportIfChanged(context, toaster, stickerDirPath)

	/** Imports every sticker from the source directory, and returns how many there are. */
	suspend fun importStickers(toaster: Toaster, stickerDirPath: String): Int =
		StickerImporter(context, toaster).importStickers(stickerDirPath)
}
