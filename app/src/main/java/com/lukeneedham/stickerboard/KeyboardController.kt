package com.lukeneedham.stickerboard

import android.content.Intent
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.inputmethodservice.InputMethodService.Insets
import android.os.Build.VERSION.SDK_INT
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
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
import com.lukeneedham.stickerboard.keyboard.DarkColors
import com.lukeneedham.stickerboard.keyboard.KeyboardDataSource
import com.lukeneedham.stickerboard.keyboard.KeyboardModel
import com.lukeneedham.stickerboard.keyboard.KeyboardView
import com.lukeneedham.stickerboard.keyboard.LightColors
import com.lukeneedham.stickerboard.keyboard.PackNavIcon
import com.lukeneedham.stickerboard.keyboard.RECENT_PACK_NAME
import com.lukeneedham.stickerboard.model.BoardItem
import com.lukeneedham.stickerboard.utilities.StickerSender
import com.lukeneedham.stickerboard.utilities.startLogger
import java.io.File

/** Default pixel height of the scrollable board viewport. */
private const val KEYBOARD_HEIGHT_PX = 800

/** Smallest height the board can be dragged down to via the pull bar. */
private const val MIN_KEYBOARD_HEIGHT_PX = 300

/** Largest height the board can be dragged up to, as a fraction of the screen height. */
private const val MAX_KEYBOARD_HEIGHT_FRACTION = 0.75f

/**
 * KeyboardController is the "Controller" in the keyboard's MVC split: it inherits from
 * InputMethodService, so it's the only piece that can mediate the platform's IME callbacks
 * (starting/finishing input, computing insets, switching input methods) and own the ComposeView.
 * The UI itself is Jetpack Compose ([KeyboardView], the "View" - pure presentation, driven only
 * by [KeyboardDataSource]); [KeyboardModel] is the "Model" - packs, caches and prefs, with no
 * dependency on this class.
 *
 * A ViewModel was considered instead of a Model class, and rejected: ViewModel's value is
 * surviving the destruction/recreation of its owning Activity/Fragment across configuration
 * changes. Here, *this* service instance already plays that role - it isn't destroyed and
 * recreated the way an Activity is; only [onCreateInputView]'s View is rebuilt, with the service
 * (and therefore [model]) persisting underneath it regardless. Introducing a real ViewModel would
 * mean manually implementing `ViewModelStoreOwner` and clearing its store by hand in [onDestroy]
 * anyway - the same boilerplate as owning a plain Model object, for no extra benefit - while
 * several [KeyboardDataSource] members ([onClose], [onOpenSettings], [onStickerSend]'s send step)
 * are inherently Controller-only, since they call InputMethodService/InputConnection APIs a
 * ViewModel can't hold. Splitting the data that remains into a second, ViewModel-shaped owner
 * would just create two owners of overlapping state for no reason.
 */
class KeyboardController :
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

	private lateinit var model: KeyboardModel

	// onStartInput
	private lateinit var stickerSender: StickerSender

	// onCreateInputView
	private var keyboardHeight = 0
	private var maxKeyboardHeightPx = 0

	private val _statusMessage = mutableStateOf<String?>(null)
	override val statusMessage: State<String?> get() = _statusMessage

	/**
	 * When the activity is created...
	 * - ensure coil can decode (and display) animated images
	 * - load [model] (which loads packs/caches/prefs itself)
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

		model = KeyboardModel(baseContext)
		// Set directly on the window rather than through Compose: this runs before
		// onCreateInputView() builds any Compose UI, so LocalAppTheme isn't available yet.
		val nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
		val bg = if (nightMode == Configuration.UI_MODE_NIGHT_YES) DarkColors.bg else LightColors.bg
		window.window?.navigationBarColor = bg.toArgb()
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
			model.savedKeyboardHeightPx(KEYBOARD_HEIGHT_PX)
				.coerceIn(MIN_KEYBOARD_HEIGHT_PX, this.maxKeyboardHeightPx)

		// Populate the model's section positions so the initial section below can be resolved.
		model.boardItems()
		// The Recent section always exists in the board now (even empty, as a placeholder), so
		// check for actual recent stickers rather than just section presence, to still land on
		// the first real pack on a fresh install with no sticker history.
		val fallbackTarget = if (model.hasRecentStickers()) RECENT_PACK_NAME else model.firstPackName()
		val initialSection =
			(if (model.sectionExists(model.activePack)) model.activePack else fallbackTarget)
				?: RECENT_PACK_NAME
		model.setActivePack(initialSection)

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
			setViewTreeLifecycleOwner(this@KeyboardController)
			setViewTreeSavedStateRegistryOwner(this@KeyboardController)
			setContent {
				KeyboardView(
					dataSource = this@KeyboardController,
					initialIconsPerX = model.iconsPerX,
					initialKeyboardHeightPx = keyboardHeight,
					minKeyboardHeightPx = MIN_KEYBOARD_HEIGHT_PX,
					maxKeyboardHeightPx = maxKeyboardHeightPx,
					initialActivePack = initialSection,
				)
			}
		}
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
			model.internalStickerDir,
			this.currentInputConnection,
			this.currentInputEditorInfo,
			model.compatCache,
			this.imageLoader,
			onCannotSend = { showStatusMessage(getString(R.string.cannot_send_sticker)) },
		)
	}

	private fun showStatusMessage(message: String) {
		_statusMessage.value = message
	}

	override fun onStatusMessageShown() {
		_statusMessage.value = null
	}

	/** When leaving some input field update the caches */
	override fun onFinishInput() {
		model.persistSessionState()
		super.onFinishInput()
	}

	override fun boardItems(): List<BoardItem> = model.boardItems()

	override fun packNavIcons(): List<PackNavIcon> = model.packNavIcons()

	override fun sectionIndex(packName: String): Int? = model.sectionIndex(packName)

	override fun sectionAt(itemIndex: Int): String? = model.sectionAt(itemIndex)

	override suspend fun refreshStickers() = model.refreshStickers()

	override fun searchStickers(query: String): List<File> = model.searchStickers(query)

	override fun changeIconsPerX(delta: Int): Int = model.changeIconsPerX(delta)

	override fun onKeyboardHeightChanged(heightPx: Int) {
		this.keyboardHeight = heightPx
	}

	override fun onKeyboardHeightSettled(heightPx: Int) {
		this.keyboardHeight = heightPx
		model.saveKeyboardHeight(heightPx)
	}

	override fun onActivePackChanged(packName: String) = model.setActivePack(packName)

	override fun onStickerSend(sticker: File) {
		model.recordStickerSent(sticker)
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
 * Split a name (a sticker filename or pack directory name) into individual words on hyphens,
 * underscores, and spaces - so e.g. "happy-cat_meme" becomes ["happy", "cat", "meme"]. Shared by
 * every call site that needs a name's constituent terms, for both search and display.
 */
fun splitNameIntoTerms(name: String): List<String> {
	return name.split('-', '_', ' ').filter { it.isNotEmpty() }
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
	return splitNameIntoTerms(name).joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
}
