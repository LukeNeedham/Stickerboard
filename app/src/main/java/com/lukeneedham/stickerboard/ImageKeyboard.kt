package com.lukeneedham.stickerboard

import android.content.Context
import android.content.SharedPreferences
import android.inputmethodservice.InputMethodService
import android.inputmethodservice.InputMethodService.Insets
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.view.iterator
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.Coil
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.SvgDecoder
import coil.decode.VideoFrameDecoder
import coil.imageLoader
import coil.load
import com.elvishew.xlog.XLog
import com.lukeneedham.stickerboard.adapter.StickerBoardAdapter
import com.lukeneedham.stickerboard.adapter.StickerPackAdapter
import com.lukeneedham.stickerboard.model.BoardItem
import com.lukeneedham.stickerboard.model.StickerPack
import com.lukeneedham.stickerboard.utilities.Cache
import com.lukeneedham.stickerboard.utilities.StickerClickListener
import com.lukeneedham.stickerboard.utilities.StickerSender
import com.lukeneedham.stickerboard.utilities.Toaster
import com.lukeneedham.stickerboard.utilities.startLogger
import java.io.File
import kotlin.math.abs

private const val SWIPE_THRESHOLD = 1
private const val SWIPE_VELOCITY_THRESHOLD = 1

/** Bounds for [ImageKeyboard.iconsPerX], matching the settings screen's SeekBar range. */
private const val MIN_ICONS_PER_X = 2
private const val MAX_ICONS_PER_X = 6

/** Cumulative pinch scale factor needed to change iconsPerX by one column. */
private const val PINCH_STEP_THRESHOLD = 1.15f

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

/** Synthetic activeSection marker while the search view is showing. */
private const val SEARCH_TAG = "__search__"

/**
 * ImageKeyboard class inherits from the InputMethodService class - provides the keyboard
 * functionality
 */
class ImageKeyboard : InputMethodService(), StickerClickListener {
	// onCreate
	//  Shared Preferences
	private lateinit var sharedPreferences: SharedPreferences
	private lateinit var backupSharedPreferences: SharedPreferences
	private var restoreOnClose = false
	private var scroll = false
	private var vibrate = false
	private var iconsPerX = 0
	private var iconSize = 0
	private var insensitiveSort = false
	private var isPngFallback = true

	//  Constants
	private lateinit var internalDir: File
	private var totalIconPadding = 0
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
	private lateinit var keyboardRoot: ViewGroup
	private lateinit var resizableArea: ViewGroup
	private lateinit var packsList: ViewGroup
	private lateinit var boardArea: ViewGroup
	private lateinit var topHScrollView: View
	private lateinit var packContent: ViewGroup
	private lateinit var pullBar: View
	private lateinit var closeButton: ImageButton
	private lateinit var searchButton: ImageButton
	private var keyboardHeight = 0
	private var pendingKeyboardHeight: Int? = null
	private var maxKeyboardHeightPx = 0
	private var qwertyWidth = 0

	private lateinit var gestureDetector: GestureDetector
	private lateinit var scaleGestureDetector: ScaleGestureDetector

	//  The unified, vertically-scrolling board holding every pack's stickers
	private lateinit var boardRecyclerView: RecyclerView

	// Cached search-mode content (query box + qwerty + results), built the first time search mode
	// is entered and reused on every later visit - including returning from the sticker preview -
	// so the query and results survive instead of resetting. Rebuilt from scratch whenever
	// onCreateInputView() runs again, since it belongs to a now-stale view hierarchy.
	private var searchContentView: View? = null

	// Resizes the current search results in place (no rebuild) to fit the current keyboard height.
	// Non-null once search content has been built; called after a resize so results keep filling
	// the available space instead of staying the size they were when search was opened.
	private var resizeSearchResults: (() -> Unit)? = null

	//  Ordered map of section-name -> adapter position of that section's header row
	private var headerPositions: LinkedHashMap<String, Int> = LinkedHashMap()
	private var activeSection = ""

	/**
	 * When the activity is created...
	 * - ensure coil can decode (and display) animated images
	 * - set the internal sticker dir, icon-padding, icon-size, icons-per-col, caches and
	 * loaded-packs
	 */
	override fun onCreate() {
		// Misc
		super.onCreate()
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
		this.totalIconPadding =
			(resources.getDimension(R.dimen.sticker_padding) * 2 * (this.iconsPerX + 1)).toInt()
		//  Constants
		this.internalDir = File(filesDir, "stickers")
		this.iconSize =
			(
				(resources.displayMetrics.widthPixels - this.totalIconPadding) /
					this.iconsPerX.toFloat()
				)
				.toInt()
		this.toaster = Toaster(baseContext)
		//  Load Packs
		this.loadedPacks = HashMap()
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
	 * When the keyboard is first drawn...
	 * - inflate keyboardLayout
	 * - set the keyboard height
	 * - create pack icons
	 *
	 * @return View keyboardLayout
	 */
	override fun onCreateInputView(): View {
		val keyboardLayout = View.inflate(baseContext, R.layout.keyboard_layout, null)
		gestureDetector = GestureDetector(baseContext, GestureListener())
		scaleGestureDetector = ScaleGestureDetector(baseContext, PinchZoomListener())

		// This view hierarchy is brand new, so any cached search content from a previous one -
		// this method can run again on the same service instance - is now stale.
		this.searchContentView = null
		this.resizeSearchResults = null

		this.keyboardRoot = keyboardLayout.findViewById(R.id.keyboardRoot)
		this.resizableArea = keyboardLayout.findViewById(R.id.resizableArea)
		this.packsList = keyboardLayout.findViewById(R.id.packsList)
		this.boardArea = keyboardLayout.findViewById(R.id.boardArea)
		this.topHScrollView = keyboardLayout.findViewById(R.id.topHScrollView)
		this.packContent = keyboardLayout.findViewById(R.id.packContent)
		this.pullBar = keyboardLayout.findViewById(R.id.pullBar)
		this.closeButton = keyboardLayout.findViewById(R.id.closeButton)
		this.searchButton = keyboardLayout.findViewById(R.id.searchButton)

		// Pin the IME window to its largest possible size up-front. Dragging the pull bar then
		// only resizes resizableArea (bottom-anchored) *inside* that fixed window - a cheap,
		// purely local layout pass - rather than resizing the window itself on every drag frame,
		// which is what caused the board to lag behind the finger and flicker.
		this.maxKeyboardHeightPx =
			(resources.displayMetrics.heightPixels * MAX_KEYBOARD_HEIGHT_FRACTION).toInt()
		this.keyboardRoot.layoutParams =
			ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, this.maxKeyboardHeightPx)

		this.keyboardHeight =
			this.backupSharedPreferences.getInt("keyboardHeight", KEYBOARD_HEIGHT_PX)
				.coerceIn(MIN_KEYBOARD_HEIGHT_PX, this.maxKeyboardHeightPx)
		this.boardArea.layoutParams?.height = this.keyboardHeight
		setupPullBar()
		setupTopBarButtons()
		createPackIcons()
		return keyboardLayout
	}

	/**
	 * Wire up the close/ search buttons that sit either side of the pull bar, honoring their
	 * show/ hide preferences.
	 */
	private fun setupTopBarButtons() {
		this.closeButton.visibility =
			if (this.backupSharedPreferences.getBoolean("showBackButton", true)) {
				View.VISIBLE
			} else {
				View.GONE
			}
		this.closeButton.load(getDrawable(R.drawable.ic_close))
		this.closeButton.setOnClickListener { closeKeyboard() }

		this.searchButton.visibility =
			if (this.backupSharedPreferences.getBoolean("showSearchButton", true)) {
				View.VISIBLE
			} else {
				View.GONE
			}
		this.searchButton.load(getDrawable(R.drawable.ic_search))
		this.searchButton.setOnClickListener {
			if (this.activeSection == SEARCH_TAG) {
				exitSearchView()
			} else {
				searchView()
			}
		}
	}

	/**
	 * Wire up the drag handle at the top of the keyboard so the user can resize the board
	 * vertically by dragging it up or down. The chosen height is persisted so it's remembered
	 * next time the keyboard is shown.
	 */
	private fun setupPullBar() {
		var startRawY = 0f
		var startHeight = 0

		this.pullBar.setOnTouchListener { _, event ->
			when (event.actionMasked) {
				MotionEvent.ACTION_DOWN -> {
					startRawY = event.rawY
					startHeight = this.keyboardHeight
					true
				}

				MotionEvent.ACTION_MOVE -> {
					val delta = (startRawY - event.rawY).toInt()
					val newHeight = (startHeight + delta)
						.coerceIn(MIN_KEYBOARD_HEIGHT_PX, this.maxKeyboardHeightPx)
					scheduleKeyboardHeight(newHeight)
					true
				}

				MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
					applyPendingKeyboardHeight()
					this.backupSharedPreferences.edit()
						.putInt("keyboardHeight", this.keyboardHeight)
						.apply()
					true
				}

				else -> false
			}
		}
	}

	/**
	 * Coalesce rapid drag updates into at most one layout pass per animation frame. Touch-move
	 * events can arrive far faster than the display refreshes, and resizing the board on every
	 * single one queued up more layout/window-resize work than a frame budget allows - that's
	 * what caused the board to lag behind the finger and flicker while dragging.
	 */
	private fun scheduleKeyboardHeight(height: Int) {
		if (height == this.keyboardHeight || height == this.pendingKeyboardHeight) return
		val alreadyScheduled = this.pendingKeyboardHeight != null
		this.pendingKeyboardHeight = height
		if (alreadyScheduled) return
		this.boardArea.postOnAnimation { applyPendingKeyboardHeight() }
	}

	/** Apply the most recently scheduled height, if any haven't been applied yet. */
	private fun applyPendingKeyboardHeight() {
		val target = this.pendingKeyboardHeight ?: return
		this.pendingKeyboardHeight = null
		if (target == this.keyboardHeight) return
		this.keyboardHeight = target
		this.boardArea.layoutParams?.height = target
		this.boardArea.requestLayout()
		// The results strip's own height tracks boardArea automatically via layout_weight, but the
		// sticker size within it is computed in code, so it needs an explicit resize to keep
		// filling the resized space instead of staying the size it was when search was opened.
		if (this.activeSection == SEARCH_TAG) {
			this.resizeSearchResults?.invoke()
		}
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
	 * keyboardRoot is pinned to a fixed, maximum-possible height (see [onCreateInputView]) so
	 * that dragging the pull bar never has to resize the actual IME window. That leaves a
	 * transparent, non-interactive gap above [resizableArea] whenever the board is smaller than
	 * that maximum - report only resizableArea's actual bounds as "content" so touches in that
	 * gap fall through to the app underneath, and so the system doesn't reserve the full fixed
	 * height for the keyboard.
	 */
	override fun onComputeInsets(outInsets: Insets) {
		super.onComputeInsets(outInsets)
		// The system can query insets before onCreateInputView() has run (e.g. while the input
		// view is still being set up), so these views may not exist yet.
		if (!this::keyboardRoot.isInitialized || !this::resizableArea.isInitialized) return
		val topInset = (this.keyboardRoot.height - this.resizableArea.height).coerceAtLeast(0)
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
	private fun computeBoardItems(): List<BoardItem> {
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

	/**
	 * Build the unified, vertically-scrolling board: every pack's stickers concatenated, each
	 * preceded by a full-width section header, and install a scroll listener that keeps the nav
	 * icons in sync with whichever section is currently on screen.
	 */
	private fun buildBoard() {
		XLog.i("Building sticker board")
		val items = computeBoardItems()

		val layoutManager = GridLayoutManager(this, iconsPerX, RecyclerView.VERTICAL, false)
		val adapter = StickerBoardAdapter(iconSize, items, this, gestureDetector, vibrate)
		layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
			override fun getSpanSize(position: Int): Int =
				if (adapter.isFullWidth(position)) iconsPerX else 1
		}

		val recyclerView = RecyclerView(this)
		recyclerView.layoutManager = layoutManager
		recyclerView.adapter = adapter
		// Without this, a final section with too few stickers to fill the viewport can never be
		// scrolled all the way to the top, so it's stuck partway down the screen and can't be
		// selected/ highlighted like every other section. Padding out the bottom (with clipping
		// disabled so it isn't drawn as a hard edge) gives it that room to scroll into.
		recyclerView.clipToPadding = false
		recyclerView.setPadding(0, 0, 0, this.keyboardHeight)
		recyclerView.addOnScrollListener(
			object : RecyclerView.OnScrollListener() {
				override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
					val firstVisible = layoutManager.findFirstVisibleItemPosition()
					if (firstVisible == RecyclerView.NO_POSITION) return
					sectionAt(firstVisible)?.let { updateActiveNavButton(it) }
				}
			},
		)
		recyclerView.addOnItemTouchListener(
			object : RecyclerView.OnItemTouchListener {
				override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
					scaleGestureDetector.onTouchEvent(e)
					// Once a second finger is down this is a pinch, not a tap/ swipe on a sticker -
					// intercept so children don't also react to it.
					return e.pointerCount > 1
				}

				override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
					scaleGestureDetector.onTouchEvent(e)
				}

				override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
			},
		)

		this.boardRecyclerView = recyclerView
		showBoard()
	}

	/**
	 * Change how many stickers are shown per row, clamped to [MIN_ICONS_PER_X, MAX_ICONS_PER_X].
	 * Persists the new value and resizes the already-built board in place (no rebuild), so scroll
	 * position is preserved. This also changes how many stickers fit in the recent section's
	 * [RECENT_ROW_LIMIT] rows, so the board contents are recomputed to match.
	 *
	 * @param requestedIconsPerX the desired iconsPerX, before clamping
	 */
	private fun updateIconsPerX(requestedIconsPerX: Int) {
		val newIconsPerX = requestedIconsPerX.coerceIn(MIN_ICONS_PER_X, MAX_ICONS_PER_X)
		if (newIconsPerX == this.iconsPerX) return
		this.iconsPerX = newIconsPerX
		this.backupSharedPreferences.edit().putInt("iconsPerX", newIconsPerX).apply()

		this.totalIconPadding =
			(resources.getDimension(R.dimen.sticker_padding) * 2 * (this.iconsPerX + 1)).toInt()
		this.iconSize =
			(
				(resources.displayMetrics.widthPixels - this.totalIconPadding) /
					this.iconsPerX.toFloat()
				)
				.toInt()

		if (!::boardRecyclerView.isInitialized) return
		(this.boardRecyclerView.layoutManager as? GridLayoutManager)?.spanCount = this.iconsPerX
		(this.boardRecyclerView.adapter as? StickerBoardAdapter)?.updateIconSize(this.iconSize)
		refreshBoard()
	}

	/**
	 * Recompute the board contents (e.g. after the recently-used section changes) and apply them to
	 * the existing board in place, preserving scroll position instead of rebuilding the view.
	 */
	private fun refreshBoard() {
		if (!this::boardRecyclerView.isInitialized) {
			buildBoard()
			return
		}
		val items = computeBoardItems()
		(this.boardRecyclerView.adapter as StickerBoardAdapter).updateItems(items)
	}

	/** Find the section a given board adapter-position belongs to (e.g. for scroll tracking). */
	private fun sectionAt(position: Int): String? {
		var result: String? = null
		for ((packName, headerPosition) in this.headerPositions) {
			if (headerPosition <= position) result = packName else break
		}
		return result
	}

	/** Ensure the board (as opposed to e.g. the search view) is the content on screen. */
	private fun showBoard() {
		this.topHScrollView.visibility = View.VISIBLE
		if (packContent.childCount == 1 && packContent.getChildAt(0) === this.boardRecyclerView) {
			return
		}
		packContent.removeAllViewsInLayout()
		packContent.addView(this.boardRecyclerView)
	}

	/** Leave the search view and return to the board, honoring the pack that was active before. */
	private fun exitSearchView() {
		val target = if (this.headerPositions.containsKey(this.activePack)) {
			this.activePack
		} else {
			this.headerPositions.keys.firstOrNull()
		}
		target?.let { jumpToSection(it) }
	}

	/**
	 * Jump straight to a section of the board - this is what nav icons do when tapped. Unlike the
	 * old tab-based navigation this doesn't swap out content, it just scrolls the shared board.
	 *
	 * @param packName the section (pack name, or [RECENT_PACK_NAME]) to jump to
	 */
	private fun jumpToSection(packName: String) {
		XLog.i("Jumping to section '$packName'")
		this.activePack = packName
		showBoard()
		val position = this.headerPositions[packName] ?: return
		(this.boardRecyclerView.layoutManager as GridLayoutManager)
			.scrollToPositionWithOffset(position, 0)
		updateActiveNavButton(packName)
	}

	/** Highlight whichever nav icon corresponds to [packName], clearing any other highlight. */
	private fun updateActiveNavButton(packName: String) {
		if (this.activeSection == packName) return
		this.activeSection = packName
		this.searchButton.isSelected = false
		for (packCard in this.packsList) {
			val packButton = packCard.findViewById<ImageButton>(R.id.stickerButton)
			packButton.isSelected = packButton.tag == packName
		}
	}

	/**
	 * Height available for the results strip below the query box and QWERTY rows, at the current
	 * keyboard height - used to size result stickers, not to set any layout height directly (the
	 * results strip fills that space itself via layout_weight).
	 */
	private fun currentSearchResultsHeight(): Int {
		return (
			this.keyboardHeight -
				(
					resources.getDimension(R.dimen.qwerty_row_height) +
						resources.getDimension(R.dimen.qwerty_row_height) * 4
					)
			).toInt()
	}

	/**
	 * Switch to search mode: highlight the search nav icon, hide the pack tab bar, and show the
	 * search content (query box + qwerty + results) - building it the first time and reusing it on
	 * every later visit, including returning from the sticker preview, so the query and results
	 * survive instead of resetting.
	 */
	private fun searchView() {
		XLog.i("Switching to search")
		for (packCard in this.packsList) {
			val packButton = packCard.findViewById<ImageButton>(R.id.stickerButton)
			packButton.isSelected = false
		}
		this.searchButton.isSelected = true
		this.activeSection = SEARCH_TAG
		// packContent fills boardArea entirely once the tab bar is hidden (it's boardArea's only
		// weighted child), so its resulting height is exactly keyboardHeight - no manual patching
		// of packContent's own height needed.
		this.topHScrollView.visibility = View.GONE

		val content = this.searchContentView ?: buildSearchContent().also { this.searchContentView = it }
		packContent.removeAllViewsInLayout()
		packContent.addView(content)
		// The keyboard may have been resized while search mode was hidden (e.g. dragged from the
		// board), which the cached results wouldn't have picked up - resize them now to match.
		this.resizeSearchResults?.invoke()
	}

	/** Build the search UI once; [searchContentView] caches the result for reuse. */
	private fun buildSearchContent(): View {
		qwertyWidth = (resources.displayMetrics.widthPixels / 10.4).toInt()

		val qwertyLayout = layoutInflater.inflate(R.layout.qwerty_layout, packContent, false)
		val searchText = qwertyLayout.findViewById<TextView>(R.id.search_text)
		val searchResults = qwertyLayout.findViewById<LinearLayout>(R.id.search_results)

		var searchResultsAdapter: StickerPackAdapter? = null

		fun searchStickers(query: String): List<File> {
			return this.allStickers.filter { it.name.contains(query, ignoreCase = true) }
		}

		fun updateSearchResults(stickers: List<File>) {
			val recyclerView = RecyclerView(baseContext)
			val adapter = StickerPackAdapter(
				(currentSearchResultsHeight() * 0.9).toInt(),
				stickers.take(128).toTypedArray(),
				this,
				gestureDetector,
				this.vibrate,
			)
			searchResultsAdapter = adapter
			val layoutManager = GridLayoutManager(
				baseContext,
				1,
				RecyclerView.HORIZONTAL,
				false,
			)
			recyclerView.layoutManager = layoutManager
			recyclerView.adapter = adapter
			searchResults.removeAllViewsInLayout()
			searchResults.addView(recyclerView)
		}
		// Called after a resize: resizes the existing results in place (no rebuild, so it stays
		// cheap even mid-drag) to keep them filling the available space.
		this.resizeSearchResults = {
			searchResultsAdapter?.updateIconSize((currentSearchResultsHeight() * 0.9).toInt())
		}

		fun searchAppend(char: String) {
			searchText.append(char)
			val query = searchText.text.toString()
			updateSearchResults(searchStickers(query))
		}

		fun searchBack(char: String) {
			if (searchText.text.isNotEmpty()) {
				val newText = searchText.text.substring(0, searchText.text.length - 1)
				searchText.text = newText
			}
			val query = searchText.text.toString()
			updateSearchResults(searchStickers(query))
		}

		fun searchClear(char: String) {
			searchText.text = ""
			updateSearchResults(searchStickers(""))
		}

		fun addKey(
			char: String,
			secondaryChar: String,
			tap: (String) -> Unit = ::searchAppend,
			longTap: (String) -> Unit = ::searchAppend,
		): RelativeLayout {
			val buttonView = layoutInflater.inflate(R.layout.qwerty_key, null, false)
			val button = buttonView.findViewById<RelativeLayout>(R.id.btn)
			val layoutParams =
				LinearLayout.LayoutParams(qwertyWidth, LinearLayout.LayoutParams.WRAP_CONTENT)
			button.layoutParams = layoutParams
			button.layoutParams.width = qwertyWidth
			button.tag = arrayOf(char.lowercase(), secondaryChar)

			val pText = buttonView.findViewById<TextView>(R.id.primaryText)
			pText.text = char
			val sText = buttonView.findViewById<TextView>(R.id.secondaryText)
			sText.text = secondaryChar
			button.setOnClickListener {
				if (this.vibrate && SDK_INT >= Build.VERSION_CODES.O_MR1) {
					it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS)
				}
				tap((it.tag as Array<String>)[0])
			}
			button.setOnLongClickListener {
				longTap((it.tag as Array<String>)[1])
				return@setOnLongClickListener true
			}
			return button
		}

		fun addRow(row: LinearLayout, chars: List<String>, secondaryChars: List<String>) {
			for ((index, key) in chars.withIndex()) {
				val button = addKey(key, secondaryChars[index])
				row.addView(button)
			}
		}

		val row1 = qwertyLayout.findViewById<LinearLayout>(R.id.qwerty_row_1)
		addRow(row1, "QWERTYUIOP".map { it.toString() }, "1234567890".map { it.toString() })
		val row2 = qwertyLayout.findViewById<LinearLayout>(R.id.qwerty_row_2)
		addRow(row2, "ASDFGHJKL".map { it.toString() }, "@#£_&-+()".map { it.toString() })
		val row3 = qwertyLayout.findViewById<LinearLayout>(R.id.qwerty_row_3)
		addRow(row3, "ZXCVBNM".map { it.toString() }, "*\"':;!?".map { it.toString() })
		val row4 = qwertyLayout.findViewById<LinearLayout>(R.id.qwerty_row_4)

		val backspace = addKey("←", "", ::searchBack, ::searchClear)
		backspace.layoutParams.width = qwertyWidth * 2
		row3.addView(backspace)

		val spacebar = addKey(" ", " ")
		spacebar.layoutParams.width = qwertyWidth * 7
		row4.addView(spacebar)

		// Show all stickers up front, before any query has been typed.
		updateSearchResults(searchStickers(""))
		return qwertyLayout
	}

	/**
	 * Adds a pack button to the packsList/ nav bar. Tapping a pack (or recent) icon jumps the
	 * board to that section.
	 *
	 * @param tag The pack name associated with the pack button.
	 * @return The ImageButton representing the added pack button.
	 */
	private fun addPackButton(tag: String): ImageButton {
		val packCard = layoutInflater.inflate(R.layout.sticker_card, this.packsList, false)
		val packButton = packCard.findViewById<ImageButton>(R.id.stickerButton)
		packButton.tag = tag
		packButton.setOnClickListener { jumpToSection(tag) }
		this.packsList.addView(packCard)
		return packButton
	}

	/**
	 * Create the pack icons (image buttons) that live in the top nav bar, then build the board and
	 * jump to whichever section was last active.
	 */
	private fun createPackIcons() {
		this.packsList.removeAllViewsInLayout()
		// Recent
		val recentButton = addPackButton(RECENT_PACK_NAME)
		recentButton.load(getDrawable(R.drawable.ic_recent))
		// Packs
		val sortedPackNames = sortedPackNames()

		for (sortedPackName in sortedPackNames) {
			val packButton = addPackButton(sortedPackName)
			packButton.load(this.loadedPacks[sortedPackName]?.thumbSticker)
		}

		buildBoard()

		// The Recent section always exists in the board now (even empty, as a placeholder), so
		// check for actual recent stickers rather than just section presence, to still land on
		// the first real pack on a fresh install with no sticker history.
		val fallbackTarget = if (this.recentCache.toFiles().isNotEmpty()) {
			RECENT_PACK_NAME
		} else {
			sortedPackNames.firstOrNull()
		}
		val targetSection =
			if (this.headerPositions.containsKey(activePack)) activePack else fallbackTarget
		targetSection?.let { jumpToSection(it) }
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

	/**
	 * onStickerClicked
	 *
	 * When a sticker is tapped/ clicked. Update the cache and send the sticker
	 *
	 *  @param sticker: File
	 */
	override fun onStickerClicked(sticker: File) {
		this.recentCache.add(sticker.path)
		refreshBoard()
		this.stickerSender.sendSticker(sticker)
	}

	/**
	 * onStickerLongClicked
	 *
	 * When a sticker is long tapped/ clicked. Take over packContent - the same content area the
	 * board/ search/ qwerty modes already share below the top bar - with an enlarged preview of
	 * the sticker, hiding the pack icon row so the preview gets the full content area to itself.
	 * The pull bar's close/ search buttons temporarily become back/ send for the duration.
	 *
	 *  @param sticker: File
	 */
	override fun onStickerLongClicked(sticker: File) {
		// packContent fills boardArea entirely once the tab bar is hidden (it's boardArea's only
		// weighted child), so its resulting height is exactly keyboardHeight - no manual patching
		// of packContent's own height needed.
		this.topHScrollView.visibility = View.GONE

		this.searchButton.isSelected = false
		this.closeButton.load(getDrawable(R.drawable.ic_back))
		this.closeButton.contentDescription = getString(R.string.close_sticker_preview)
		this.closeButton.visibility = View.VISIBLE
		this.closeButton.setOnClickListener { closeStickerPreview() }

		this.searchButton.load(getDrawable(R.drawable.ic_send))
		this.searchButton.contentDescription = getString(R.string.send_sticker)
		this.searchButton.visibility = View.VISIBLE
		this.searchButton.setOnClickListener { sendAndCloseStickerPreview(sticker) }

		val previewContent =
			layoutInflater.inflate(R.layout.sticker_preview, this.packContent, false)
		previewContent.findViewById<TextView>(R.id.stickerPreviewPackName).text =
			prettifyPackName(sticker.parent?.split('/')?.last() ?: "")
		previewContent.findViewById<TextView>(R.id.stickerPreviewStickerName).text =
			trimString(sticker.name)
		previewContent.findViewById<ImageButton>(R.id.stickerPreviewImage).apply {
			load(sticker)
			setOnClickListener { sendAndCloseStickerPreview(sticker) }
		}

		this.packContent.removeAllViewsInLayout()
		this.packContent.addView(previewContent)
	}

	/** Dismiss the sticker preview and restore whatever chrome/ content it replaced. */
	private fun closeStickerPreview() {
		setupTopBarButtons()
		if (this.activeSection == SEARCH_TAG) {
			searchView()
		} else {
			showBoard()
			updateActiveNavButton(this.activePack)
		}
	}

	private fun sendAndCloseStickerPreview(sticker: File) {
		closeStickerPreview()
		onStickerClicked(sticker)
	}

	internal fun switchToPreviousPack() {
		val sectionNames = this.headerPositions.keys.toList()
		if (sectionNames.isEmpty()) return
		val currentIndex = sectionNames.indexOf(this.activeSection).let { if (it == -1) 0 else it }
		val previousIndex = if (currentIndex > 0) currentIndex - 1 else sectionNames.size - 1
		jumpToSection(sectionNames[previousIndex])
	}

	internal fun switchToNextPack() {
		val sectionNames = this.headerPositions.keys.toList()
		if (sectionNames.isEmpty()) return
		val currentIndex = sectionNames.indexOf(this.activeSection).let { if (it == -1) 0 else it }
		val nextIndex = (currentIndex + 1) % sectionNames.size
		jumpToSection(sectionNames[nextIndex])
	}

	private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
		override fun onDown(e: MotionEvent): Boolean {
			return false
		}

		override fun onScroll(
			e1: MotionEvent?,
			e2: MotionEvent,
			velocityX: Float,
			velocityY: Float,
		): Boolean {
			// The board always scrolls vertically on its own, so a horizontal swipe is free to use
			// for jumping between sections.
			val diffX = e2.x - (e1?.x ?: 0f)

			if (scroll && abs(diffX) > SWIPE_THRESHOLD && abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
				if (diffX > 0) {
					// Swipe right
					switchToPreviousPack()
				} else {
					// Swipe left
					switchToNextPack()
				}
				return true
			}

			return false
		}
	}

	/**
	 * Pinch to zoom: spreading fingers apart shows fewer, bigger stickers per row; pinching them
	 * together shows more, smaller stickers per row.
	 */
	private inner class PinchZoomListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
		private var cumulativeScale = 1f

		override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
			cumulativeScale = 1f
			return true
		}

		override fun onScale(detector: ScaleGestureDetector): Boolean {
			cumulativeScale *= detector.scaleFactor
			if (cumulativeScale > PINCH_STEP_THRESHOLD) {
				updateIconsPerX(iconsPerX - 1)
				cumulativeScale = 1f
			} else if (cumulativeScale < 1f / PINCH_STEP_THRESHOLD) {
				updateIconsPerX(iconsPerX + 1)
				cumulativeScale = 1f
			}
			return true
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
