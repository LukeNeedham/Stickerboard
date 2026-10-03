@file:OptIn(ExperimentalMaterial3Api::class)

package com.lukeneedham.stickerboard.gallery

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lukeneedham.stickerboard.R
import com.lukeneedham.stickerboard.model.BoardItem
import com.lukeneedham.stickerboard.prettifyPackName
import com.lukeneedham.stickerboard.settings.SettingsCard
import com.lukeneedham.stickerboard.settings.SettingsTopBar
import com.lukeneedham.stickerboard.trimString
import com.lukeneedham.stickerboard.utilities.StickerImage
import com.lukeneedham.stickerboard.utilities.isAnimatedSticker
import com.lukeneedham.stickerboard.utilities.renamedStickerFile
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shows every sticker pack using the same section/grid board layout as the keyboard's own board,
 * with an extra "add photo" cell at the end of each pack's section. Tapping a sticker opens an
 * enlarged preview in a swipeable bottom sheet, which has its own delete button; tapping the add-photo cell opens the device's
 * photo picker. Long-pressing a sticker instead enters multi-select mode, where tapping toggles
 * stickers in/out of the selection and the top bar's delete button removes all of them at once -
 * either way, a confirmation dialog stands between the tap and the actual deletion, and a spinner
 * overlays a sticker's cell for as long as its delete is still in flight. A sticker being added -
 * from the add-photo cell or a share-import - shows a matching spinner placeholder in its pack's
 * section until it's actually landed on disk. A card above the grid shows where the stickers are
 * sourced from and lets the user change or open that folder or re-import from it - it scrolls with
 * the rest of the page, and pulling down anywhere re-imports from disk the same way the keyboard's
 * own pull-to-refresh does.
 */
@Composable
fun StickerGalleryPage(
	items: List<BoardItem>?,
	columns: Int,
	stickerDirDisplayName: String,
	lastUpdateDate: String,
	isRefreshing: Boolean,
	onBack: () -> Unit,
	onOpenFolder: () -> Unit,
	onOpenPackFolder: (packName: String) -> Unit,
	onChangeDirectory: () -> Unit,
	onRefresh: () -> Unit,
	onAddPhotoClick: (packName: String) -> Unit,
	onDeleteStickers: (Set<File>) -> Unit,
	onRenameSticker: (file: File, newBaseName: String, onResult: (File?) -> Unit) -> Unit,
	deletingStickers: Set<File>,
	modifier: Modifier = Modifier,
	gridState: LazyGridState = rememberLazyGridState(),
) {
	var previewSticker by remember { mutableStateOf<File?>(null) }
	var selectedStickers by remember { mutableStateOf(setOf<File>()) }
	var stickerPendingDelete by remember { mutableStateOf<File?>(null) }
	var stickerPendingRename by remember { mutableStateOf<File?>(null) }
	var isRenaming by remember { mutableStateOf(false) }
	var showBulkDeleteConfirm by remember { mutableStateOf(false) }

	val scope = rememberCoroutineScope()

	BackHandler(enabled = selectedStickers.isNotEmpty()) { selectedStickers = emptySet() }

	Scaffold(
		modifier = modifier,
		containerColor = MaterialTheme.colorScheme.background,
		topBar = {
			if (selectedStickers.isNotEmpty()) {
				SettingsTopBar(
					title = stringResource(R.string.selected_count_lbl, selectedStickers.size),
					onBack = { selectedStickers = emptySet() },
					actions = {
						IconButton(onClick = { showBulkDeleteConfirm = true }) {
							Icon(
								painter = painterResource(R.drawable.ic_delete),
								contentDescription = stringResource(R.string.delete_button),
								tint = MaterialTheme.colorScheme.error,
							)
						}
					},
				)
			} else {
				SettingsTopBar(
					title = stringResource(R.string.view_stickers_heading),
					onBack = onBack,
				)
			}
		},
	) { innerPadding ->
		PullToRefreshBox(
			isRefreshing = isRefreshing,
			onRefresh = onRefresh,
			modifier = Modifier.padding(innerPadding).fillMaxSize(),
		) {
			LazyVerticalGrid(
				columns = GridCells.Fixed(columns),
				state = gridState,
				modifier = Modifier.fillMaxSize(),
				contentPadding = PaddingValues(bottom = 20.dp),
			) {
				item(span = { GridItemSpan(maxLineSpan) }) {
					StickerSourceCard(
						stickerDirDisplayName = stickerDirDisplayName,
						lastUpdateDate = lastUpdateDate,
						isRefreshing = isRefreshing,
						onOpenFolder = onOpenFolder,
						onChangeDirectory = onChangeDirectory,
						onRefresh = onRefresh,
						modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
					)
				}
				val packSummaries = items.orEmpty()
					.filterIsInstance<BoardItem.Header>()
					.map { header ->
						PackSummary(
							packName = header.packName,
							displayName = header.displayName,
							stickerCount = items.orEmpty().count {
								it is BoardItem.Sticker && it.packName == header.packName
							},
							itemIndex = items.orEmpty().indexOf(header),
						)
					}
				// Items after the source card (and the pack list, when present) are offset in the grid.
				val itemsOffset = if (packSummaries.isEmpty()) 1 else 2
				if (packSummaries.isNotEmpty()) {
					item(span = { GridItemSpan(maxLineSpan) }) {
						PackListCard(
							packs = packSummaries,
							onPackClick = { pack ->
								scope.launch { gridState.animateScrollToItem(pack.itemIndex + itemsOffset) }
							},
							modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp),
						)
					}
				}
				if (items == null) {
					item(span = { GridItemSpan(maxLineSpan) }) {
						Box(
							modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
							contentAlignment = Alignment.Center,
						) {
							CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
						}
					}
				} else if (items.isEmpty()) {
					item(span = { GridItemSpan(maxLineSpan) }) {
						Text(
							text = stringResource(R.string.view_stickers_empty),
							color = MaterialTheme.colorScheme.onSurfaceVariant,
							textAlign = TextAlign.Center,
							modifier = Modifier.fillMaxWidth().padding(32.dp),
						)
					}
				} else {
					items(
						count = items.size,
						key = { index ->
							when (val item = items[index]) {
								is BoardItem.Header -> "header:${item.packName}"
								is BoardItem.EmptyMessage -> "empty:${item.packName}"
								is BoardItem.Sticker -> "sticker:${item.packName}:${item.file.path}"
								is BoardItem.AddPhoto -> "add:${item.packName}"
								is BoardItem.Loading -> "loading:${item.packName}:${item.token}"
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
							is BoardItem.Header -> GallerySectionHeader(
								text = item.displayName,
								onClick = { onOpenPackFolder(item.packName) },
							)
							is BoardItem.EmptyMessage -> GallerySectionEmptyMessage(item.message)
							is BoardItem.Sticker -> GalleryStickerCell(
								file = item.file,
								isSelected = item.file in selectedStickers,
								isDeleting = item.file in deletingStickers,
								selectionMode = selectedStickers.isNotEmpty(),
								onClick = {
									if (selectedStickers.isNotEmpty()) {
										selectedStickers = selectedStickers.toggled(item.file)
									} else {
										previewSticker = item.file
									}
								},
								onLongClick = { selectedStickers = selectedStickers.toggled(item.file) },
							)
							is BoardItem.AddPhoto -> GalleryAddPhotoCell(
								onClick = { onAddPhotoClick(item.packName) },
								enabled = selectedStickers.isEmpty(),
							)
							is BoardItem.Loading -> GalleryLoadingCell()
						}
					}
				}
			}
		}
	}

	previewSticker?.let { initial ->
		val stickers = remember(items) {
			items.orEmpty().filterIsInstance<BoardItem.Sticker>().map { it.file }
		}
		StickerPreviewSheet(
			stickers = stickers,
			initialSticker = initial,
			onDismiss = { previewSticker = null },
			onDeleteClick = { stickerPendingDelete = it },
			isRenaming = isRenaming,
			onRenameClick = { stickerPendingRename = it },
		)
	}

	stickerPendingRename?.let { sticker ->
		RenameStickerDialog(
			sticker = sticker,
			onConfirm = { newName ->
				isRenaming = true
				onRenameSticker(sticker, newName) { _ ->
					isRenaming = false
				}
				stickerPendingRename = null
			},
			onDismiss = { stickerPendingRename = null },
		)
	}

	stickerPendingDelete?.let { sticker ->
		DeleteConfirmationDialog(
			title = stringResource(R.string.delete_sticker_confirm_title),
			message = stringResource(R.string.delete_sticker_confirm_message),
			onConfirm = {
				onDeleteStickers(setOf(sticker))
				stickerPendingDelete = null
				previewSticker = null
			},
			onDismiss = { stickerPendingDelete = null },
		)
	}

	if (showBulkDeleteConfirm) {
		DeleteConfirmationDialog(
			title = stringResource(R.string.delete_stickers_confirm_title, selectedStickers.size),
			message = stringResource(R.string.delete_stickers_confirm_message),
			onConfirm = {
				onDeleteStickers(selectedStickers)
				selectedStickers = emptySet()
				showBulkDeleteConfirm = false
			},
			onDismiss = { showBulkDeleteConfirm = false },
		)
	}
}

/** Toggles [element]'s membership in this set, returning the updated copy. */
private fun <T> Set<T>.toggled(element: T): Set<T> =
	if (element in this) this - element else this + element

/**
 * Shows where the loaded stickers came from and when they were last
 * refreshed. The path itself (with a trailing chevron) opens that folder in the system file
 * browser; the pencil button next to it lets the user pick a different one. [isRefreshing] swaps
 * the trailing refresh button for an inline spinner rather than the app showing any toast.
 */
@Composable
private fun StickerSourceCard(
	stickerDirDisplayName: String,
	lastUpdateDate: String,
	isRefreshing: Boolean,
	onOpenFolder: () -> Unit,
	onChangeDirectory: () -> Unit,
	onRefresh: () -> Unit,
	modifier: Modifier = Modifier,
) {
	SettingsCard(modifier) {
		Row(
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.SpaceBetween,
			modifier = Modifier.fillMaxWidth(),
		) {
			Row(
				verticalAlignment = Alignment.CenterVertically,
				modifier = Modifier
					.weight(1f, fill = false)
					.heightIn(min = 48.dp)
					.clickable(enabled = !isRefreshing, onClick = onOpenFolder),
			) {
				Icon(
					painter = painterResource(R.drawable.ic_folder),
					contentDescription = null,
					tint = MaterialTheme.colorScheme.primary,
					modifier = Modifier.size(22.dp),
				)
				Text(
					text = stickerDirDisplayName,
					style = MaterialTheme.typography.titleMedium,
					color = MaterialTheme.colorScheme.onSurface,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
					modifier = Modifier.weight(1f, fill = false).padding(start = 10.dp),
				)
				Icon(
					painter = painterResource(R.drawable.ic_chevron_right),
					contentDescription = null,
					tint = MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.padding(start = 4.dp).size(18.dp),
				)
			}
			IconButton(onClick = onChangeDirectory, enabled = !isRefreshing) {
				Icon(
					painter = painterResource(R.drawable.ic_edit),
					contentDescription = stringResource(R.string.sticker_source_change_button),
					tint = MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.size(20.dp),
				)
			}
		}

		Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
			Icon(
				painter = painterResource(R.drawable.ic_recent),
				contentDescription = null,
				tint = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.size(20.dp),
			)
			Text(
				text = stringResource(R.string.sticker_source_last_refreshed, lastUpdateDate),
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.weight(1f).padding(start = 8.dp),
			)
			if (isRefreshing) {
				Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
					CircularProgressIndicator(
						modifier = Modifier.size(18.dp),
						strokeWidth = 2.dp,
						color = MaterialTheme.colorScheme.primary,
					)
				}
			} else {
				IconButton(onClick = onRefresh) {
					Icon(
						painter = painterResource(R.drawable.ic_refresh),
						contentDescription = stringResource(R.string.reload_sticker_pack_button),
						tint = MaterialTheme.colorScheme.onSurfaceVariant,
					)
				}
			}
		}
	}
}

private data class PackSummary(
	val packName: String,
	val displayName: String,
	val stickerCount: Int,
	/** Index of the pack's header within the board items (not the grid). */
	val itemIndex: Int,
)

/** Lists every pack as a tile (name over sticker count) in a [PACK_LIST_COLUMNS]-wide grid; tapping a tile calls [onPackClick] so the page
 * can scroll to that pack's section below. */
@Composable
private fun PackListCard(
	packs: List<PackSummary>,
	onPackClick: (PackSummary) -> Unit,
	modifier: Modifier = Modifier,
) {
	SettingsCard(modifier) {
		Text(
			text = stringResource(R.string.gallery_pack_list_heading),
			style = MaterialTheme.typography.titleMedium,
			fontWeight = FontWeight.Bold,
			color = MaterialTheme.colorScheme.onSurface,
		)
		Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
			packs.chunked(PACK_LIST_COLUMNS).forEach { rowPacks ->
				Row(
					horizontalArrangement = Arrangement.spacedBy(8.dp),
					modifier = Modifier.fillMaxWidth(),
				) {
					rowPacks.forEach { pack ->
						PackTile(
							pack = pack,
							onClick = { onPackClick(pack) },
							modifier = Modifier.weight(1f),
						)
					}
					// Keeps the last row's tiles the same width as the full rows above.
					repeat(PACK_LIST_COLUMNS - rowPacks.size) {
						Box(modifier = Modifier.weight(1f))
					}
				}
			}
		}
	}
}

private const val PACK_LIST_COLUMNS = 4

/** A rounded tonal tile with the pack name above its sticker count, both centered. */
@Composable
private fun PackTile(pack: PackSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
	Column(
		modifier = modifier
			.height(72.dp)
			.clip(RoundedCornerShape(16.dp))
			.background(MaterialTheme.colorScheme.surfaceVariant)
			.clickable(onClick = onClick)
			.padding(horizontal = 6.dp, vertical = 8.dp),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.Center,
	) {
		Text(
			text = pack.displayName,
			style = MaterialTheme.typography.labelLarge,
			fontWeight = FontWeight.Bold,
			color = MaterialTheme.colorScheme.onSurface,
			textAlign = TextAlign.Center,
			maxLines = 2,
			overflow = TextOverflow.Ellipsis,
		)
		Text(
			text = stringResource(R.string.gallery_pack_sticker_count, pack.stickerCount),
			style = MaterialTheme.typography.labelMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
	}
}

@Composable
private fun GallerySectionHeader(text: String, onClick: () -> Unit) {
	Row(
		verticalAlignment = Alignment.CenterVertically,
		modifier = Modifier
			.fillMaxWidth()
			.clickable(onClick = onClick)
			.padding(horizontal = 20.dp)
			.padding(top = 14.dp, bottom = 4.dp),
	) {
		Text(
			text = text,
			style = MaterialTheme.typography.titleMedium,
			color = MaterialTheme.colorScheme.onBackground,
		)
		Icon(
			painter = painterResource(R.drawable.ic_chevron_right),
			contentDescription = null,
			tint = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.padding(start = 4.dp).size(18.dp),
		)
	}
}

@Composable
private fun GallerySectionEmptyMessage(text: String) {
	Text(
		text = text,
		style = MaterialTheme.typography.bodyMedium,
		color = MaterialTheme.colorScheme.onSurfaceVariant,
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 20.dp)
			.padding(bottom = 12.dp),
	)
}

/** [selectionMode] shows a selection badge in the corner (filled and checked when [isSelected]) -
 * long-pressing any cell enters selection mode, after which tapping any cell toggles it instead of
 * opening the full-screen preview. Animated stickers (GIF, animated WebP/PNG/AVIF, video) get a small play badge in the bottom-right corner, since they're shown as a static frame here. [isDeleting] dims the sticker and overlays a spinner instead,
 * and disables both taps, for as long as it's still visible here while its delete is in flight. */
@Composable
private fun GalleryStickerCell(
	file: File,
	isSelected: Boolean,
	isDeleting: Boolean,
	selectionMode: Boolean,
	onClick: () -> Unit,
	onLongClick: () -> Unit,
) {
	val haptic = LocalHapticFeedback.current
	val isAnimated by produceState(false, file) { value = withContext(Dispatchers.IO) { isAnimatedSticker(file) } }
	Box(
		modifier = Modifier
			.padding(4.dp)
			.aspectRatio(1f)
			.combinedClickable(
				enabled = !isDeleting,
				onClick = {
					haptic.performHapticFeedback(HapticFeedbackType.LongPress)
					onClick()
				},
				onLongClick = {
					haptic.performHapticFeedback(HapticFeedbackType.LongPress)
					onLongClick()
				},
			),
	) {
		StickerImage(
			file = file,
			animate = false,
			contentDescription = stringResource(R.string.pack_icon),
			modifier = Modifier
				.fillMaxSize()
				.let { imageModifier ->
					if (isSelected) {
						imageModifier
							.clip(RoundedCornerShape(10.dp))
							.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
					} else {
						imageModifier
					}
				}
				.alpha(if (isDeleting) 0.3f else 1f),
		)
		if (isAnimated && !isDeleting) {
			Box(
				modifier = Modifier
					.align(Alignment.BottomEnd)
					.padding(4.dp)
					.size(22.dp)
					.clip(CircleShape)
					.background(Color.Black.copy(alpha = 0.5f)),
				contentAlignment = Alignment.Center,
			) {
				Icon(
					painter = painterResource(R.drawable.ic_play),
					contentDescription = stringResource(R.string.sticker_animated_content_description),
					tint = Color.White,
					modifier = Modifier.size(12.dp),
				)
			}
		}
		if (selectionMode && !isDeleting) {
			Box(
				modifier = Modifier
					.align(Alignment.TopEnd)
					.padding(4.dp)
					.size(22.dp)
					.clip(CircleShape)
					.background(
						if (isSelected) {
							MaterialTheme.colorScheme.primary
						} else {
							Color.Black.copy(alpha = 0.35f)
						},
					)
					.border(1.5.dp, Color.White, CircleShape),
				contentAlignment = Alignment.Center,
			) {
				if (isSelected) {
					Icon(
						painter = painterResource(R.drawable.ic_check),
						contentDescription = stringResource(R.string.sticker_selected_content_description),
						tint = Color.White,
						modifier = Modifier.size(14.dp),
					)
				}
			}
		}
		if (isDeleting) {
			CircularProgressIndicator(
				modifier = Modifier.align(Alignment.Center).size(24.dp),
				strokeWidth = 2.dp,
				color = MaterialTheme.colorScheme.primary,
			)
		}
	}
}

/** Same footprint as a sticker cell, so the grid stays aligned, but the visible circle itself is
 * small and centered - a sticker-sized button here would dwarf the actual stickers around it. The
 * whole cell is tappable, not just the circle.
 * Disabled (but still shown, to keep the grid's layout stable) while a bulk selection is active. */
@Composable
private fun GalleryAddPhotoCell(onClick: () -> Unit, enabled: Boolean) {
	val haptic = LocalHapticFeedback.current
	Box(
		modifier = Modifier
			.padding(4.dp)
			.aspectRatio(1f)
			.clip(RoundedCornerShape(10.dp))
			.clickable(enabled = enabled) {
				haptic.performHapticFeedback(HapticFeedbackType.LongPress)
				onClick()
			},
		contentAlignment = Alignment.Center,
	) {
		Box(
			modifier = Modifier
				.size(40.dp)
				.clip(CircleShape)
				.background(MaterialTheme.colorScheme.surfaceVariant),
			contentAlignment = Alignment.Center,
		) {
			Icon(
				painter = painterResource(R.drawable.ic_add),
				contentDescription = stringResource(R.string.add_photo_content_description),
				tint = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.size(20.dp),
			)
		}
	}
}

/** Same footprint as a sticker cell - shown in a pack's section in place of a sticker that's still
 * being copied in, either via [GalleryAddPhotoCell] or a share-import, until it lands on disk and
 * a real [BoardItem.Sticker] takes its place. */
@Composable
private fun GalleryLoadingCell() {
	Box(
		modifier = Modifier
			.padding(4.dp)
			.aspectRatio(1f)
			.clip(RoundedCornerShape(10.dp))
			.background(MaterialTheme.colorScheme.surfaceVariant),
		contentAlignment = Alignment.Center,
	) {
		CircularProgressIndicator(
			modifier = Modifier.size(24.dp),
			strokeWidth = 2.dp,
			color = MaterialTheme.colorScheme.primary,
		)
	}
}

/** An enlarged preview of a sticker in a bottom sheet (drag handle, dismissed by dragging it down
 * or tapping outside it), opened on [initialSticker]. Only the sticker image swipes horizontally
 * between all [stickers]; the pack name, filename, rename and delete controls stay put and update in
 * place to describe whichever sticker is showing. */
@Composable
private fun StickerPreviewSheet(
	stickers: List<File>,
	initialSticker: File,
	onDismiss: () -> Unit,
	onDeleteClick: (File) -> Unit,
	isRenaming: Boolean,
	onRenameClick: (File) -> Unit,
) {
	if (stickers.isEmpty()) {
		LaunchedEffect(Unit) { onDismiss() }
		return
	}
	val pagerState = rememberPagerState(
		initialPage = stickers.indexOf(initialSticker).coerceAtLeast(0),
		pageCount = { stickers.size },
	)
	val current = stickers[pagerState.currentPage.coerceIn(stickers.indices)]
	ModalBottomSheet(
		onDismissRequest = onDismiss,
		sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
	) {
		Box(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 20.dp)) {
			Column(
				modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
				horizontalAlignment = Alignment.CenterHorizontally,
			) {
				Text(
					text = prettifyPackName(current.parentFile?.name.orEmpty()),
					style = MaterialTheme.typography.titleMedium,
					fontWeight = FontWeight.Bold,
					color = MaterialTheme.colorScheme.primary,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
					modifier = Modifier.padding(horizontal = 48.dp),
				)
				Row(verticalAlignment = Alignment.CenterVertically) {
					Text(
						text = trimString(current.name),
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
						modifier = Modifier.weight(1f, fill = false),
					)
					if (isRenaming) {
						Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
							CircularProgressIndicator(
								modifier = Modifier.size(16.dp),
								strokeWidth = 2.dp,
								color = MaterialTheme.colorScheme.primary,
							)
						}
					} else {
						IconButton(onClick = { onRenameClick(current) }, modifier = Modifier.size(32.dp)) {
							Icon(
								painter = painterResource(R.drawable.ic_edit),
								contentDescription = stringResource(R.string.rename_sticker_button),
								tint = MaterialTheme.colorScheme.onSurfaceVariant,
								modifier = Modifier.size(16.dp),
							)
						}
					}
				}
				HorizontalPager(
					state = pagerState,
					key = { stickers[it].path },
					modifier = Modifier.fillMaxWidth().height(320.dp).padding(top = 16.dp),
				) { page ->
					val sticker = stickers[page]
					StickerImage(
						file = sticker,
						contentDescription = trimString(sticker.name),
						modifier = Modifier.fillMaxSize(),
					)
				}
			}
			IconButton(
				onClick = { onDeleteClick(current) },
				modifier = Modifier.align(Alignment.TopEnd).padding(end = 12.dp),
			) {
				Icon(
					painter = painterResource(R.drawable.ic_delete),
					contentDescription = stringResource(R.string.delete_sticker_button),
					tint = MaterialTheme.colorScheme.error,
				)
			}
		}
	}
}

/** Edits [sticker]'s name (without its extension). Confirm is disabled until the new name is valid
 * and not already taken by another file in the same pack. */
@Composable
private fun RenameStickerDialog(
	sticker: File,
	onConfirm: (String) -> Unit,
	onDismiss: () -> Unit,
) {
	var name by remember(sticker) { mutableStateOf(sticker.nameWithoutExtension) }
	val target = renamedStickerFile(sticker, name)
	val isValid = target != null && (target == sticker || !target.exists())
	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text(stringResource(R.string.rename_sticker_title)) },
		text = {
			OutlinedTextField(
				value = name,
				onValueChange = { name = it },
				label = { Text(stringResource(R.string.rename_sticker_label)) },
				singleLine = true,
				isError = !isValid,
				supportingText = if (!isValid) {
					{ Text(stringResource(R.string.rename_sticker_error)) }
				} else {
					null
				},
			)
		},
		confirmButton = {
			TextButton(onClick = { onConfirm(name) }, enabled = isValid) {
				Text(stringResource(R.string.rename_button))
			}
		},
		dismissButton = {
			TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel_button)) }
		},
	)
}

/** A destructive-action confirmation dialog shared by the single-sticker and bulk deletes. */
@Composable
private fun DeleteConfirmationDialog(
	title: String,
	message: String,
	onConfirm: () -> Unit,
	onDismiss: () -> Unit,
) {
	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text(title) },
		text = { Text(message) },
		confirmButton = {
			TextButton(onClick = onConfirm) {
				Text(
					text = stringResource(R.string.delete_button),
					color = MaterialTheme.colorScheme.error,
				)
			}
		},
		dismissButton = {
			TextButton(onClick = onDismiss) {
				Text(stringResource(R.string.cancel_button))
			}
		},
	)
}

/**
 * Wires [StickerGalleryPage] up with its real dependencies (prefs, the internal sticker dir, the
 * photo picker, the sticker source directory picker) - the nav-host destination that used to be
 * StickerGalleryActivity, and now also owns choosing/reloading the sticker source directory that
 * used to live on the settings page.
 */
@Composable
fun GalleryRoute(
	onBack: () -> Unit,
	pendingImportPackName: String? = null,
	pendingImportUris: List<String> = emptyList(),
	modifier: Modifier = Modifier,
	viewModel: GalleryViewModel = viewModel(),
) {
	val uiState by viewModel.uiState.collectAsStateWithLifecycle()

	// Lifecycle.addObserver() (which this is built on) replays the events needed to bring a new
	// observer up to the current state, so this alone also covers the very first load - it fires
	// immediately here, since the page is only ever composed while already resumed. A separate
	// LaunchedEffect(Unit) for that initial load would run concurrently with this and double the
	// work every time the gallery opens.
	LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResumed() }

	// Runs once per GalleryViewModel (guarded on its side against a config change re-triggering
	// this LaunchedEffect) - a pack picked on the ShareImport screen that hasn't been copied yet.
	LaunchedEffect(pendingImportPackName) {
		if (pendingImportPackName != null) {
			viewModel.runPendingImport(pendingImportPackName, pendingImportUris.map { Uri.parse(it) })
		}
	}

	val gridState = rememberLazyGridState()
	// Jump to whatever scrollToPackName/scrollToFileName currently points at (e.g. a sticker just
	// imported via a share) as soon as items reflects it, then consume it so a later, unrelated
	// items update doesn't scroll back there again.
	LaunchedEffect(uiState.items, uiState.scrollToPackName) {
		val packName = uiState.scrollToPackName ?: return@LaunchedEffect
		val items = uiState.items ?: return@LaunchedEffect
		val targetIndex = items.indexOfFirst { item ->
			item is BoardItem.Sticker &&
				item.packName == packName &&
				(uiState.scrollToFileName == null || item.file.name == uiState.scrollToFileName)
		}.takeIf { it >= 0 } ?: items.indexOfFirst { item ->
			item is BoardItem.Header && item.packName == packName
		}
		if (targetIndex >= 0) {
			gridState.animateScrollToItem(targetIndex)
		}
		viewModel.onScrolledToTarget()
	}

	val chooseDirLauncher = rememberLauncherForActivityResult(
		ActivityResultContracts.StartActivityForResult(),
	) { result ->
		if (result.resultCode == Activity.RESULT_OK) {
			viewModel.onDirectorySelected(result.data?.data)
		}
	}

	var pendingPackName by remember { mutableStateOf<String?>(null) }
	val pickPhotosLauncher =
		rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
			val packName = pendingPackName
			pendingPackName = null
			if (packName != null && uris.isNotEmpty()) {
				viewModel.addPhotosToPack(packName, uris)
			}
		}

	val context = LocalContext.current
	StickerGalleryPage(
		items = uiState.items,
		columns = viewModel.columns,
		stickerDirDisplayName = uiState.stickerDirDisplayName,
		lastUpdateDate = uiState.lastUpdateDate,
		isRefreshing = uiState.isRefreshing,
		onBack = onBack,
		onOpenFolder = { viewModel.openStickerFolderIntent()?.let { context.startActivity(it) } },
		onOpenPackFolder = { packName ->
			viewModel.openStickerFolderIntent(packName)?.let { context.startActivity(it) }
		},
		onChangeDirectory = {
			val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
				addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
				addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
			}
			chooseDirLauncher.launch(intent)
		},
		onRefresh = { viewModel.refreshStickers() },
		onAddPhotoClick = { packName ->
			pendingPackName = packName
			pickPhotosLauncher.launch(
				PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
			)
		},
		onDeleteStickers = { files -> viewModel.deleteStickers(files) },
		onRenameSticker = { file, name, onResult -> viewModel.renameSticker(file, name, onResult) },
		deletingStickers = uiState.deletingStickers,
		modifier = modifier,
		gridState = gridState,
	)
}
