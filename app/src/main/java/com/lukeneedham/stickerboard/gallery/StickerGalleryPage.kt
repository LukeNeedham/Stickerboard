@file:OptIn(ExperimentalMaterial3Api::class)

package com.lukeneedham.stickerboard.gallery

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import java.io.File

/**
 * Shows every sticker pack using the same section/grid board layout as the keyboard's own board,
 * with an extra "add photo" cell at the end of each pack's section. Tapping a sticker opens a
 * read-only enlarged preview; tapping the add-photo cell opens the device's photo picker. A card
 * above the grid shows where the stickers are sourced from and lets the user change or open that
 * folder or re-import from it - it scrolls with the rest of the page, and pulling down anywhere
 * re-imports from disk the same way the keyboard's own pull-to-refresh does.
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
	onChangeDirectory: () -> Unit,
	onRefresh: () -> Unit,
	onAddPhotoClick: (packName: String) -> Unit,
	modifier: Modifier = Modifier,
	gridState: LazyGridState = rememberLazyGridState(),
) {
	var previewSticker by remember { mutableStateOf<File?>(null) }

	Scaffold(
		modifier = modifier,
		containerColor = MaterialTheme.colorScheme.background,
		topBar = {
			SettingsTopBar(
				title = stringResource(R.string.view_stickers_heading),
				onBack = onBack,
			)
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
						totalStickers = items?.count { it is BoardItem.Sticker } ?: 0,
						totalPacks = items?.count { it is BoardItem.Header } ?: 0,
						isRefreshing = isRefreshing,
						onOpenFolder = onOpenFolder,
						onChangeDirectory = onChangeDirectory,
						onRefresh = onRefresh,
						modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
					)
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
							is BoardItem.Header -> GallerySectionHeader(item.displayName)
							is BoardItem.EmptyMessage -> GallerySectionEmptyMessage(item.message)
							is BoardItem.Sticker -> GalleryStickerCell(
								file = item.file,
								onClick = { previewSticker = item.file },
							)
							is BoardItem.AddPhoto -> GalleryAddPhotoCell(
								onClick = { onAddPhotoClick(item.packName) },
							)
						}
					}
				}
			}
		}
	}

	previewSticker?.let { sticker ->
		StickerPreviewDialog(sticker = sticker, onDismiss = { previewSticker = null })
	}
}

/**
 * Shows where the loaded stickers came from, how many there are, and when they were last
 * refreshed. The path itself (with a trailing chevron) opens that folder in the system file
 * browser; the pencil button next to it lets the user pick a different one. [isRefreshing] swaps
 * the trailing refresh button for an inline spinner rather than the app showing any toast.
 */
@Composable
private fun StickerSourceCard(
	stickerDirDisplayName: String,
	lastUpdateDate: String,
	totalStickers: Int,
	totalPacks: Int,
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

		Row(
			horizontalArrangement = Arrangement.spacedBy(12.dp),
			modifier = Modifier.fillMaxWidth(),
		) {
			StatTile(
				value = totalStickers,
				label = stringResource(R.string.sticker_source_total_stickers_lbl),
				modifier = Modifier.weight(1f),
			)
			StatTile(
				value = totalPacks,
				label = stringResource(R.string.sticker_source_total_packs_lbl),
				modifier = Modifier.weight(1f),
			)
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

/** A rounded tonal tile showing one big number over a small label - used for the sticker/pack
 * counts, so they read as at-a-glance stats rather than another line of label/value text. */
@Composable
private fun StatTile(value: Int, label: String, modifier: Modifier = Modifier) {
	Column(
		modifier = modifier
			.clip(RoundedCornerShape(16.dp))
			.background(MaterialTheme.colorScheme.surfaceVariant)
			.padding(vertical = 12.dp),
		horizontalAlignment = Alignment.CenterHorizontally,
	) {
		Text(
			text = value.toString(),
			style = MaterialTheme.typography.titleLarge,
			fontWeight = FontWeight.Bold,
			color = MaterialTheme.colorScheme.onSurface,
		)
		Text(
			text = label,
			style = MaterialTheme.typography.labelMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
	}
}

@Composable
private fun GallerySectionHeader(text: String) {
	Text(
		text = text,
		style = MaterialTheme.typography.titleMedium,
		color = MaterialTheme.colorScheme.onBackground,
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 20.dp)
			.padding(top = 14.dp, bottom = 4.dp),
	)
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

@Composable
private fun GalleryStickerCell(file: File, onClick: () -> Unit) {
	val haptic = LocalHapticFeedback.current
	Box(
		modifier = Modifier
			.padding(4.dp)
			.aspectRatio(1f)
			.clickable {
				haptic.performHapticFeedback(HapticFeedbackType.LongPress)
				onClick()
			},
	) {
		StickerImage(
			file = file,
			contentDescription = stringResource(R.string.pack_icon),
			modifier = Modifier.fillMaxSize(),
		)
	}
}

/** Same footprint as a sticker cell, so the grid stays aligned, but the tappable circle itself is
 * small and centered - a sticker-sized button here would dwarf the actual stickers around it. */
@Composable
private fun GalleryAddPhotoCell(onClick: () -> Unit) {
	val haptic = LocalHapticFeedback.current
	Box(
		modifier = Modifier
			.padding(4.dp)
			.aspectRatio(1f),
		contentAlignment = Alignment.Center,
	) {
		Box(
			modifier = Modifier
				.size(40.dp)
				.clip(CircleShape)
				.background(MaterialTheme.colorScheme.surfaceVariant)
				.clickable {
					haptic.performHapticFeedback(HapticFeedbackType.LongPress)
					onClick()
				},
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

/** A simple, read-only enlarged preview of a sticker - tap the image (or outside) to dismiss. */
@Composable
private fun StickerPreviewDialog(sticker: File, onDismiss: () -> Unit) {
	Dialog(
		onDismissRequest = onDismiss,
		properties = DialogProperties(usePlatformDefaultWidth = false),
	) {
		Column(
			modifier = Modifier
				.fillMaxSize()
				.background(MaterialTheme.colorScheme.background)
				.clickable(onClick = onDismiss)
				.padding(20.dp),
			horizontalAlignment = Alignment.CenterHorizontally,
		) {
			Text(
				text = prettifyPackName(sticker.parentFile?.name.orEmpty()),
				style = MaterialTheme.typography.titleMedium,
				fontWeight = FontWeight.Bold,
				color = MaterialTheme.colorScheme.primary,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
			Text(
				text = trimString(sticker.name),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
			StickerImage(
				file = sticker,
				contentDescription = trimString(sticker.name),
				modifier = Modifier
					.weight(1f)
					.fillMaxWidth()
					.padding(top = 16.dp)
					.clickable(onClick = onDismiss),
			)
		}
	}
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
	scrollToPackName: String? = null,
	scrollToFileName: String? = null,
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

	val gridState = rememberLazyGridState()
	// One-shot: once items load, jump to whatever scrollToPackName/scrollToFileName pointed at
	// (e.g. a sticker just imported via a share) and don't fight the user's own scrolling after.
	var hasScrolledToTarget by rememberSaveable(scrollToPackName, scrollToFileName) {
		mutableStateOf(scrollToPackName == null)
	}
	LaunchedEffect(uiState.items) {
		if (hasScrolledToTarget) return@LaunchedEffect
		val items = uiState.items ?: return@LaunchedEffect
		val targetIndex = items.indexOfFirst { item ->
			item is BoardItem.Sticker &&
				item.packName == scrollToPackName &&
				(scrollToFileName == null || item.file.name == scrollToFileName)
		}.takeIf { it >= 0 } ?: items.indexOfFirst { item ->
			item is BoardItem.Header && item.packName == scrollToPackName
		}
		if (targetIndex >= 0) {
			gridState.animateScrollToItem(targetIndex)
		}
		hasScrolledToTarget = true
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
		modifier = modifier,
		gridState = gridState,
	)
}
