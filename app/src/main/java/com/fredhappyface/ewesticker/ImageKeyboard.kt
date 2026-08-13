package com.fredhappyface.ewesticker

import android.content.Context
import android.content.SharedPreferences
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
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
import com.fredhappyface.ewesticker.adapter.StickerBoardAdapter
import com.fredhappyface.ewesticker.adapter.StickerPackAdapter
import com.fredhappyface.ewesticker.model.BoardItem
import com.fredhappyface.ewesticker.model.StickerPack
import com.fredhappyface.ewesticker.utilities.Cache
import com.fredhappyface.ewesticker.utilities.StickerClickListener
import com.fredhappyface.ewesticker.utilities.StickerSender
import com.fredhappyface.ewesticker.utilities.Toaster
import com.fredhappyface.ewesticker.utilities.startLogger
import java.io.File
import kotlin.math.abs
import kotlin.math.min

private const val SWIPE_THRESHOLD = 1
private const val SWIPE_VELOCITY_THRESHOLD = 1

/** Default pixel height of the scrollable board viewport. */
private const val KEYBOARD_HEIGHT_PX = 800

/** Smallest height the board can be dragged down to via the pull bar. */
private const val MIN_KEYBOARD_HEIGHT_PX = 300

/** Largest height the board can be dragged up to, as a fraction of the screen height. */
private const val MAX_KEYBOARD_HEIGHT_FRACTION = 0.75f

/** Synthetic pack name used for the "recently used" section/ nav icon. */
private const val RECENT_PACK_NAME = "__recentSticker__"

/** Synthetic tags for the close/ search nav icons (never appear as board sections). */
private const val CLOSE_TAG = "__close__"
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
	private lateinit var packsList: ViewGroup
	private lateinit var packContent: ViewGroup
	private lateinit var pullBar: View
	private var keyboardHeight = 0
	private var fullIconSize = 0
	private var qwertyWidth = 0

	private lateinit var gestureDetector: GestureDetector

	//  The unified, vertically-scrolling board holding every pack's stickers
	private lateinit var boardRecyclerView: RecyclerView

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

		this.iconsPerX = this.backupSharedPreferences.getInt("iconsPerX", 3)
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

		this.keyboardRoot = keyboardLayout.findViewById(R.id.keyboardRoot)
		this.packsList = keyboardLayout.findViewById(R.id.packsList)
		this.packContent = keyboardLayout.findViewById(R.id.packContent)
		this.pullBar = keyboardLayout.findViewById(R.id.pullBar)
		this.keyboardHeight =
			this.backupSharedPreferences.getInt("keyboardHeight", KEYBOARD_HEIGHT_PX)
		this.packContent.layoutParams?.height = this.keyboardHeight
		this.fullIconSize =
			(
				min(
					resources.displayMetrics.widthPixels,
					this.keyboardHeight -
						resources.getDimensionPixelOffset(R.dimen.text_size_body) * 2,
				) * 0.95
				)
				.toInt()
		setupPullBar()
		createPackIcons()
		return keyboardLayout
	}

	/**
	 * Wire up the drag handle at the top of the keyboard so the user can resize the board
	 * vertically by dragging it up or down. The chosen height is persisted so it's remembered
	 * next time the keyboard is shown.
	 */
	private fun setupPullBar() {
		var startRawY = 0f
		var startHeight = 0
		val minHeight = MIN_KEYBOARD_HEIGHT_PX
		val maxHeight =
			(resources.displayMetrics.heightPixels * MAX_KEYBOARD_HEIGHT_FRACTION).toInt()

		this.pullBar.setOnTouchListener { _, event ->
			when (event.actionMasked) {
				MotionEvent.ACTION_DOWN -> {
					startRawY = event.rawY
					startHeight = this.keyboardHeight
					true
				}

				MotionEvent.ACTION_MOVE -> {
					val delta = (startRawY - event.rawY).toInt()
					val newHeight = (startHeight + delta).coerceIn(minHeight, maxHeight)
					this.keyboardHeight = newHeight
					this.packContent.layoutParams?.height = newHeight
					this.packContent.requestLayout()
					true
				}

				MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
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
	 * Disable full-screen mode as content will likely be hidden by the IME.
	 *
	 * @return Boolean false
	 */
	override fun onEvaluateFullscreenMode(): Boolean {
		return false
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
	 * Build the unified, vertically-scrolling board: every pack's stickers concatenated, each
	 * preceded by a full-width section header. Populates [headerPositions] so nav icons can jump
	 * straight to a section, and installs a scroll listener that keeps the nav icons in sync with
	 * whichever section is currently on screen.
	 */
	private fun buildBoard() {
		XLog.i("Building sticker board")
		val items = mutableListOf<BoardItem>()
		this.headerPositions = LinkedHashMap()

		val recentStickers = this.recentCache.toFiles().reversedArray()
		if (recentStickers.isNotEmpty()) {
			this.headerPositions[RECENT_PACK_NAME] = items.size
			items.add(BoardItem.Header(RECENT_PACK_NAME, getString(R.string.recent_heading)))
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

		val layoutManager = GridLayoutManager(this, iconsPerX, RecyclerView.VERTICAL, false)
		layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
			override fun getSpanSize(position: Int): Int =
				if (items[position] is BoardItem.Header) iconsPerX else 1
		}

		val recyclerView = RecyclerView(this)
		recyclerView.layoutManager = layoutManager
		recyclerView.adapter =
			StickerBoardAdapter(iconSize, items, this, gestureDetector, vibrate)
		recyclerView.addOnScrollListener(
			object : RecyclerView.OnScrollListener() {
				override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
					val firstVisible = layoutManager.findFirstVisibleItemPosition()
					if (firstVisible == RecyclerView.NO_POSITION) return
					sectionAt(firstVisible)?.let { updateActiveNavButton(it) }
				}
			},
		)

		this.boardRecyclerView = recyclerView
		showBoard()
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
		if (packContent.childCount == 1 && packContent.getChildAt(0) === this.boardRecyclerView) {
			return
		}
		packContent.removeAllViewsInLayout()
		packContent.addView(this.boardRecyclerView)
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
		for (packCard in this.packsList) {
			val packButton = packCard.findViewById<ImageButton>(R.id.stickerButton)
			packButton.isSelected = packButton.tag == packName
		}
	}

	/**
	 * Set the current tab to the search page/ view
	 */
	private fun searchView() {
		XLog.i("Switching to search")
		for (packCard in this.packsList) {
			val packButton = packCard.findViewById<ImageButton>(R.id.stickerButton)
			packButton.isSelected = packButton.tag == SEARCH_TAG
		}
		this.activeSection = SEARCH_TAG

		qwertyWidth = (resources.displayMetrics.widthPixels / 10.4).toInt()

		val qwertyLayout = layoutInflater.inflate(R.layout.qwerty_layout, packContent, false)
		val searchText = qwertyLayout.findViewById<TextView>(R.id.search_text)
		val searchResults = qwertyLayout.findViewById<LinearLayout>(R.id.search_results)

		val searchResultsHeight =
			packContent.layoutParams.height -
				(
					resources.getDimension(R.dimen.qwerty_row_height) +
						resources.getDimension(R.dimen.qwerty_row_height) * 4
					)

		searchResults.layoutParams.height = searchResultsHeight.toInt()

		fun searchStickers(query: String): List<File> {
			return this.allStickers.filter { it.name.contains(query, ignoreCase = true) }
		}

		fun updateSearchResults(stickers: List<File>) {
			val recyclerView = RecyclerView(baseContext)
			val adapter = StickerPackAdapter(
				(searchResultsHeight * 0.9).toInt(),
				stickers.take(128).toTypedArray(),
				this,
				gestureDetector,
				this.vibrate,
			)
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
			searchResults.removeAllViews()
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

		packContent.removeAllViewsInLayout()
		packContent.addView(qwertyLayout)
	}

	/**
	 * Adds a pack button to the packsList/ nav bar. Tapping a pack (or recent) icon jumps the
	 * board to that section; the close/ search icons override this with their own behaviour.
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
		// Close button
		if (this.backupSharedPreferences.getBoolean("showBackButton", true)) {
			val closeButton = addPackButton(CLOSE_TAG)
			closeButton.load(getDrawable(R.drawable.ic_close))
			closeButton.setOnClickListener {
				closeKeyboard()
			}
		}

		// Search
		if (this.backupSharedPreferences.getBoolean("showSearchButton", true)) {
			val searchButton = addPackButton(SEARCH_TAG)
			searchButton.load(getDrawable(R.drawable.ic_search))
			searchButton.setOnClickListener {
				searchView()
			}
		}
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

		val fallbackTarget = if (this.headerPositions.containsKey(RECENT_PACK_NAME)) {
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
		this.stickerSender.sendSticker(sticker)
	}

	/**
	 * onStickerLongClicked
	 *
	 * When a sticker is long tapped/ clicked. Attach a new view to see an enlarged version of the sticker
	 *
	 *  @param sticker: File
	 */
	override fun onStickerLongClicked(sticker: File) {
		val fullStickerLayout =
			layoutInflater.inflate(R.layout.sticker_preview, this.keyboardRoot, false) as
				RelativeLayout
		// Set dimens + load image
		fullStickerLayout.layoutParams.height =
			this.keyboardHeight +
				(
					resources.getDimension(R.dimen.pack_dimens) +
						resources.getDimension(R.dimen.sticker_padding) * 4
					).toInt()
		val fSticker = fullStickerLayout.findViewById<ImageButton>(R.id.stickerButton)
		fSticker.layoutParams.height = this.fullIconSize
		fSticker.layoutParams.width = this.fullIconSize
		fSticker.load(sticker)
		val fText = fullStickerLayout.findViewById<TextView>(R.id.stickerInfo)
		val stickerName = trimString(sticker.name)
		val packName = trimString(sticker.parent?.split('/')?.last())
		fText.text = getString(R.string.sticker_pack_info, stickerName, packName)

		// Tap to exit popup
		fullStickerLayout.setOnClickListener { this.keyboardRoot.removeView(it) }
		fSticker.setOnClickListener { this.keyboardRoot.removeView(fullStickerLayout) }
		this.keyboardRoot.addView(fullStickerLayout)
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
