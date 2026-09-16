package com.lukeneedham.stickerboard

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.elvishew.xlog.XLog
import com.lukeneedham.stickerboard.gallery.StickerGalleryScreen
import com.lukeneedham.stickerboard.model.BoardItem
import com.lukeneedham.stickerboard.model.StickerPack
import com.lukeneedham.stickerboard.settings.StickerBoardSettingsTheme
import com.lukeneedham.stickerboard.utilities.Toaster
import com.lukeneedham.stickerboard.utilities.startLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Maximum number of stickers allowed in a single pack, mirrors StickerImporter's limit */
private const val MAX_PACK_SIZE = 128

/** Bounds for iconsPerX, matching the settings screen's SeekBar range. */
private const val MIN_ICONS_PER_X = 2
private const val MAX_ICONS_PER_X = 6

/**
 * StickerGalleryActivity shows every sticker pack using the same section/grid board layout as
 * ImageKeyboard, with an extra "add photo" cell at the end of each pack's section. Tapping that
 * cell opens the device's photo picker and copies the chosen photos straight into that pack.
 */
class StickerGalleryActivity : AppCompatActivity() {
	private lateinit var sharedPreferences: SharedPreferences
	private lateinit var backupSharedPreferences: SharedPreferences
	private lateinit var toaster: Toaster
	private lateinit var internalDir: File

	private var iconsPerX by mutableIntStateOf(4)
	private var insensitiveSort = false
	private var vibrate = true
	private var boardItems by mutableStateOf(emptyList<BoardItem>())
	private var pendingPackName: String? = null

	private val pickPhotosLauncher =
		registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
			val packName = pendingPackName
			pendingPackName = null
			if (packName != null && uris.isNotEmpty()) {
				addPhotosToPack(packName, uris)
			}
		}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		startLogger(filesDir)

		this.sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
		this.backupSharedPreferences = getSharedPreferences("backup_prefs", MODE_PRIVATE)
		this.toaster = Toaster(baseContext)
		this.internalDir = File(filesDir, "stickers")

		this.iconsPerX =
			this.backupSharedPreferences.getInt("iconsPerX", 4)
				.coerceIn(MIN_ICONS_PER_X, MAX_ICONS_PER_X)
		this.insensitiveSort = this.backupSharedPreferences.getBoolean("insensitiveSort", false)
		this.vibrate = this.backupSharedPreferences.getBoolean("vibrate", true)

		setContent {
			StickerBoardSettingsTheme {
				StickerGalleryScreen(
					items = boardItems,
					columns = iconsPerX,
					vibrate = vibrate,
					onBack = { finish() },
					onOpenFolder = { openStickerFolder() },
					onAddPhotoClick = { packName -> onAddPhotoClicked(packName) },
				)
			}
		}
	}

	/** Re-scan packs in case they changed while this activity wasn't in the foreground. */
	override fun onResume() {
		super.onResume()
		refreshBoard()
	}

	private fun sortedPackNames(loadedPacks: Map<String, StickerPack>): List<String> =
		if (insensitiveSort) {
			loadedPacks.keys.sortedWith(String.CASE_INSENSITIVE_ORDER)
		} else {
			loadedPacks.keys.sorted()
		}

	/** Mirrors ImageKeyboard's pack loading: every non-empty sub-directory of internalDir. */
	private fun computeBoardItems(): List<BoardItem> {
		val packs =
			internalDir.listFiles { file ->
				file.isDirectory && !file.absolutePath.contains("__compatSticker__")
			} ?: arrayOf()

		val loadedPacks = HashMap<String, StickerPack>()
		for (file in packs) {
			val pack = StickerPack(file)
			if (pack.stickerList.isNotEmpty()) {
				loadedPacks[file.name] = pack
			}
		}

		val items = mutableListOf<BoardItem>()
		for (packName in sortedPackNames(loadedPacks)) {
			val stickers = loadedPacks[packName]?.stickerList ?: continue
			items.add(BoardItem.Header(packName, prettifyPackName(packName)))
			for (sticker in stickers) {
				items.add(BoardItem.Sticker(sticker, packName))
			}
			items.add(BoardItem.AddPhoto(packName))
		}
		return items
	}

	/** Recomputes the board, preserving the LazyVerticalGrid's scroll position where it can. */
	private fun refreshBoard() {
		boardItems = computeBoardItems()
	}

	private fun onAddPhotoClicked(packName: String) {
		pendingPackName = packName
		pickPhotosLauncher.launch(
			PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
		)
	}

	/** Copies the given gallery photo URIs into packName, up to MAX_PACK_SIZE stickers total. */
	private fun addPhotosToPack(packName: String, uris: List<Uri>) {
		val packDir = File(internalDir, packName)
		lifecycleScope.launch(Dispatchers.IO) {
			packDir.mkdirs()
			var packSize = packDir.listFiles { file -> file.isFile }?.size ?: 0
			var addedCount = 0
			var skippedLimit = false
			for (uri in uris) {
				if (packSize >= MAX_PACK_SIZE) {
					skippedLimit = true
					break
				}
				if (copyPhotoToPack(uri, packDir, packName)) {
					addedCount++
					packSize++
				}
			}

			withContext(Dispatchers.Main) {
				val displayName = prettifyPackName(packName)
				if (addedCount > 0) {
					toaster.toast(getString(R.string.add_photo_050, addedCount, displayName))
					val editor = sharedPreferences.edit()
					editor.putInt(
						"numStickersImported",
						sharedPreferences.getInt("numStickersImported", 0) + addedCount,
					)
					editor.apply()
					refreshBoard()
				} else if (!skippedLimit) {
					toaster.toast(getString(R.string.add_photo_051, displayName))
				}
				if (skippedLimit) {
					toaster.toast(getString(R.string.imported_032, MAX_PACK_SIZE, displayName))
				}
			}
		}
	}

	/**
	 * Copies a single gallery photo into packDir - and, best-effort, into the matching pack folder
	 * of the external sticker source directory too - returning true if the internal copy (the one
	 * the keyboard actually reads) succeeded.
	 */
	private fun copyPhotoToPack(uri: Uri, packDir: File, packName: String): Boolean {
		return try {
			val mimeType = contentResolver.getType(uri)
			val extension =
				mimeType?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) } ?: "jpg"
			val fileName = "gallery_${System.currentTimeMillis()}_${System.nanoTime()}.$extension"

			val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return false
			File(packDir, fileName).outputStream().use { it.write(bytes) }

			copyToExternalSourceDir(packName, fileName, bytes)
			true
		} catch (e: IOException) {
			XLog.e("There was an IOException when copying a gallery photo into a pack!")
			XLog.e(e)
			false
		}
	}

	/**
	 * Best-effort copy of a just-added photo into the user's external sticker source directory, so
	 * that a future "Reload stickers" - which wipes and re-imports the internal copy from scratch -
	 * doesn't lose it. Silently does nothing if no source directory is configured; silently fails if
	 * the write doesn't succeed for any other reason, since the sticker is already usable from the
	 * internal copy regardless.
	 */
	private fun copyToExternalSourceDir(packName: String, fileName: String, bytes: ByteArray) {
		val stickerDirPath = sharedPreferences.getString("stickerDirPath", null) ?: return
		try {
			val rootDir = DocumentFile.fromTreeUri(this, Uri.parse(stickerDirPath)) ?: return
			val packDocDir = rootDir.findFile(packName) ?: rootDir.createDirectory(packName) ?: return
			val newFile = packDocDir.createFile("application/octet-stream", fileName) ?: return
			contentResolver.openOutputStream(newFile.uri)?.use { it.write(bytes) }
		} catch (e: Exception) {
			XLog.e("There was an error copying a gallery photo into the external sticker source directory!")
			XLog.e(e)
		}
	}

	/**
	 * Opens the user's chosen external sticker source directory in their device's file browser app,
	 * if one is configured and something can handle it.
	 */
	private fun openStickerFolder() {
		val stickerDirPath = sharedPreferences.getString("stickerDirPath", null)
		if (stickerDirPath == null) {
			toaster.toast(getString(R.string.open_folder_missing_dir))
			return
		}
		try {
			val treeUri = Uri.parse(stickerDirPath)
			val docUri =
				DocumentsContract.buildDocumentUriUsingTree(
					treeUri,
					DocumentsContract.getTreeDocumentId(treeUri),
				)
			val intent =
				Intent(Intent.ACTION_VIEW).apply {
					setDataAndType(docUri, DocumentsContract.Document.MIME_TYPE_DIR)
					addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
				}
			startActivity(intent)
		} catch (e: Exception) {
			XLog.e("Failed to open the sticker source directory in a file browser app")
			XLog.e(e)
			toaster.toast(getString(R.string.open_folder_052))
		}
	}
}
