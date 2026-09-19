package com.lukeneedham.stickerboard.gallery

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.elvishew.xlog.XLog
import com.lukeneedham.stickerboard.R
import com.lukeneedham.stickerboard.model.BoardItem
import com.lukeneedham.stickerboard.model.StickerPack
import com.lukeneedham.stickerboard.prettifyPackName
import com.lukeneedham.stickerboard.utilities.StickerImporter
import com.lukeneedham.stickerboard.utilities.Toaster
import com.lukeneedham.stickerboard.utilities.reimportStickersIfChanged
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.system.measureTimeMillis
import android.text.format.DateFormat as AndroidDateFormat

/** Maximum number of stickers allowed in a single pack, mirrors StickerImporter's limit. */
private const val MAX_PACK_SIZE = 128

/** Bounds for iconsPerX, matching the settings page's SeekBar range. */
private const val MIN_ICONS_PER_X = 2
private const val MAX_ICONS_PER_X = 6

/**
 * Minimum time to hold the pull-refresh indicator's `isRefreshing = true` state - mirrors
 * [com.lukeneedham.stickerboard.keyboard.KeyboardView]'s BoardGrid: without this floor, a
 * refresh that finishes within a single frame can flip true then false before PullToRefreshBox
 * observes a transition to animate, leaving the indicator stuck wherever the pull released it.
 */
private const val MIN_REFRESH_INDICATOR_MS = 500L

/** Everything [StickerGalleryPage] needs to render, besides the fixed [GalleryViewModel.columns]
 * and [GalleryViewModel.vibrate] settings, which don't change within the page's lifetime. */
data class GalleryUiState(
	val items: List<BoardItem>? = null,
	val stickerDirDisplayName: String = "",
	val lastUpdateDate: String = "",
	val isRefreshing: Boolean = false,
)

/**
 * Owns [StickerGalleryPage]'s data and every side effect it triggers: scanning internal storage
 * for packs/stickers, choosing/reloading the external sticker source directory, and copying
 * newly-added gallery photos into a pack (and best-effort into that external source directory).
 */
class GalleryViewModel(application: Application) : AndroidViewModel(application) {
	private val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(application)
	private val backupSharedPreferences =
		application.getSharedPreferences("backup_prefs", Context.MODE_PRIVATE)

	// Only ever passed through to StickerImporter, which logs warnings on it as it works - never
	// toasted, so re-importing here never pops up any message of its own (see refreshStickers below).
	private val toaster = Toaster()
	private val internalDir = File(application.filesDir, "stickers")

	val columns =
		backupSharedPreferences.getInt("iconsPerX", 4).coerceIn(MIN_ICONS_PER_X, MAX_ICONS_PER_X)
	val vibrate = backupSharedPreferences.getBoolean("vibrate", true)
	private val insensitiveSort = backupSharedPreferences.getBoolean("insensitiveSort", false)

	private val _uiState = MutableStateFlow(
		GalleryUiState(
			stickerDirDisplayName = currentStickerDirDisplayName(),
			lastUpdateDate = currentLastUpdateDate(),
		),
	)
	val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

	private fun currentLastUpdateDate(): String {
		val epochMillis = sharedPreferences.getLong("lastUpdateEpochMillis", -1L)
		if (epochMillis < 0) {
			return getApplication<Application>().getString(R.string.update_sticker_pack_info_date)
		}
		return formatLastRefreshed(epochMillis)
	}

	/** The chosen source folder's path relative to its storage volume's own root (e.g.
	 * "/Pictures/Stickers"), not its raw content:// tree URI or the volume's absolute filesystem
	 * prefix (e.g. "/storage/emulated/0") - falling back to that raw URI if it can't be resolved. */
	private fun currentStickerDirDisplayName(): String {
		val path = sharedPreferences.getString("stickerDirPath", null)
			?: return getApplication<Application>().getString(R.string.update_sticker_pack_info_path)
		return try {
			val documentId = DocumentsContract.getTreeDocumentId(Uri.parse(path))
			"/" + documentId.substringAfter(':', missingDelimiterValue = documentId)
		} catch (e: Exception) {
			XLog.e("Failed to resolve a friendly path for the sticker source directory")
			XLog.e(e)
			path
		}
	}

	private fun sortedPackNames(loadedPacks: Map<String, StickerPack>): List<String> =
		if (insensitiveSort) {
			loadedPacks.keys.sortedWith(String.CASE_INSENSITIVE_ORDER)
		} else {
			loadedPacks.keys.sorted()
		}

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

	/**
	 * Re-scans internal storage and refreshes everything [StickerGalleryPage] shows. Lifecycle
	 * .Event.ON_RESUME (which this is driven by) replays for a brand new observer, so this alone
	 * also covers the very first load.
	 */
	fun onResumed() {
		viewModelScope.launch {
			val (items, dirName) = withContext(Dispatchers.IO) {
				computeBoardItems() to currentStickerDirDisplayName()
			}
			_uiState.update { it.copy(items = items, stickerDirDisplayName = dirName) }
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
		val context = getApplication<Application>()
		val path = sharedPreferences.getString("stickerDirPath", null) ?: return
		try {
			val rootDir = DocumentFile.fromTreeUri(context, Uri.parse(path)) ?: return
			val packDocDir = rootDir.findFile(packName) ?: rootDir.createDirectory(packName) ?: return
			val newFile = packDocDir.createFile("application/octet-stream", fileName) ?: return
			context.contentResolver.openOutputStream(newFile.uri)?.use { it.write(bytes) }
		} catch (e: Exception) {
			XLog.e("There was an error copying a gallery photo into the external sticker source directory!")
			XLog.e(e)
		}
	}

	/**
	 * Copies a single gallery photo into packDir - and, best-effort, into the matching pack folder
	 * of the external sticker source directory too - returning true if the internal copy (the one
	 * the keyboard actually reads) succeeded.
	 */
	private fun copyPhotoToPack(uri: Uri, packDir: File, packName: String): Boolean {
		val context = getApplication<Application>()
		return try {
			val mimeType = context.contentResolver.getType(uri)
			val extension =
				mimeType?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) } ?: "jpg"
			val fileName = "gallery_${System.currentTimeMillis()}_${System.nanoTime()}.$extension"

			val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return false
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
	 * Copies the given gallery photo URIs into packName, up to MAX_PACK_SIZE stickers total. No
	 * feedback on success/failure/limit by design - the grid updating (or not) is the feedback.
	 */
	fun addPhotosToPack(packName: String, uris: List<Uri>) {
		val packDir = File(internalDir, packName)
		viewModelScope.launch(Dispatchers.IO) {
			packDir.mkdirs()
			var packSize = packDir.listFiles { file -> file.isFile }?.size ?: 0
			var addedCount = 0
			for (uri in uris) {
				if (packSize >= MAX_PACK_SIZE) break
				if (copyPhotoToPack(uri, packDir, packName)) {
					addedCount++
					packSize++
				}
			}

			if (addedCount == 0) return@launch
			val refreshedBoardItems = computeBoardItems()

			withContext(Dispatchers.Main) {
				sharedPreferences.edit()
					.putInt(
						"numStickersImported",
						sharedPreferences.getInt("numStickersImported", 0) + addedCount,
					)
					.apply()
				_uiState.update { it.copy(items = refreshedBoardItems) }
			}
		}
	}

	/**
	 * An intent that opens the user's chosen external sticker source directory in their device's
	 * file browser app, or null if none is configured. Onboarding requires a sticker source
	 * directory to be chosen before this page is reachable, so a null path here would be a bug
	 * rather than something to show the user a message about.
	 */
	fun openStickerFolderIntent(): Intent? {
		val path = sharedPreferences.getString("stickerDirPath", null) ?: return null
		return try {
			val treeUri = Uri.parse(path)
			val docUri =
				DocumentsContract.buildDocumentUriUsingTree(
					treeUri,
					DocumentsContract.getTreeDocumentId(treeUri),
				)
			Intent(Intent.ACTION_VIEW).apply {
				setDataAndType(docUri, DocumentsContract.Document.MIME_TYPE_DIR)
				addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
			}
		} catch (e: Exception) {
			XLog.e("Failed to open the sticker source directory in a file browser app")
			XLog.e(e)
			null
		}
	}

	/**
	 * Runs [reimport] (which does the actual disk work, off the main thread) then rescans internal
	 * storage and refreshes everything [StickerGalleryPage] shows - all under the same
	 * isRefreshing spinner, held for at least [MIN_REFRESH_INDICATOR_MS] so the pull-to-refresh
	 * indicator always gets a visible transition to animate away.
	 */
	private fun runRefresh(reimport: suspend () -> Unit) {
		if (_uiState.value.isRefreshing) return
		_uiState.update { it.copy(isRefreshing = true) }
		viewModelScope.launch {
			val (items, dirName) = withContext(Dispatchers.IO) {
				val elapsedMs = measureTimeMillis { reimport() }
				delay((MIN_REFRESH_INDICATOR_MS - elapsedMs).coerceAtLeast(0))
				computeBoardItems() to currentStickerDirDisplayName()
			}
			sharedPreferences.edit()
				.putLong("lastUpdateEpochMillis", System.currentTimeMillis())
				.apply()
			_uiState.update {
				it.copy(
					items = items,
					stickerDirDisplayName = dirName,
					lastUpdateDate = currentLastUpdateDate(),
					isRefreshing = false,
				)
			}
		}
	}

	/** Re-imports from the current source directory only if its contents actually changed - the
	 * same reload the keyboard's own pull-to-refresh performs - then rescans internal storage. */
	fun refreshStickers() {
		val path = sharedPreferences.getString("stickerDirPath", null) ?: return
		runRefresh { reimportStickersIfChanged(getApplication(), toaster, path) }
	}

	/** Switches the sticker source to [path] and does a full (re)import from it, since it's new. */
	private fun changeStickerDirectory(path: String) {
		runRefresh { StickerImporter(getApplication(), toaster).importStickers(path) }
	}

	/**
	 * Grants persistable access to the newly chosen source directory (if any), persists it, and
	 * imports from it in full (it's new, so there's nothing to diff against). [uri] may be null if
	 * the picker was cancelled/failed, matching how the underlying ActivityResultContract reports
	 * that.
	 */
	fun onDirectorySelected(uri: Uri?) {
		val context = getApplication<Application>()
		if (uri != null) {
			val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
			context.contentResolver.takePersistableUriPermission(uri, takeFlags)
		}
		val stickerDirPath = uri.toString()
		sharedPreferences.edit()
			.putString("stickerDirPath", stickerDirPath)
			.putString("recentCache", "")
			.putString("compatCache", "")
			.apply()
		changeStickerDirectory(stickerDirPath)
	}
}

/**
 * Formats [epochMillis] as a short, always-local time - just hours and minutes, with the date
 * prepended only if it isn't today, and the year only added to that date if it isn't this year.
 * Uses [AndroidDateFormat.getBestDateTimePattern] so the result still respects the user's own
 * locale (e.g. 12h vs 24h clock, day/month order) despite dropping seconds and the full date.
 */
private fun formatLastRefreshed(epochMillis: Long): String {
	val locale = Locale.getDefault()
	val then = Calendar.getInstance().apply { timeInMillis = epochMillis }
	val now = Calendar.getInstance()

	val timePattern = AndroidDateFormat.getBestDateTimePattern(locale, "Hm")
	val timeText = SimpleDateFormat(timePattern, locale).format(Date(epochMillis))

	val sameDay = then.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
		then.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
	if (sameDay) return timeText

	val sameYear = then.get(Calendar.YEAR) == now.get(Calendar.YEAR)
	val dateSkeleton = if (sameYear) "MMMd" else "yMMMd"
	val datePattern = AndroidDateFormat.getBestDateTimePattern(locale, dateSkeleton)
	val dateText = SimpleDateFormat(datePattern, locale).format(Date(epochMillis))
	return "$dateText $timeText"
}
