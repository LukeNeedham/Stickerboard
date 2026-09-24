package com.lukeneedham.stickerboard.keyboard

import android.content.Context
import com.elvishew.xlog.XLog
import com.lukeneedham.stickerboard.R
import com.lukeneedham.stickerboard.data.AppPreferences
import com.lukeneedham.stickerboard.model.BoardItem
import com.lukeneedham.stickerboard.model.StickerPack
import com.lukeneedham.stickerboard.prettifyPackName
import com.lukeneedham.stickerboard.splitNameIntoTerms
import com.lukeneedham.stickerboard.utilities.Cache
import com.lukeneedham.stickerboard.utilities.Toaster
import com.lukeneedham.stickerboard.utilities.reimportStickersIfChanged
import java.io.File

/** Bounds for [KeyboardModel.iconsPerX], matching the settings page's SeekBar range. */
private const val MIN_ICONS_PER_X = 2
private const val MAX_ICONS_PER_X = 6

/** Synthetic pack name used for the "recently used" section/nav icon. */
internal const val RECENT_PACK_NAME = "__recentSticker__"

/** Max number of rows the "recently used" section shows, regardless of iconsPerX/zoom level. */
private const val RECENT_ROW_LIMIT = 2

/** Max number of stickers shown at once in search results. */
private const val SEARCH_RESULT_LIMIT = 128

/**
 * The "Model" in the keyboard's MVC split (see [com.lukeneedham.stickerboard.KeyboardController]
 * for why MVC rather than a ViewModel): every piece of data the keyboard shows - loaded packs,
 * the recent/compat caches, and the persisted display prefs - plus the business logic that reads
 * and updates it. Framework-independent aside from taking a [Context] (for `filesDir`, string
 * resources, and [AppPreferences]), exactly like a Repository would in an app with a full
 * ViewModel layer; it has no dependency on the [android.inputmethodservice.InputMethodService]
 * that owns it.
 */
class KeyboardModel(context: Context) {
	private val appContext = context.applicationContext
	private val internalDir = File(appContext.filesDir, "stickers")
	private val prefs = AppPreferences(appContext)
	private val toaster = Toaster()

	var iconsPerX = prefs.iconsPerX
		private set

	var activePack: String = prefs.activePack
		private set

	val internalStickerDir: File get() = internalDir

	val compatCache = Cache()
	val recentCache = Cache()

	private var loadedPacks: Map<String, StickerPack> = emptyMap()
	private var allStickers: List<File> = emptyList()

	/** Ordered map of section-name -> board-item position of that section's header row. */
	private var headerPositions: LinkedHashMap<String, Int> = LinkedHashMap()

	init {
		recentCache.fromSharedPref(prefs.recentCache)
		compatCache.fromSharedPref(prefs.compatCache)
		loadPacks()
	}

	/** The height (in px) the keyboard was last dragged to, or [default] if none was saved yet. */
	fun savedKeyboardHeightPx(default: Int): Int = prefs.keyboardHeightPx(default)

	fun saveKeyboardHeight(heightPx: Int) {
		prefs.saveKeyboardHeight(heightPx)
	}

	fun hasRecentStickers(): Boolean = recentCache.toFiles().any { it.exists() }

	fun firstPackName(): String? = sortedPackNames().firstOrNull()

	fun sectionExists(packName: String): Boolean = headerPositions.containsKey(packName)

	/** Re-scan [internalDir] without touching the external source directory - cheap enough to call
	 * every time the keyboard becomes visible (see
	 * [com.lukeneedham.stickerboard.utilities.KeyboardRefreshSignal]), unlike [refreshStickers] which
	 * may also re-import from the (potentially large) external tree. */
	fun rescanFromDisk() = loadPacks()

	fun setActivePack(packName: String) {
		activePack = packName
	}

	/**
	 * Scan [internalDir] and (re)populate [loadedPacks]/[allStickers] from what's on disk. Safe to
	 * call again after the initial load - e.g. from [refreshStickers] - to pick up packs or
	 * stickers added since.
	 */
	private fun loadPacks() {
		val packs =
			internalDir.listFiles { obj: File ->
				obj.isDirectory && !obj.absolutePath.contains("__compatSticker__")
			}
				?: arrayOf()
		val newLoadedPacks = HashMap<String, StickerPack>()
		var newAllStickers = listOf<File>()
		for (file in packs) {
			val pack = StickerPack(file)
			if (pack.stickerList.isNotEmpty()) {
				newLoadedPacks[file.name] = pack
			}
			newAllStickers += pack.stickerList
		}
		loadedPacks = newLoadedPacks
		allStickers = newAllStickers
		XLog.i("Loaded all packs: [${loadedPacks.keys.joinToString(", ")}]")
	}

	/** Pack names in nav-bar/board order. */
	private fun sortedPackNames(): List<String> = loadedPacks.keys.sorted()

	/**
	 * Compute the flattened list of board items (recent section, always first, followed by every
	 * pack), populating [headerPositions] as a side effect so nav icons can jump straight to a
	 * section.
	 */
	fun boardItems(): List<BoardItem> {
		val items = mutableListOf<BoardItem>()
		val newHeaderPositions = LinkedHashMap<String, Int>()

		// recentCache just remembers paths of previously-sent stickers, with no way to notice a
		// sticker being deleted (or a whole reimport removing it) out from under it - filter out
		// anything no longer on disk so a deleted sticker doesn't linger here forever.
		val recentStickers = recentCache.toFiles()
			.filter { it.exists() }
			.asReversed()
			.take(iconsPerX * RECENT_ROW_LIMIT)
		newHeaderPositions[RECENT_PACK_NAME] = items.size
		items.add(BoardItem.Header(RECENT_PACK_NAME, appContext.getString(R.string.recent_heading)))
		if (recentStickers.isEmpty()) {
			items.add(BoardItem.EmptyMessage(RECENT_PACK_NAME, appContext.getString(R.string.recent_empty)))
		} else {
			for (sticker in recentStickers) {
				items.add(BoardItem.Sticker(sticker, RECENT_PACK_NAME))
			}
		}

		for (packName in sortedPackNames()) {
			val stickers = loadedPacks[packName]?.stickerList ?: continue
			if (stickers.isEmpty()) continue
			newHeaderPositions[packName] = items.size
			items.add(BoardItem.Header(packName, prettifyPackName(packName)))
			for (sticker in stickers) {
				items.add(BoardItem.Sticker(sticker, packName))
			}
		}
		headerPositions = newHeaderPositions
		return items
	}

	fun packNavIcons(): List<PackNavIcon> {
		val icons = mutableListOf(PackNavIcon(RECENT_PACK_NAME, null))
		for (packName in sortedPackNames()) {
			icons.add(PackNavIcon(packName, loadedPacks[packName]?.thumbSticker))
		}
		return icons
	}

	fun sectionIndex(packName: String): Int? = headerPositions[packName]

	/** Find the section a given board-item position belongs to (e.g. for scroll tracking). */
	fun sectionAt(itemIndex: Int): String? {
		var result: String? = null
		for ((packName, headerPosition) in headerPositions) {
			if (headerPosition <= itemIndex) result = packName else break
		}
		return result
	}

	// The same reload GalleryRoute's refresh action performs on the Stickers page, sharing its
	// logic - so both mean exactly the same thing. A no-op when no source directory is set, or its
	// contents still match what's already imported.
	suspend fun refreshStickers() {
		val stickerDirPath = prefs.stickerDirPath
		if (stickerDirPath != null) {
			reimportStickersIfChanged(appContext, toaster, stickerDirPath)
		}
		loadPacks()
	}

	fun searchStickers(query: String): List<File> {
		val queryTerms = splitNameIntoTerms(query)
		return allStickers
			.filter { file ->
				val terms = stickerSearchTerms(file)
				queryTerms.all { queryTerm ->
					terms.any { term -> term.contains(queryTerm, ignoreCase = true) }
				}
			}
			.take(SEARCH_RESULT_LIMIT)
	}

	/**
	 * Change how many stickers are shown per row, clamped to [MIN_ICONS_PER_X, MAX_ICONS_PER_X].
	 * Persists the new value; this also changes how many stickers fit in the recent section's
	 * [RECENT_ROW_LIMIT] rows, so callers should refetch [boardItems] afterwards.
	 *
	 * @return the clamped iconsPerX actually applied
	 */
	fun changeIconsPerX(delta: Int): Int {
		val newIconsPerX = (iconsPerX + delta).coerceIn(MIN_ICONS_PER_X, MAX_ICONS_PER_X)
		if (newIconsPerX != iconsPerX) {
			iconsPerX = newIconsPerX
			prefs.iconsPerX = newIconsPerX
		}
		return iconsPerX
	}

	fun recordStickerSent(sticker: File) {
		recentCache.add(sticker.path)
	}

	/** Persists everything that needs to survive to the next [android.view.inputmethod.InputConnection]. */
	fun persistSessionState() {
		XLog.i("Persisting preferences based on use, and closing...")
		prefs.persistSessionState(recentCache.toSharedPref(), compatCache.toSharedPref(), activePack)
	}
}

/**
 * A sticker's search terms: its file name (without extension) and its pack's directory name,
 * each split into individual words - so e.g. sticker "happy-cat_meme.png" in pack "funny_memes"
 * is searchable by "happy", "cat", "meme", "funny", or "memes" individually, not just as a match
 * against the whole file name.
 */
private fun stickerSearchTerms(file: File): List<String> =
	splitNameIntoTerms(file.nameWithoutExtension) + splitNameIntoTerms(file.parentFile?.name ?: "")
