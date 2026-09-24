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
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lukeneedham.stickerboard.R
import com.lukeneedham.stickerboard.model.BoardItem
import com.lukeneedham.stickerboard.prettifyPackName
import com.lukeneedham.stickerboard.trimString
import com.lukeneedham.stickerboard.utilities.StickerImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.system.measureTimeMillis

/** Cumulative pinch scale factor needed to change iconsPerX by one column. */
private const val PINCH_STEP_THRESHOLD = 1.15f

/** The search keyboard's widest row - determines the per-key width all other rows share. */
private const val QWERTY_TOP_ROW = "qwertyuiop"

/** How long the search bar's text-cursor stays visible, then invisible, each half of its blink cycle. */
private const val CURSOR_BLINK_HALF_PERIOD_MS = 500L

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
fun KeyboardView(
	dataSource: KeyboardDataSource,
	initialIconsPerX: Int,
	initialKeyboardHeightPx: Int,
	minKeyboardHeightPx: Int,
	maxKeyboardHeightPx: Int,
	initialActivePack: String,
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
	AppTheme {
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
					.background(LocalAppTheme.current.bg),
			) {
				PullBar(
					mode = mode,
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
						when (val current = mode) {
							is Mode.Preview -> mode = current.returnTo
							Mode.Search -> mode = Mode.Board
							Mode.Board -> dataSource.onClose()
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
								isRefreshing = isRefreshingStickers,
								onStickerClick = { sendSticker(it) },
								onStickerLongClick = { mode = Mode.Preview(it, Mode.Board) },
								onZoomStep = { delta ->
									iconsPerX = dataSource.changeIconsPerX(delta)
									refreshBoard()
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

			val statusMessage: String? = dataSource.statusMessage.value
			if (statusMessage != null) {
				LaunchedEffect(statusMessage) {
					delay(STATUS_MESSAGE_DURATION_MS)
					dataSource.onStatusMessageShown()
				}
				StatusBanner(
					message = statusMessage,
					modifier = Modifier
						.align(Alignment.BottomCenter)
						.padding(bottom = 10.dp),
				)
			}
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
			.clip(RoundedCornerShape(16.dp))
			.background(LocalAppTheme.current.accent)
			.padding(
				horizontal = 16.dp,
				vertical = 10.dp,
			),
	) {
		BasicText(
			text = message,
			style = TextStyle(color = LocalAppTheme.current.onAccent, fontSize = 16.sp),
		)
	}
}

@Composable
private fun PullBar(
	mode: Mode,
	onOpenSettings: () -> Unit,
	onHeightDrag: (Float) -> Unit,
	onHeightDragEnd: () -> Unit,
	onBackOrClose: () -> Unit,
	onSearchOrSend: () -> Unit,
) {
	val isPreview = mode is Mode.Preview
	val isSearch = mode is Mode.Search
	Box(
		Modifier
			.fillMaxWidth()
			.height(44.dp)
			.pointerInput(Unit) {
				detectVerticalDragGestures(onDragEnd = onHeightDragEnd) { change, dragAmount ->
					change.consume()
					onHeightDrag(dragAmount)
				}
			},
	) {
		CircleIconButton(
			iconRes = if (isPreview || isSearch) R.drawable.ic_back else R.drawable.ic_close,
			contentDescription = when {
				isPreview -> stringResource(R.string.close_sticker_preview)
				isSearch -> stringResource(R.string.close_search)
				else -> stringResource(R.string.pack_icon)
			},
			selected = false,
			onClick = onBackOrClose,
			modifier = Modifier
				.align(Alignment.CenterStart)
				.padding(start = 4.dp),
		)
		Box(
			Modifier
				.align(Alignment.Center)
				.width(36.dp)
				.height(4.dp)
				.background(LocalAppTheme.current.pullHandle, RoundedCornerShape(2.dp)),
		)
		Row(
			Modifier
				.align(Alignment.CenterEnd)
				.padding(end = 4.dp),
		) {
			if (!isPreview) {
				CircleIconButton(
					iconRes = R.drawable.ic_settings,
					contentDescription = stringResource(R.string.open_settings_button),
					selected = false,
					onClick = onOpenSettings,
				)
			}
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

/**
 * Shared 40dp touch target for [CircleIconButton]'s pull-bar icons and [NavIconButton]'s pack nav
 * row icons - a square or circular slot that highlights white when [selected]. The 40dp size lives
 * only here, not as a value shared by its two callers.
 */
@Composable
private fun SelectableIconSlot(
	selected: Boolean,
	onClick: () -> Unit,
	contentPadding: Dp,
	modifier: Modifier = Modifier,
	shape: Shape = RectangleShape,
	content: @Composable BoxScope.() -> Unit,
) {
	Box(
		modifier
			.size(40.dp)
			.clip(shape)
			.background(if (selected) Color.White else Color.Transparent)
			.clickable(onClick = onClick)
			.padding(contentPadding),
		contentAlignment = Alignment.Center,
		content = content,
	)
}

@Composable
private fun CircleIconButton(
	iconRes: Int,
	contentDescription: String,
	selected: Boolean,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	SelectableIconSlot(
		selected = selected,
		onClick = onClick,
		contentPadding = 8.dp,
		shape = CircleShape,
		modifier = modifier,
	) {
		Image(
			painter = painterResource(iconRes),
			contentDescription = contentDescription,
			colorFilter = ColorFilter.tint(LocalAppTheme.current.fg),
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
			.padding(4.dp),
	) {
		for (pack in packs) {
			NavIconButton(
				thumbnail = pack.thumbnail,
				selected = pack.packName == activeSection,
				onClick = { onPackClick(pack.packName) },
				modifier = Modifier.padding(4.dp),
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
	SelectableIconSlot(
		selected = selected,
		onClick = onClick,
		contentPadding = 6.dp,
		modifier = modifier,
	) {
		if (thumbnail != null) {
			StickerImage(
				file = thumbnail,
				contentDescription = stringResource(R.string.pack_icon),
				modifier = Modifier.fillMaxSize(),
			)
		} else {
			Image(
				painter = painterResource(R.drawable.ic_recent),
				contentDescription = stringResource(R.string.pack_icon),
				colorFilter = ColorFilter.tint(LocalAppTheme.current.fg),
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
	isRefreshing: Boolean,
	onStickerClick: (File) -> Unit,
	onStickerLongClick: (File) -> Unit,
	onZoomStep: (Int) -> Unit,
	onRefresh: () -> Unit,
) {
	val density = LocalDensity.current
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
				.boardGestures(onZoomStep),
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
			color = LocalAppTheme.current.fg,
			fontSize = 16.sp, // the keyboard's standard body text size, also used elsewhere in this file
			fontWeight = FontWeight.Bold,
		),
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 10.dp)
			.padding(
				top = 14.dp,
				bottom = 4.dp,
			),
	)
}

@Composable
private fun SectionEmptyMessage(text: String) {
	BasicText(
		text = text,
		style = TextStyle(color = LocalAppTheme.current.fg, fontSize = 16.sp),
		modifier = Modifier
			.fillMaxWidth()
			.alpha(0.6f)
			.padding(horizontal = 10.dp)
			.padding(bottom = 10.dp),
	)
}

@Composable
private fun StickerCell(
	file: File,
	contentDescription: String,
	onClick: () -> Unit,
	onLongClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val haptic = LocalHapticFeedback.current
	Box(
		modifier
			.padding(4.dp)
			.aspectRatio(1f)
			.combinedClickable(
				onClick = {
					haptic.performHapticFeedback(HapticFeedbackType.LongPress)
					onClick()
				},
				onLongClick = onLongClick,
			),
	) {
		StickerImage(
			file = file,
			contentDescription = contentDescription,
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
) {
	Column(Modifier.fillMaxSize()) {
		LazyRow(Modifier.weight(1f).fillMaxWidth()) {
			items(results.size, key = { results[it].path }) { index ->
				val file = results[index]
				StickerCell(
					file = file,
					contentDescription = stringResource(R.string.pack_icon),
					onClick = { onStickerClick(file) },
					onLongClick = { onStickerLongClick(file) },
					modifier = Modifier.fillMaxHeight(),
				)
			}
		}
		SearchQueryBar(query)
		BoxWithConstraints(
			Modifier
				.fillMaxWidth()
				.padding(bottom = 10.dp),
		) {
			// The widest row's keys share maxWidth evenly, with no margin between them.
			val keyWidth = maxWidth / QWERTY_TOP_ROW.length
			QwertyKeyboard(
				keyWidth = keyWidth,
				keyHeight = keyWidth * 1.3f,
				onKeyTap = { onQueryChange(query + it) },
				onBackspace = {
					if (query.isNotEmpty()) onQueryChange(query.substring(0, query.length - 1))
				},
				onClear = { onQueryChange("") },
			)
		}
	}
}

/**
 * The search query, shown above the qwerty keyboard with a blinking text-cursor after it, so the
 * bar reads clearly as live text input rather than a static label.
 */
@Composable
private fun SearchQueryBar(query: String) {
	var cursorVisible by remember { mutableStateOf(true) }
	LaunchedEffect(Unit) {
		while (true) {
			delay(CURSOR_BLINK_HALF_PERIOD_MS)
			cursorVisible = !cursorVisible
		}
	}
	val density = LocalDensity.current
	Row(
		Modifier
			.fillMaxWidth()
			// 40sp, not 40dp - scales with the system font size the same way the row's own text does.
			.height(with(density) { 40.sp.toDp() })
			.padding(horizontal = 16.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		BasicText(
			text = query,
			style = TextStyle(color = LocalAppTheme.current.fg, fontSize = 16.sp),
		)
		Box(
			Modifier
				.padding(start = 2.dp)
				.width(2.dp)
				.height(20.dp)
				.alpha(if (cursorVisible) 1f else 0f)
				.background(LocalAppTheme.current.fg),
		)
	}
}

/**
 * Staggered qwerty layout with no long-press symbols - just letters, space, and backspace:
 * ```
 * q w e r t y u i o p
 *  a s d f g h j k l
 * __ z x c v b n m <
 * ```
 * where `<` is backspace and `__` is the spacebar.
 */
@Composable
private fun QwertyKeyboard(
	keyWidth: Dp,
	keyHeight: Dp,
	onKeyTap: (String) -> Unit,
	onBackspace: () -> Unit,
	onClear: () -> Unit,
) {
	Column(Modifier.fillMaxWidth()) {
		Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
			QwertyRowKeys(keyWidth, keyHeight, QWERTY_TOP_ROW, onKeyTap)
		}
		Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
			QwertyRowKeys(keyWidth, keyHeight, "asdfghjkl", onKeyTap)
		}
		Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
			QwertyKey(
				iconRes = R.drawable.ic_space,
				contentDescription = stringResource(R.string.space_key),
				width = keyWidth * 1.5f,
				height = keyHeight,
				onTap = { onKeyTap(" ") },
				onLongTap = { onKeyTap(" ") },
			)
			QwertyRowKeys(keyWidth, keyHeight, "zxcvbnm", onKeyTap)
			QwertyKey(
				iconRes = R.drawable.ic_backspace,
				contentDescription = stringResource(R.string.backspace_key),
				width = keyWidth * 1.5f,
				height = keyHeight,
				onTap = onBackspace,
				onLongTap = onClear,
			)
		}
	}
}

@Composable
private fun QwertyRowKeys(
	keyWidth: Dp,
	keyHeight: Dp,
	chars: String,
	onKeyTap: (String) -> Unit,
) {
	for (char in chars) {
		val key = char.toString()
		QwertyKey(
			text = key,
			width = keyWidth,
			height = keyHeight,
			onTap = { onKeyTap(key) },
			onLongTap = {},
		)
	}
}

@Composable
private fun QwertyKey(
	width: Dp,
	height: Dp,
	onTap: () -> Unit,
	onLongTap: () -> Unit,
	text: String? = null,
	iconRes: Int? = null,
	contentDescription: String? = null,
) {
	Box(
		Modifier
			.width(width)
			.height(height)
			.combinedClickable(onClick = onTap, onLongClick = onLongTap),
		contentAlignment = Alignment.Center,
	) {
		if (iconRes != null) {
			Image(
				painter = painterResource(iconRes),
				contentDescription = contentDescription,
				colorFilter = ColorFilter.tint(LocalAppTheme.current.fg),
				modifier = Modifier.size(20.dp),
			)
		} else if (text != null) {
			BasicText(
				text = text,
				style = TextStyle(color = LocalAppTheme.current.fg, fontSize = 16.sp),
			)
		}
	}
}

@Composable
private fun PreviewContent(sticker: File, onSend: () -> Unit) {
	Column(
		Modifier
			.fillMaxSize()
			.padding(horizontal = 10.dp)
			.padding(bottom = 10.dp),
	) {
		Column(
			Modifier
				.align(Alignment.CenterHorizontally)
				.padding(top = 8.dp),
			horizontalAlignment = Alignment.CenterHorizontally,
		) {
			BasicText(
				text = prettifyPackName(sticker.parent?.split('/')?.last() ?: ""),
				style = TextStyle(
					color = LocalAppTheme.current.accent,
					fontWeight = FontWeight.Bold,
					fontSize = 20.sp, // subheading size
				),
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
			BasicText(
				text = trimString(sticker.name),
				style = TextStyle(color = LocalAppTheme.current.fg, fontSize = 10.sp), // tiny caption size
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
				modifier = Modifier.alpha(0.6f),
			)
		}
		Box(
			Modifier
				.weight(1f)
				.fillMaxWidth()
				.padding(top = 8.dp)
				.clickable(onClick = onSend),
		) {
			StickerImage(
				file = sticker,
				contentDescription = stringResource(R.string.send_sticker),
				modifier = Modifier
					.fillMaxSize()
					.padding(16.dp),
			)
		}
	}
}

/**
 * Pinch to zoom (spread = fewer, bigger stickers per row; pinch = more, smaller), living at the
 * [BoardGrid] level so it can intercept multi-pointer gestures before the grid's own
 * vertical-scroll handling sees them, without stealing plain single-finger vertical scrolling or
 * taps.
 */
private fun Modifier.boardGestures(onZoomStep: (Int) -> Unit): Modifier = pointerInput(Unit) {
	awaitEachGesture {
		var cumulativeZoom = 1f
		var prevPinchDistance = 0f
		do {
			val event = awaitPointerEvent(PointerEventPass.Initial)
			val pressed = event.changes.filter { it.pressed }
			if (pressed.size >= 2) {
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
			} else {
				prevPinchDistance = 0f
			}
		} while (event.changes.any { it.pressed })
	}
}
