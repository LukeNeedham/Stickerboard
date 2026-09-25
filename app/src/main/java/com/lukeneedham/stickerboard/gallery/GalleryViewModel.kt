package com.lukeneedham.stickerboard.gallery

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.elvishew.xlog.XLog
import com.lukeneedham.stickerboard.R
import com.lukeneedham.stickerboard.data.AppPreferences
import com.lukeneedham.stickerboard.model.BoardItem
import com.lukeneedham.stickerboard.model.StickerPack
import com.lukeneedham.stickerboard.prettifyPackName
import com.lukeneedham.stickerboard.utilities.StickerImporter
import com.lukeneedham.stickerboard.utilities.Toaster
import com.lukeneedham.stickerboard.utilities.deleteStickerFiles
import com.lukeneedham.stickerboard.utilities.importPhotosToPack
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.system.measureTimeMillis
import android.text.format.DateFormat as AndroidDateFormat

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
 * setting, which doesn't change within the page's lifetime. */
data class GalleryUiState(
	val items: List<BoardItem>? = null,
	val stickerDirDisplayName: String = "",
	val lastUpdateDate: String = "",
	val isRefreshing: Boolean = false,
	/** A sticker (or, if [scrollToFileName] is null, just a pack) to scroll the grid to as soon as
	 * [items] reflects it - e.g. one just added by [runPendingImport]. Cleared once consumed. */
	val scrollToPackName: String? = null,
	val scrollToFileName: String? = null,
)

/**
 * Owns [StickerGalleryPage]'s data and every side effect it triggers: scanning internal storage
 * for packs/stickers, choosing/reloading the external sticker source directory, and copying
 * newly-added gallery photos into a pack (and best-effort into that external source directory).
 */
class GalleryViewModel(application: Application) : AndroidViewModel(application) {
	private val prefs = AppPreferences(application)

	// Only ever passed through to StickerImporter, which logs warnings on it as it works - never
	// toasted, so re-importing here never pops up any message of its own (see refreshStickers below).
	private val toaster = Toaster()
	private val internalDir = File(application.filesDir, "stickers")

	val columns = prefs.iconsPerX.coerceIn(MIN_ICONS_PER_X, MAX_ICONS_PER_X)

	private val _uiState = MutableStateFlow(
		GalleryUiState(
			stickerDirDisplayName = currentStickerDirDisplayName(),
			lastUpdateDate = currentLastUpdateDate(),
		),
	)
	val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

	private fun currentLastUpdateDate(): String {
		val epochMillis = prefs.lastUpdateEpochMillis
		if (epochMillis < 0) {
			return getApplication<Application>().getString(R.string.update_sticker_pack_info_date)
		}
		return formatLastRefreshed(epochMillis)
	}

	/** The chosen source folder's path relative to its storage volume's own root (e.g.
	 * "/Pictures/Stickers"), not its raw content:// tree URI or the volume's absolute filesystem
	 * prefix (e.g. "/storage/emulated/0") - falling back to that raw URI if it can't be resolved. */
	private fun currentStickerDirDisplayName(): String {
		val path = prefs.stickerDirPath
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
		loadedPacks.keys.sorted()

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
	 * Copies the given gallery photo URIs into packName, up to MAX_PACK_SIZE stickers total (see
	 * [importPhotosToPack]). No feedback on success/failure/limit by design - the grid updating (or
	 * not) is the feedback.
	 */
	fun addPhotosToPack(packName: String, uris: List<Uri>) {
		viewModelScope.launch(Dispatchers.IO) {
			val addedFiles = importPhotosToPack(getApplication(), packName, uris)
			if (addedFiles.isEmpty()) return@launch
			val refreshedBoardItems = computeBoardItems()

			withContext(Dispatchers.Main) {
				prefs.numStickersImported += addedFiles.size
				_uiState.update { it.copy(items = refreshedBoardItems) }
			}
		}
	}

	/**
	 * Deletes [files] from internal storage (and, best-effort, the external sticker source
	 * directory), then rescans and refreshes everything [StickerGalleryPage] shows. Used for both
	 * the single-sticker delete (full-screen preview) and the bulk multi-select delete - a no-op
	 * if [files] is empty.
	 */
	fun deleteStickers(files: Set<File>) {
		if (files.isEmpty()) return
		viewModelScope.launch(Dispatchers.IO) {
			deleteStickerFiles(getApplication(), files)
			val items = computeBoardItems()
			withContext(Dispatchers.Main) {
				_uiState.update { it.copy(items = items) }
			}
		}
	}

	// Set once a pending import has been started, so a config change re-composing GalleryRoute (and
	// re-running its LaunchedEffect) can't kick off the same import a second time - unlike isRefreshing,
	// this stays true for the rest of this ViewModel's life, well after the import itself finishes.
	private var hasStartedPendingImport = false

	/**
	 * Runs a share-import that was deferred until landing here - see
	 * [com.lukeneedham.stickerboard.share.ShareImportRoute], which navigates to this page the instant
	 * a pack is picked rather than waiting for the copy itself. Shows the same isRefreshing spinner a
	 * manual reload does while [uris] are copied into [packName], then sets [GalleryUiState
	 * .scrollToPackName]/[GalleryUiState.scrollToFileName] to whichever one landed last. A no-op if
	 * called again, or with nothing to import.
	 */
	fun runPendingImport(packName: String, uris: List<Uri>) {
		if (hasStartedPendingImport || uris.isEmpty()) return
		hasStartedPendingImport = true
		_uiState.update { it.copy(isRefreshing = true) }
		viewModelScope.launch {
			val addedFiles = withContext(Dispatchers.IO) {
				importPhotosToPack(getApplication(), packName, uris)
			}
			if (addedFiles.isNotEmpty()) prefs.numStickersImported += addedFiles.size
			val items = withContext(Dispatchers.IO) { computeBoardItems() }
			_uiState.update {
				it.copy(
					items = items,
					isRefreshing = false,
					scrollToPackName = packName,
					scrollToFileName = addedFiles.lastOrNull()?.name,
				)
			}
		}
	}

	/** Clears [GalleryUiState.scrollToPackName]/[GalleryUiState.scrollToFileName] once
	 * [StickerGalleryPage] has scrolled to it, so a later, unrelated items update doesn't scroll
	 * there again. */
	fun onScrolledToTarget() {
		_uiState.update { it.copy(scrollToPackName = null, scrollToFileName = null) }
	}

	/**
	 * An intent that opens the user's chosen external sticker source directory in their device's
	 * file browser app, or null if none is configured. Onboarding requires a sticker source
	 * directory to be chosen before this page is reachable, so a null path here would be a bug
	 * rather than something to show the user a message about.
	 */
	fun openStickerFolderIntent(): Intent? {
		val path = prefs.stickerDirPath ?: return null
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
			prefs.lastUpdateEpochMillis = System.currentTimeMillis()
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
		val path = prefs.stickerDirPath ?: return
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
		prefs.stickerDirPath = stickerDirPath
		prefs.recentCache = ""
		prefs.compatCache = ""
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
