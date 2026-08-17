package com.lukeneedham.stickerboard

import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.GestureDetector
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.elvishew.xlog.XLog
import com.google.android.material.appbar.MaterialToolbar
import com.lukeneedham.stickerboard.adapter.StickerBoardAdapter
import com.lukeneedham.stickerboard.model.BoardItem
import com.lukeneedham.stickerboard.model.StickerPack
import com.lukeneedham.stickerboard.utilities.StickerClickListener
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
class StickerGalleryActivity : AppCompatActivity(), StickerClickListener {
	private lateinit var sharedPreferences: SharedPreferences
	private lateinit var backupSharedPreferences: SharedPreferences
	private lateinit var toaster: Toaster
	private lateinit var internalDir: File
	private lateinit var recyclerView: RecyclerView
	private lateinit var emptyText: TextView

	private var iconsPerX = 4
	private var iconSize = 0
	private var insensitiveSort = false
	private var vibrate = true
	private var boardAdapter: StickerBoardAdapter? = null
	private var pendingPackName: String? = null

	private val gestureDetector by lazy {
		GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {})
	}

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
		setContentView(R.layout.activity_sticker_gallery)
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
		recomputeIconSize()

		val toolbar = findViewById<MaterialToolbar>(R.id.stickerGalleryToolbar)
		val navIcon = getDrawable(R.drawable.ic_back)?.mutate()
		navIcon?.setTint(getColor(R.color.onAccent))
		toolbar.navigationIcon = navIcon
		toolbar.setNavigationOnClickListener { finish() }

		this.recyclerView = findViewById(R.id.stickerGalleryRecyclerView)
		this.emptyText = findViewById(R.id.stickerGalleryEmptyText)

		buildBoard()
	}

	/** Re-scan packs in case they changed while this activity wasn't in the foreground. */
	override fun onResume() {
		super.onResume()
		buildBoard()
	}

	private fun recomputeIconSize() {
		val totalIconPadding =
			(resources.getDimension(R.dimen.sticker_padding) * 2 * (iconsPerX + 1)).toInt()
		iconSize =
			((resources.displayMetrics.widthPixels - totalIconPadding) / iconsPerX.toFloat()).toInt()
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

	/** Build the board the first time, or refresh it in place (preserving scroll) afterwards. */
	private fun buildBoard() {
		val items = computeBoardItems()
		emptyText.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
		recyclerView.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE

		val existingAdapter = boardAdapter
		if (existingAdapter != null) {
			existingAdapter.updateIconSize(iconSize)
			existingAdapter.updateItems(items)
			return
		}

		val layoutManager = GridLayoutManager(this, iconsPerX, RecyclerView.VERTICAL, false)
		val adapter =
			StickerBoardAdapter(iconSize, items, this, gestureDetector, vibrate) { packName ->
				onAddPhotoClicked(packName)
			}
		layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
			override fun getSpanSize(position: Int): Int =
				if (adapter.isFullWidth(position)) iconsPerX else 1
		}
		recyclerView.layoutManager = layoutManager
		recyclerView.adapter = adapter
		boardAdapter = adapter
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
					buildBoard()
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

	override fun onStickerClicked(sticker: File) = showStickerPreview(sticker)

	override fun onStickerLongClicked(sticker: File) = showStickerPreview(sticker)

	/** A simple, read-only enlarged preview of a sticker - tap the image (or outside) to dismiss. */
	private fun showStickerPreview(sticker: File) {
		val view = layoutInflater.inflate(R.layout.sticker_preview, null, false)
		view.findViewById<TextView>(R.id.stickerPreviewPackName).text =
			prettifyPackName(sticker.parentFile?.name ?: "")
		view.findViewById<TextView>(R.id.stickerPreviewStickerName).text = trimString(sticker.name)

		val dialog = AlertDialog.Builder(this).setView(view).create()
		view.findViewById<ImageButton>(R.id.stickerPreviewImage).apply {
			load(sticker)
			contentDescription = trimString(sticker.name)
			setOnClickListener { dialog.dismiss() }
		}
		dialog.show()
	}
}
