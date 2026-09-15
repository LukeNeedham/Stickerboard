package com.lukeneedham.stickerboard

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.inputmethodservice.InputMethodService
import android.inputmethodservice.InputMethodService.Insets
import android.os.Build.VERSION.SDK_INT
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.preference.PreferenceManager
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import coil.Coil
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.SvgDecoder
import coil.decode.VideoFrameDecoder
import coil.imageLoader
import com.elvishew.xlog.XLog
import com.lukeneedham.stickerboard.keyboard.KeyboardDataSource
import com.lukeneedham.stickerboard.keyboard.KeyboardScreen
import com.lukeneedham.stickerboard.keyboard.PackNavIcon
import com.lukeneedham.stickerboard.model.BoardItem
import com.lukeneedham.stickerboard.model.StickerPack
import com.lukeneedham.stickerboard.utilities.Cache
import com.lukeneedham.stickerboard.utilities.StickerSender
import com.lukeneedham.stickerboard.utilities.Toaster
import com.lukeneedham.stickerboard.utilities.startLogger
import java.io.File

/** Bounds for [ImageKeyboard.iconsPerX], matching the settings screen's SeekBar range. */
private const val MIN_ICONS_PER_X = 2
private const val MAX_ICONS_PER_X = 6

/** Default pixel height of the scrollable board viewport. */
private const val KEYBOARD_HEIGHT_PX = 800

/** Smallest height the board can be dragged down to via the pull bar. */
private const val MIN_KEYBOARD_HEIGHT_PX = 300

/** Largest height the board can be dragged up to, as a fraction of the screen height. */
private const val MAX_KEYBOARD_HEIGHT_FRACTION = 0.75f

/** Synthetic pack name used for the "recently used" section/ nav icon. */
private const val RECENT_PACK_NAME = "__recentSticker__"

/** Max number of rows the "recently used" section shows, regardless of iconsPerX/ zoom level. */
private const val RECENT_ROW_LIMIT = 2

/** Max number of stickers shown at once in search results. */
private const val SEARCH_RESULT_LIMIT = 128

/**
 * ImageKeyboard class inherits from the InputMethodService class - provides the keyboard
 * functionality. The UI itself is Jetpack Compose (see [com.lukeneedham.stickerboard.keyboard]);
 * this class owns the data (packs, caches, prefs) and side effects (sending a sticker, closing
 * the keyboard) that the Compose UI reads and calls back into via [KeyboardDataSource].
 */
class ImageKeyboard :
	InputMethodService(),
	LifecycleOwner,
	SavedStateRegistryOwner,
	KeyboardDataSource {
	private val lifecycleRegistry = LifecycleRegistry(this)
	override val lifecycle: Lifecycle get() = lifecycleRegistry

	// ComposeView requires both a ViewTreeLifecycleOwner and a ViewTreeSavedStateRegistryOwner to
	// be set before it attaches to the window, or it crashes immediately - InputMethodService
	// provides neither by default the way an Activity/Fragment does.
	private val savedStateRegistryController = SavedStateRegistryController.create(this)
	override val savedStateRegistry: SavedStateRegistry
		get() = savedStateRegistryController.savedStateRegistry

	// onCreate
	//  Shared Preferences
	private lateinit var sharedPreferences: SharedPreferences
	private lateinit var backupSharedPreferences: SharedPreferences
	private var restoreOnClose = false
	private var scroll = false
	private var vibrate = false
	private var iconsPerX = 0
	private var insensitiveSort = false
	private var isPngFallback = true

	//  Constants
	private lateinit var internalDir: File
	private lateinit var toaster: Toaster

	//  Load Packs
	private lateinit var loadedPacks: HashMap<String, StickerPack>
	private var allStickers: List<File> = listOf()
	private var activePack = ""

	//  Caches
	private var compatCache = Cache()
	private var recentCache = Cache()

	// onStartInput
	private lateinit var stickerSender: StickerSender

	// onCreateInputView
	private var keyboardHeight = 0
	private var maxKeyboardHeightPx = 0

	//  Ordered map of section-name -> board-item position of that section's header row
	private var headerPositions: LinkedHashMap<String, Int> = LinkedHashMap()

	/**
	 * When the activity is created...
	 * - ensure coil can decode (and display) animated images
	 * - set the internal sticker dir, icons-per-col, caches and loaded-packs
	 */
	override fun onCreate() {
		// Misc
		super.onCreate()
		savedStateRegistryController.performRestore(null)
		lifecycleRegistry.currentState = Lifecycle.State.CREATED
		startLogger(filesDir)

		XLog.i("=".repeat(80))
		XLog.i("Loaded $packageName:${javaClass.name}")

		// Setup coil
		val imageLoader =
			ImageLoader.Builder(baseContext)
				.components {
					if (SDK_INT >= 28) {
						add(ImageDecoderDecoder.Factory())
					} else {
						add(GifDecoder.Factory())
					}
					add(VideoFrameDecoder.Factory())
					add(SvgDecoder.Factory())
				}
				.build()
		Coil.setImageLoader(imageLoader)
		//  Shared Preferences
		this.sharedPreferences = PreferenceManager.getDefaultSharedPreferences(baseContext)
		this.backupSharedPreferences =
			this.getSharedPreferences("backup_prefs", Context.MODE_PRIVATE)

		XLog.i("Loading private shared preferences: ${this.sharedPreferences.all}")
		XLog.i("Loading backup shared preferences: ${this.backupSharedPreferences.all}")

		this.restoreOnClose = this.backupSharedPreferences.getBoolean("restoreOnClose", false)
		this.scroll = this.backupSharedPreferences.getBoolean("scroll", false)
		this.vibrate = this.backupSharedPreferences.getBoolean("vibrate", true)
		this.insensitiveSort = this.backupSharedPreferences.getBoolean("insensitiveSort", false)
		this.isPngFallback = this.backupSharedPreferences.getBoolean("isPngFallback", true)

		this.iconsPerX = this.backupSharedPreferences.getInt("iconsPerX", 4)
		//  Constants
		this.internalDir = File(filesDir, "stickers")
		this.toaster = Toaster(baseContext)
		//  Load Packs
		loadPacks()
		this.activePack = this.sharedPreferences.getString("activePack", "").toString()
		//  Caches
		this.sharedPreferences.getString("recentCache", "")?.let {
			this.recentCache.fromSharedPref(it)
		}
		this.sharedPreferences.getString("compatCache", "")?.let {
			this.compatCache.fromSharedPref(it)
		}
		window.window?.navigationBarColor = getColor(R.color.bg)
	}

	/**
	 * When the keyboard is first drawn, build the Compose view. keyboardHeight/ maxKeyboardHeightPx
	 * are pinned up-front so the IME window itself never needs to be resized while the pull bar is
	 * dragged - see [onComputeInsets].
	 *
	 * @return View the Compose keyboard view
	 */
	override fun onCreateInputView(): View {
		this.maxKeyboardHeightPx =
			(resources.displayMetrics.heightPixels * MAX_KEYBOARD_HEIGHT_FRACTION).toInt()
		this.keyboardHeight =
			this.backupSharedPreferences.getInt("keyboardHeight", KEYBOARD_HEIGHT_PX)
				.coerceIn(MIN_KEYBOARD_HEIGHT_PX, this.maxKeyboardHeightPx)

		// Populate headerPositions so the initial section below can be resolved.
		boardItems()
		// The Recent section always exists in the board now (even empty, as a placeholder), so
		// check for actual recent stickers rather than just section presence, to still land on
		// the first real pack on a fresh install with no sticker history.
		val fallbackTarget = if (this.recentCache.toFiles().isNotEmpty()) {
			RECENT_PACK_NAME
		} else {
			sortedPackNames().firstOrNull()
		}
		val initialSection =
			(if (this.headerPositions.containsKey(activePack)) activePack else fallbackTarget)
				?: RECENT_PACK_NAME
		this.activePack = initialSection

		// Compose looks up the window's recomposer starting from the *root* of the window's view
		// hierarchy, not from the view returned below - and that root is a container the system
		// wraps around it (InputMethodService's own softinput window decor), several levels above
		// what onCreateInputView() returns. Tagging just the ComposeView below isn't enough; the
		// tree-owner lookup that matters walks up from the window's real decorView.
		window.window?.decorView?.let {
			it.setViewTreeLifecycleOwner(this)
			it.setViewTreeSavedStateRegistryOwner(this)
		}

		return ComposeView(this).apply {
			setViewTreeLifecycleOwner(this@ImageKeyboard)
			setViewTreeSavedStateRegistryOwner(this@ImageKeyboard)
			setContent {
				KeyboardScreen(
					dataSource = this@ImageKeyboard,
					initialIconsPerX = iconsPerX,
					initialKeyboardHeightPx = keyboardHeight,
					minKeyboardHeightPx = MIN_KEYBOARD_HEIGHT_PX,
					maxKeyboardHeightPx = maxKeyboardHeightPx,
					initialActivePack = initialSection,
					showCloseButton = backupSharedPreferences.getBoolean("showBackButton", true),
					showSearchButton = backupSharedPreferences.getBoolean("showSearchButton", true),
					vibrate = vibrate,
					swipeEnabled = scroll,
				)
			}
		}
	}

	/**
	 * Scan [internalDir] and (re)populate [loadedPacks]/[allStickers] from what's on disk. Safe to
	 * call again after the initial [onCreate] load - e.g. from [refreshStickers] - to pick up packs
	 * or stickers added since.
	 */
	private fun loadPacks() {
		this.loadedPacks = HashMap()
		this.allStickers = listOf()
		val packs =
			this.internalDir.listFiles { obj: File ->
				obj.isDirectory && !obj.absolutePath.contains("__compatSticker__")
			}
				?: arrayOf()
		for (file in packs) {
			val pack = StickerPack(file)
			if (pack.stickerList.isNotEmpty()) {
				this.loadedPacks[file.name] = pack
			}
			this.allStickers += pack.stickerList
		}
		XLog.i("Loaded all packs: [${this.loadedPacks.keys.joinToString(", ")}]")
	}

	override fun onWindowShown() {
		super.onWindowShown()
		lifecycleRegistry.currentState = Lifecycle.State.RESUMED
	}

	override fun onWindowHidden() {
		super.onWindowHidden()
		lifecycleRegistry.currentState = Lifecycle.State.CREATED
	}

	override fun onDestroy() {
		lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
		super.onDestroy()
	}

	/**
	 * Disable full-screen mode as content will likely be hidden by the IME.
	 *
	 * @return Boolean false
	 */
	override fun onEvaluateFullscreenMode(): Boolean {
		return false
	}

	/**
	 * keyboardHeight/ maxKeyboardHeightPx (see [onCreateInputView]) describe a fixed-height Compose
	 * canvas with a bottom-anchored, resizable content area inside it - report only that resizable
	 * area's bounds as "content" so touches in the transparent gap above it (if any) fall through
	 * to the app underneath, and so the system doesn't reserve the full fixed height for the
	 * keyboard.
	 */
	override fun onComputeInsets(outInsets: Insets) {
		super.onComputeInsets(outInsets)
		// The system can query insets before onCreateInputView() has run.
		if (this.maxKeyboardHeightPx <= 0) return
		val topInset = (this.maxKeyboardHeightPx - this.keyboardHeight).coerceAtLeast(0)
		outInsets.contentTopInsets = topInset
		outInsets.visibleTopInsets = topInset
		outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_CONTENT
	}

	/**
	 * When entering some input field update the list of supported-mimes
	 *
	 * @param info
	 * @param restarting
	 */
	override fun onStartInput(info: EditorInfo?, restarting: Boolean) {
		this.stickerSender = StickerSender(
			this.baseContext,
			this.toaster,
			this.internalDir,
			this.currentInputConnection,
			this.currentInputEditorInfo,
			this.compatCache,
			this.imageLoader,
			this.isPngFallback,
		)
	}

	/** When leaving some input field update the caches */
	override fun onFinishInput() {
		XLog.i("Updating sharedPreferences based on use, and closing...")
		val editor = this.sharedPreferences.edit()
		editor.putString("recentCache", this.recentCache.toSharedPref())
		editor.putString("compatCache", this.compatCache.toSharedPref())
		editor.putString("activePack", this.activePack)
		editor.apply()
		super.onFinishInput()
		if (restoreOnClose) {
			closeKeyboard()
		}
	}

	/** Pack names in nav-bar/ board order, respecting the case-insensitive-sort preference. */
	private fun sortedPackNames(): List<String> {
		return if (this.insensitiveSort) {
			this.loadedPacks.keys.sortedWith(String.CASE_INSENSITIVE_ORDER)
		} else {
			this.loadedPacks.keys.sorted()
		}
	}

	/**
	 * Compute the flattened list of board items (recent section, always first, followed by every
	 * pack), populating [headerPositions] as a side effect so nav icons can jump straight to a
	 * section.
	 */
	override fun boardItems(): List<BoardItem> {
		val items = mutableListOf<BoardItem>()
		this.headerPositions = LinkedHashMap()

		val recentStickers =
			this.recentCache.toFiles().reversedArray().take(this.iconsPerX * RECENT_ROW_LIMIT)
		this.headerPositions[RECENT_PACK_NAME] = items.size
		items.add(BoardItem.Header(RECENT_PACK_NAME, getString(R.string.recent_heading)))
		if (recentStickers.isEmpty()) {
			items.add(BoardItem.EmptyMessage(RECENT_PACK_NAME, getString(R.string.recent_empty)))
		} else {
			for (sticker in recentStickers) {
				items.add(BoardItem.Sticker(sticker, RECENT_PACK_NAME))
			}
		}

		for (packName in sortedPackNames()) {
			val stickers = this.loadedPacks[packName]?.stickerList ?: continue
			if (stickers.isEmpty()) continue
			this.headerPositions[packName] = items.size
			items.add(BoardItem.Header(packName, prettifyPackName(packName)))
			for (sticker in stickers) {
				items.add(BoardItem.Sticker(sticker, packName))
			}
		}
		return items
	}

	override fun packNavIcons(): List<PackNavIcon> {
		val icons = mutableListOf(PackNavIcon(RECENT_PACK_NAME, null))
		for (packName in sortedPackNames()) {
			icons.add(PackNavIcon(packName, this.loadedPacks[packName]?.thumbSticker))
		}
		return icons
	}

	override fun sectionIndex(packName: String): Int? = this.headerPositions[packName]

	/** Find the section a given board-item position belongs to (e.g. for scroll tracking). */
	override fun sectionAt(itemIndex: Int): String? {
		var result: String? = null
		for ((packName, headerPosition) in this.headerPositions) {
			if (headerPosition <= itemIndex) result = packName else break
		}
		return result
	}

	override fun previousSection(current: String): String? {
		val names = this.headerPositions.keys.toList()
		if (names.isEmpty()) return null
		val index = names.indexOf(current).let { if (it == -1) 0 else it }
		return names[if (index > 0) index - 1 else names.size - 1]
	}

	override fun nextSection(current: String): String? {
		val names = this.headerPositions.keys.toList()
		if (names.isEmpty()) return null
		val index = names.indexOf(current).let { if (it == -1) 0 else it }
		return names[(index + 1) % names.size]
	}

	override fun refreshStickers() = loadPacks()

	override fun searchStickers(query: String): List<File> {
		return this.allStickers
			.filter { it.name.contains(query, ignoreCase = true) }
			.take(SEARCH_RESULT_LIMIT)
	}

	/**
	 * Change how many stickers are shown per row, clamped to [MIN_ICONS_PER_X, MAX_ICONS_PER_X].
	 * Persists the new value; this also changes how many stickers fit in the recent section's
	 * [RECENT_ROW_LIMIT] rows, so callers should refetch [boardItems] afterwards.
	 *
	 * @return the clamped iconsPerX actually applied
	 */
	override fun changeIconsPerX(delta: Int): Int {
		val newIconsPerX = (this.iconsPerX + delta).coerceIn(MIN_ICONS_PER_X, MAX_ICONS_PER_X)
		if (newIconsPerX != this.iconsPerX) {
			this.iconsPerX = newIconsPerX
			this.backupSharedPreferences.edit().putInt("iconsPerX", newIconsPerX).apply()
		}
		return this.iconsPerX
	}

	override fun onKeyboardHeightChanged(heightPx: Int) {
		this.keyboardHeight = heightPx
	}

	override fun onKeyboardHeightSettled(heightPx: Int) {
		this.keyboardHeight = heightPx
		this.backupSharedPreferences.edit().putInt("keyboardHeight", heightPx).apply()
	}

	override fun onActivePackChanged(packName: String) {
		this.activePack = packName
	}

	override fun onStickerSend(sticker: File) {
		this.recentCache.add(sticker.path)
		this.stickerSender.sendSticker(sticker)
	}

	override fun onClose() = closeKeyboard()

	override fun onOpenSettings() {
		startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
	}

	private fun closeKeyboard() {
		if (SDK_INT >= 28) {
			this.switchToPreviousInputMethod()
		} else {
			(
				baseContext.getSystemService(INPUT_METHOD_SERVICE) as
					InputMethodManager
				).showInputMethodPicker()
		}
	}
}

/**
 * trimString
 *
 * for strings longer than 32 chars, trim to 32 chars and add ellipsis ...
 *
 *  @param str: String
 *  @return String
 */
fun trimString(str: String?): String {
	if (str == null) {
		return "null"
	}
	if (str.length > 32) {
		return str.substring(0, 32) + "..."
	}
	return str
}

/**
 * prettifyPackName
 *
 * Turn a sticker pack's directory name into a readable section header, e.g. "cat_memes" ->
 * "Cat Memes"
 *
 *  @param name: String
 *  @return String
 */
fun prettifyPackName(name: String): String {
	return name.split('_', '-', ' ')
		.filter { it.isNotEmpty() }
		.joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
}
