@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.lukeneedham.stickerboard.keyboard

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lukeneedham.stickerboard.R
import com.lukeneedham.stickerboard.model.BoardItem
import com.lukeneedham.stickerboard.prettifyPackName
import com.lukeneedham.stickerboard.trimString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.system.measureTimeMillis

/** Cumulative pinch scale factor needed to change iconsPerX by one column. */
private const val PINCH_STEP_THRESHOLD = 1.15f

/**
 * Minimum time to hold the pull-refresh indicator's `isRefreshing = true` state. The sticker
 * re-scan can finish within a single frame, and PullToRefreshBox's animation only reacts when it
 * observes isRefreshing actually change between recompositions - without this floor, a fast
 * enough refresh can flip true then false before that happens, so the indicator never sees a
 * transition to animate away and is left stuck wherever the pull gesture released it.
 */
private const val MIN_REFRESH_INDICATOR_MS = 500L

/** How long the [StatusBanner] (e.g. "Cannot send image") stays on screen before auto-dismissing. */
private const val STATUS_MESSAGE_DURATION_MS = 2500L

/** Which content is currently showing below the pull bar. */
private sealed interface Mode {
	data object Board : Mode
	data object Search : Mode
	data class Preview(val sticker: File, val returnTo: Mode) : Mode
}

/**
 * The whole keyboard UI: pull bar, pack nav row, and the board/search/preview content beneath it.
 * Pinned to [maxKeyboardHeightPx] so the IME window is never resized while the pull bar is
 * dragged - only the (bottom-anchored) content within it grows/shrinks, tracked by
 * [KeyboardDataSource.onKeyboardHeightChanged]/[KeyboardDataSource.onComputeInsets]-equivalent
 * bookkeeping on the caller's side.
 */
@Composable
fun KeyboardScreen(
	dataSource: KeyboardDataSource,
	initialIconsPerX: Int,
	initialKeyboardHeightPx: Int,
	minKeyboardHeightPx: Int,
	maxKeyboardHeightPx: Int,
	initialActivePack: String,
	showCloseButton: Boolean,
	showSearchButton: Boolean,
	vibrate: Boolean,
	swipeEnabled: Boolean,
) {
	var iconsPerX by remember { mutableIntStateOf(initialIconsPerX) }
	var keyboardHeightPx by remember { mutableIntStateOf(initialKeyboardHeightPx) }
	var mode by remember { mutableStateOf<Mode>(Mode.Board) }
	var activeSection by remember { mutableStateOf(initialActivePack) }
	var boardItems by remember { mutableStateOf(dataSource.boardItems()) }
	var packNavIcons by remember { mutableStateOf(dataSource.packNavIcons()) }
	var searchQuery by remember { mutableStateOf("") }
	var searchResults by remember { mutableStateOf(dataSource.searchStickers("")) }
	var isRefreshingStickers by remember { mutableStateOf(false) }
	val gridState = rememberLazyGridState()
	val scope = rememberCoroutineScope()

	fun refreshBoard() {
		boardItems = dataSource.boardItems()
		packNavIcons = dataSource.packNavIcons()
	}

	fun sendSticker(file: File) {
		dataSource.onStickerSend(file)
		refreshBoard()
	}

	// Packs/stickers can be added on disk (e.g. via the app's gallery) while the keyboard is open,
	// so pulling down re-scans them in rather than requiring the keyboard to be fully reloaded.
	fun refreshStickers() {
		if (isRefreshingStickers) return
		isRefreshingStickers = true
		scope.launch {
			try {
				val elapsedMs = measureTimeMillis {
					withContext(Dispatchers.IO) { dataSource.refreshStickers() }
					refreshBoard()
				}
				delay((MIN_REFRESH_INDICATOR_MS - elapsedMs).coerceAtLeast(0))
			} finally {
				isRefreshingStickers = false
			}
		}
	}

	fun jumpToSection(packName: String) {
		activeSection = packName
		dataSource.onActivePackChanged(packName)
		mode = Mode.Board
		val index = dataSource.sectionIndex(packName)
		if (index != null) {
			scope.launch { gridState.scrollToItem(index) }
		}
	}

	LaunchedEffect(gridState) {
		snapshotFlow { gridState.firstVisibleItemIndex }.collect { index ->
			if (mode is Mode.Board) {
				dataSource.sectionAt(index)?.let { activeSection = it }
			}
		}
	}

	LaunchedEffect(Unit) {
		dataSource.sectionIndex(initialActivePack)?.let { gridState.scrollToItem(it) }
	}

	val density = LocalDensity.current
	Box(
		Modifier
			.fillMaxWidth()
			.height(with(density) { maxKeyboardHeightPx.toDp() }),
	) {
		Column(
			Modifier
				.align(Alignment.BottomStart)
				.fillMaxWidth()
				.height(with(density) { keyboardHeightPx.toDp() })
				.background(colorResource(R.color.bg)),
		) {
			PullBar(
				mode = mode,
				showCloseButton = showCloseButton,
				showSearchButton = showSearchButton,
				onOpenSettings = { dataSource.onOpenSettings() },
				onHeightDrag = { dragAmountPx ->
					val newHeight = (keyboardHeightPx - dragAmountPx)
						.roundToInt()
						.coerceIn(minKeyboardHeightPx, maxKeyboardHeightPx)
					keyboardHeightPx = newHeight
					dataSource.onKeyboardHeightChanged(newHeight)
				},
				onHeightDragEnd = { dataSource.onKeyboardHeightSettled(keyboardHeightPx) },
				onBackOrClose = {
					val current = mode
					if (current is Mode.Preview) {
						mode = current.returnTo
					} else {
						dataSource.onClose()
					}
				},
				onSearchOrSend = {
					when (val current = mode) {
						is Mode.Preview -> {
							sendSticker(current.sticker)
							mode = current.returnTo
						}
						Mode.Search -> jumpToSection(activeSection)
						Mode.Board -> mode = Mode.Search
					}
				},
			)
			Column(Modifier.weight(1f).fillMaxWidth()) {
				if (mode is Mode.Board) {
					PackNavRow(
						packs = packNavIcons,
						activeSection = activeSection,
						onPackClick = { jumpToSection(it) },
					)
				}
				Box(Modifier.weight(1f).fillMaxWidth()) {
					when (val current = mode) {
						Mode.Board -> BoardGrid(
							items = boardItems,
							columns = iconsPerX,
							gridState = gridState,
							keyboardHeightPx = keyboardHeightPx,
							swipeEnabled = swipeEnabled,
							vibrate = vibrate,
							isRefreshing = isRefreshingStickers,
							onStickerClick = { sendSticker(it) },
							onStickerLongClick = { mode = Mode.Preview(it, Mode.Board) },
							onZoomStep = { delta ->
								iconsPerX = dataSource.changeIconsPerX(delta)
								refreshBoard()
							},
							onSwipePrevious = {
								dataSource.previousSection(activeSection)?.let { jumpToSection(it) }
							},
							onSwipeNext = {
								dataSource.nextSection(activeSection)?.let { jumpToSection(it) }
							},
							onRefresh = { refreshStickers() },
						)
						Mode.Search -> SearchContent(
							query = searchQuery,
							onQueryChange = { query ->
								searchQuery = query
								searchResults = dataSource.searchStickers(query)
							},
							results = searchResults,
							onStickerClick = { sendSticker(it) },
							onStickerLongClick = { mode = Mode.Preview(it, Mode.Search) },
							vibrate = vibrate,
						)
						is Mode.Preview -> PreviewContent(
							sticker = current.sticker,
							onSend = {
								sendSticker(current.sticker)
								mode = current.returnTo
							},
						)
					}
				}
			}
		}

		val statusMessage by dataSource.statusMessage
		if (statusMessage != null) {
			LaunchedEffect(statusMessage) {
				delay(STATUS_MESSAGE_DURATION_MS)
				dataSource.onStatusMessageShown()
			}
			StatusBanner(
				message = statusMessage,
				modifier = Modifier
					.align(Alignment.BottomCenter)
					.padding(bottom = dimensionResource(R.dimen.content_margin)),
			)
		}
	}
}

/**
 * A snackbar-style banner shown briefly over the keyboard (e.g. "Cannot send image") in place of a
 * system dialog, which would otherwise close the keyboard to show itself.
 */
@Composable
private fun StatusBanner(message: String, modifier: Modifier = Modifier) {
	Box(
		modifier
			.clip(RoundedCornerShape(dimensionResource(R.dimen.corner)))
			.background(colorResource(R.color.accent))
			.padding(
				horizontal = dimensionResource(R.dimen.card_margin),
				vertical = dimensionResource(R.dimen.content_margin),
			),
	) {
		BasicText(
			text = message,
			style = TextStyle(color = colorResource(R.color.onAccent), fontSize = 16.sp),
		)
	}
}

@Composable
private fun PullBar(
	mode: Mode,
	showCloseButton: Boolean,
	showSearchButton: Boolean,
	onOpenSettings: () -> Unit,
	onHeightDrag: (Float) -> Unit,
	onHeightDragEnd: () -> Unit,
	onBackOrClose: () -> Unit,
	onSearchOrSend: () -> Unit,
) {
	val isPreview = mode is Mode.Preview
	Box(
		Modifier
			.fillMaxWidth()
			.height(dimensionResource(R.dimen.pull_bar_height))
			.pointerInput(Unit) {
				detectVerticalDragGestures(onDragEnd = onHeightDragEnd) { change, dragAmount ->
					change.consume()
					onHeightDrag(dragAmount)
				}
			},
	) {
		if (isPreview || showCloseButton) {
			CircleIconButton(
				iconRes = if (isPreview) R.drawable.ic_back else R.drawable.ic_close,
				contentDescription = if (isPreview) {
					stringResource(R.string.close_sticker_preview)
				} else {
					stringResource(R.string.pack_icon)
				},
				selected = false,
				onClick = onBackOrClose,
				modifier = Modifier
					.align(Alignment.CenterStart)
					.padding(start = dimensionResource(R.dimen.sticker_padding)),
			)
		}
		Box(
			Modifier
				.align(Alignment.Center)
				.width(36.dp)
				.height(4.dp)
				.background(colorResource(R.color.pull_handle), RoundedCornerShape(2.dp)),
		)
		Row(
			Modifier
				.align(Alignment.CenterEnd)
				.padding(end = dimensionResource(R.dimen.sticker_padding)),
		) {
			if (!isPreview) {
				CircleIconButton(
					iconRes = R.drawable.ic_settings,
					contentDescription = stringResource(R.string.open_settings_button),
					selected = false,
					onClick = onOpenSettings,
				)
			}
			if (isPreview || showSearchButton) {
				CircleIconButton(
					iconRes = if (isPreview) R.drawable.ic_send else R.drawable.ic_search,
					contentDescription = if (isPreview) {
						stringResource(R.string.send_sticker)
					} else {
						stringResource(R.string.pack_icon)
					},
					selected = mode is Mode.Search,
					onClick = onSearchOrSend,
				)
			}
		}
	}
}

@Composable
private fun CircleIconButton(
	iconRes: Int,
	contentDescription: String,
	selected: Boolean,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	Box(
		modifier
			.size(dimensionResource(R.dimen.pack_dimens))
			.clip(CircleShape)
			.background(if (selected) Color.White else Color.Transparent)
			.clickable(onClick = onClick)
			.padding(dimensionResource(R.dimen.nav_button_icon_padding)),
		contentAlignment = Alignment.Center,
	) {
		Image(
			painter = painterResource(iconRes),
			contentDescription = contentDescription,
			modifier = Modifier.fillMaxSize(),
		)
	}
}

@Composable
private fun PackNavRow(
	packs: List<PackNavIcon>,
	activeSection: String,
	onPackClick: (String) -> Unit,
) {
	Row(
		Modifier
			.fillMaxWidth()
			.horizontalScroll(rememberScrollState())
			.padding(dimensionResource(R.dimen.sticker_padding)),
	) {
		for (pack in packs) {
			NavIconButton(
				thumbnail = pack.thumbnail,
				selected = pack.packName == activeSection,
				onClick = { onPackClick(pack.packName) },
				modifier = Modifier.padding(dimensionResource(R.dimen.sticker_padding)),
			)
		}
	}
}

@Composable
private fun NavIconButton(
	thumbnail: File?,
	selected: Boolean,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	Box(
		modifier
			.size(dimensionResource(R.dimen.pack_dimens))
			.clip(RoundedCornerShape(dimensionResource(R.dimen.nav_icon_corner)))
			.background(if (selected) Color.White else Color.Transparent)
			.clickable(onClick = onClick)
			.padding(dimensionResource(R.dimen.nav_icon_padding)),
		contentAlignment = Alignment.Center,
	) {
		if (thumbnail != null) {
			AsyncImage(
				model = thumbnail,
				contentDescription = stringResource(R.string.pack_icon),
				contentScale = ContentScale.Fit,
				modifier = Modifier.fillMaxSize(),
			)
		} else {
			Image(
				painter = painterResource(R.drawable.ic_recent),
				contentDescription = stringResource(R.string.pack_icon),
				modifier = Modifier.fillMaxSize(),
			)
		}
	}
}

@Composable
private fun BoardGrid(
	items: List<BoardItem>,
	columns: Int,
	gridState: LazyGridState,
	keyboardHeightPx: Int,
	swipeEnabled: Boolean,
	vibrate: Boolean,
	isRefreshing: Boolean,
	onStickerClick: (File) -> Unit,
	onStickerLongClick: (File) -> Unit,
	onZoomStep: (Int) -> Unit,
	onSwipePrevious: () -> Unit,
	onSwipeNext: () -> Unit,
	onRefresh: () -> Unit,
) {
	val density = LocalDensity.current
	val touchSlop = LocalViewConfiguration.current.touchSlop
	PullToRefreshBox(
		isRefreshing = isRefreshing,
		onRefresh = onRefresh,
		modifier = Modifier.fillMaxSize(),
	) {
		LazyVerticalGrid(
			columns = GridCells.Fixed(columns),
			state = gridState,
			// A final section with too few stickers to fill the viewport otherwise could never be
			// scrolled all the way to the top, so it'd be stuck partway down the screen unable to
			// become the active section - this padding gives it room to scroll into.
			contentPadding = PaddingValues(bottom = with(density) { keyboardHeightPx.toDp() }),
			modifier = Modifier
				.fillMaxSize()
				.boardGestures(swipeEnabled, touchSlop, onZoomStep, onSwipePrevious, onSwipeNext),
		) {
			items(
				count = items.size,
				key = { index ->
					when (val item = items[index]) {
						is BoardItem.Header -> "header:${item.packName}"
						is BoardItem.EmptyMessage -> "empty:${item.packName}"
						is BoardItem.Sticker -> "sticker:${item.packName}:${item.file.path}"
						is BoardItem.AddPhoto -> "add:${item.packName}"
					}
				},
				span = { index ->
					when (items[index]) {
						is BoardItem.Header, is BoardItem.EmptyMessage -> GridItemSpan(maxLineSpan)
						else -> GridItemSpan(1)
					}
				},
			) { index ->
				when (val item = items[index]) {
					is BoardItem.Header -> SectionHeader(item.displayName)
					is BoardItem.EmptyMessage -> SectionEmptyMessage(item.message)
					is BoardItem.Sticker -> StickerCell(
						file = item.file,
						contentDescription = stringResource(R.string.pack_icon),
						vibrate = vibrate,
						onClick = { onStickerClick(item.file) },
						onLongClick = { onStickerLongClick(item.file) },
					)
					// Board mode (unlike the sticker gallery) never surfaces AddPhoto cells.
					is BoardItem.AddPhoto -> Unit
				}
			}
		}
	}
}

@Composable
private fun SectionHeader(text: String) {
	BasicText(
		text = text,
		style = TextStyle(
			color = colorResource(R.color.fg),
			fontSize = 16.sp, // mirrors @dimen/text_size_body - dimensionResource() can't yield sp
			fontWeight = FontWeight.Bold,
		),
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = dimensionResource(R.dimen.content_margin))
			.padding(
				top = dimensionResource(R.dimen.section_header_top_margin),
				bottom = dimensionResource(R.dimen.content_margin_bottom),
			),
	)
}

@Composable
private fun SectionEmptyMessage(text: String) {
	BasicText(
		text = text,
		style = TextStyle(color = colorResource(R.color.fg), fontSize = 16.sp),
		modifier = Modifier
			.fillMaxWidth()
			.alpha(0.6f)
			.padding(horizontal = dimensionResource(R.dimen.content_margin))
			.padding(bottom = dimensionResource(R.dimen.content_margin)),
	)
}

@Composable
private fun StickerCell(
	file: File,
	contentDescription: String,
	vibrate: Boolean,
	onClick: () -> Unit,
	onLongClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val haptic = LocalHapticFeedback.current
	Box(
		modifier
			.padding(dimensionResource(R.dimen.sticker_padding))
			.aspectRatio(1f)
			.combinedClickable(
				onClick = {
					if (vibrate) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
					onClick()
				},
				onLongClick = onLongClick,
			),
	) {
		AsyncImage(
			model = file,
			contentDescription = contentDescription,
			contentScale = ContentScale.Fit,
			modifier = Modifier.fillMaxSize(),
		)
	}
}

@Composable
private fun SearchContent(
	query: String,
	onQueryChange: (String) -> Unit,
	results: List<File>,
	onStickerClick: (File) -> Unit,
	onStickerLongClick: (File) -> Unit,
	vibrate: Boolean,
) {
	Column(Modifier.fillMaxSize()) {
		LazyRow(Modifier.weight(1f).fillMaxWidth()) {
			items(results.size, key = { results[it].path }) { index ->
				val file = results[index]
				StickerCell(
					file = file,
					contentDescription = stringResource(R.string.pack_icon),
					vibrate = vibrate,
					onClick = { onStickerClick(file) },
					onLongClick = { onStickerLongClick(file) },
					modifier = Modifier.fillMaxHeight(),
				)
			}
		}
		BasicText(
			text = query,
			style = TextStyle(color = colorResource(R.color.fg), fontSize = 16.sp),
			modifier = Modifier
				.fillMaxWidth()
				.height(dimensionResource(R.dimen.qwerty_row_height))
				.wrapContentHeight(Alignment.CenterVertically)
				.padding(horizontal = dimensionResource(R.dimen.card_margin)),
		)
		BoxWithConstraints(Modifier.fillMaxWidth()) {
			QwertyKeyboard(
				keyWidth = maxWidth / 10.4f,
				onKeyTap = { onQueryChange(query + it) },
				onBackspace = {
					if (query.isNotEmpty()) onQueryChange(query.substring(0, query.length - 1))
				},
				onClear = { onQueryChange("") },
			)
		}
	}
}

@Composable
private fun QwertyKeyboard(
	keyWidth: Dp,
	onKeyTap: (String) -> Unit,
	onBackspace: () -> Unit,
	onClear: () -> Unit,
) {
	Column(Modifier.fillMaxWidth()) {
		Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
			QwertyRowKeys(keyWidth, "QWERTYUIOP", "1234567890", onKeyTap)
		}
		Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
			QwertyRowKeys(keyWidth, "ASDFGHJKL", "@#£_&-+()", onKeyTap)
		}
		Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
			QwertyRowKeys(keyWidth, "ZXCVBNM", "*\"':;!?", onKeyTap)
			QwertyKey(
				primary = "←",
				secondary = "",
				width = keyWidth * 2,
				onTap = onBackspace,
				onLongTap = onClear,
			)
		}
		Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
			QwertyKey(
				primary = " ",
				secondary = " ",
				width = keyWidth * 7,
				onTap = { onKeyTap(" ") },
				onLongTap = { onKeyTap(" ") },
			)
		}
	}
}

@Composable
private fun QwertyRowKeys(
	keyWidth: Dp,
	primaryChars: String,
	secondaryChars: String,
	onKeyTap: (String) -> Unit,
) {
	for (i in primaryChars.indices) {
		val primary = primaryChars[i].toString()
		val secondary = secondaryChars.getOrNull(i)?.toString().orEmpty()
		QwertyKey(
			primary = primary,
			secondary = secondary,
			width = keyWidth,
			onTap = { onKeyTap(primary.lowercase()) },
			onLongTap = { if (secondary.isNotEmpty()) onKeyTap(secondary) },
		)
	}
}

@Composable
private fun QwertyKey(
	primary: String,
	secondary: String,
	width: Dp,
	onTap: () -> Unit,
	onLongTap: () -> Unit,
) {
	Box(
		Modifier
			.padding(dimensionResource(R.dimen.sticker_padding))
			.width(width)
			.height(dimensionResource(R.dimen.qwerty_row_height))
			.clip(RoundedCornerShape(dimensionResource(R.dimen.corner)))
			.background(colorResource(R.color.bg2))
			.combinedClickable(onClick = onTap, onLongClick = onLongTap),
	) {
		BasicText(
			text = primary,
			style = TextStyle(color = colorResource(R.color.fg), fontSize = 16.sp),
			modifier = Modifier.align(Alignment.Center),
		)
		if (secondary.isNotEmpty()) {
			BasicText(
				text = secondary,
				style = TextStyle(color = colorResource(R.color.fg), fontSize = 10.sp),
				modifier = Modifier.align(Alignment.TopEnd),
			)
		}
	}
}

@Composable
private fun PreviewContent(sticker: File, onSend: () -> Unit) {
	Column(
		Modifier
			.fillMaxSize()
			.padding(horizontal = dimensionResource(R.dimen.content_margin))
			.padding(bottom = dimensionResource(R.dimen.content_margin)),
	) {
		Column(
			Modifier
				.align(Alignment.CenterHorizontally)
				.padding(top = dimensionResource(R.dimen.content_margin_top)),
			horizontalAlignment = Alignment.CenterHorizontally,
		) {
			BasicText(
				text = prettifyPackName(sticker.parent?.split('/')?.last() ?: ""),
				style = TextStyle(
					color = colorResource(R.color.accent),
					fontWeight = FontWeight.Bold,
					fontSize = 20.sp, // mirrors @dimen/text_size_subheading
				),
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
			BasicText(
				text = trimString(sticker.name),
				style = TextStyle(color = colorResource(R.color.fg), fontSize = 10.sp), // text_size_tiny
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
				modifier = Modifier.alpha(0.6f),
			)
		}
		Box(
			Modifier
				.weight(1f)
				.fillMaxWidth()
				.padding(top = dimensionResource(R.dimen.content_margin_top))
				.clip(RoundedCornerShape(dimensionResource(R.dimen.corner)))
				.clickable(onClick = onSend),
		) {
			AsyncImage(
				model = sticker,
				contentDescription = stringResource(R.string.send_sticker),
				contentScale = ContentScale.Fit,
				modifier = Modifier
					.fillMaxSize()
					.padding(dimensionResource(R.dimen.card_margin)),
			)
		}
	}
}

/**
 * Pinch to zoom (spread = fewer, bigger stickers per row; pinch = more, smaller) and swipe to
 * switch section - both live at the [BoardGrid] level so they can intercept multi/single-pointer
 * gestures before the grid's own vertical-scroll handling sees them, without stealing plain
 * single-finger vertical scrolling or taps.
 */
private fun Modifier.boardGestures(
	swipeEnabled: Boolean,
	touchSlopPx: Float,
	onZoomStep: (Int) -> Unit,
	onSwipePrevious: () -> Unit,
	onSwipeNext: () -> Unit,
): Modifier = pointerInput(swipeEnabled) {
	awaitEachGesture {
		var cumulativeZoom = 1f
		var prevPinchDistance = 0f
		var panX = 0f
		var swiped = false
		do {
			val event = awaitPointerEvent(PointerEventPass.Initial)
			val pressed = event.changes.filter { it.pressed }
			when {
				pressed.size >= 2 -> {
					val a = pressed[0].position
					val b = pressed[1].position
					val distance = hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()).toFloat()
					if (prevPinchDistance > 0f) {
						cumulativeZoom *= distance / prevPinchDistance
						if (cumulativeZoom > PINCH_STEP_THRESHOLD) {
							onZoomStep(-1)
							cumulativeZoom = 1f
						} else if (cumulativeZoom < 1f / PINCH_STEP_THRESHOLD) {
							onZoomStep(1)
							cumulativeZoom = 1f
						}
					}
					prevPinchDistance = distance
					event.changes.forEach { it.consume() }
				}
				swipeEnabled && pressed.size == 1 && !swiped -> {
					prevPinchDistance = 0f
					val change = pressed[0]
					panX += change.positionChange().x
					if (abs(panX) > touchSlopPx) {
						swiped = true
						if (panX > 0) onSwipePrevious() else onSwipeNext()
						change.consume()
					}
				}
				else -> prevPinchDistance = 0f
			}
		} while (event.changes.any { it.pressed })
	}
}
