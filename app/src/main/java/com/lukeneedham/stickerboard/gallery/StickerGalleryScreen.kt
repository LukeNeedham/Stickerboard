package com.lukeneedham.stickerboard.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.lukeneedham.stickerboard.R
import com.lukeneedham.stickerboard.model.BoardItem
import com.lukeneedham.stickerboard.prettifyPackName
import com.lukeneedham.stickerboard.settings.SettingsTopBar
import com.lukeneedham.stickerboard.trimString
import java.io.File

/**
 * Shows every sticker pack using the same section/grid board layout as the keyboard's own board,
 * with an extra "add photo" cell at the end of each pack's section. Tapping a sticker opens a
 * read-only enlarged preview; tapping the add-photo cell opens the device's photo picker.
 */
@Composable
fun StickerGalleryScreen(
	items: List<BoardItem>,
	columns: Int,
	vibrate: Boolean,
	onBack: () -> Unit,
	onOpenFolder: () -> Unit,
	onAddPhotoClick: (packName: String) -> Unit,
	modifier: Modifier = Modifier,
) {
	var previewSticker by remember { mutableStateOf<File?>(null) }

	Scaffold(
		modifier = modifier,
		containerColor = MaterialTheme.colorScheme.background,
		topBar = {
			SettingsTopBar(
				title = stringResource(R.string.view_stickers_heading),
				onBack = onBack,
				actions = {
					IconButton(onClick = onOpenFolder) {
						Icon(
							painter = painterResource(R.drawable.ic_folder),
							contentDescription = stringResource(R.string.open_folder_button),
						)
					}
				},
			)
		},
	) { innerPadding ->
		if (items.isEmpty()) {
			Box(
				modifier = Modifier.padding(innerPadding).fillMaxSize(),
				contentAlignment = Alignment.Center,
			) {
				Text(
					text = stringResource(R.string.view_stickers_empty),
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					textAlign = TextAlign.Center,
					modifier = Modifier.padding(32.dp),
				)
			}
		} else {
			LazyVerticalGrid(
				columns = GridCells.Fixed(columns),
				modifier = Modifier.padding(innerPadding).fillMaxSize(),
				contentPadding = PaddingValues(bottom = 20.dp),
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
						is BoardItem.Header -> GallerySectionHeader(item.displayName)
						is BoardItem.EmptyMessage -> GallerySectionEmptyMessage(item.message)
						is BoardItem.Sticker -> GalleryStickerCell(
							file = item.file,
							vibrate = vibrate,
							onClick = { previewSticker = item.file },
						)
						is BoardItem.AddPhoto -> GalleryAddPhotoCell(
							vibrate = vibrate,
							onClick = { onAddPhotoClick(item.packName) },
						)
					}
				}
			}
		}
	}

	previewSticker?.let { sticker ->
		StickerPreviewDialog(sticker = sticker, onDismiss = { previewSticker = null })
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
private fun GalleryStickerCell(file: File, vibrate: Boolean, onClick: () -> Unit) {
	val haptic = LocalHapticFeedback.current
	Box(
		modifier = Modifier
			.padding(4.dp)
			.aspectRatio(1f)
			.clip(RoundedCornerShape(12.dp))
			.clickable {
				if (vibrate) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
				onClick()
			},
	) {
		AsyncImage(
			model = file,
			contentDescription = stringResource(R.string.pack_icon),
			contentScale = ContentScale.Fit,
			modifier = Modifier.fillMaxSize(),
		)
	}
}

@Composable
private fun GalleryAddPhotoCell(vibrate: Boolean, onClick: () -> Unit) {
	val haptic = LocalHapticFeedback.current
	Box(
		modifier = Modifier
			.padding(4.dp)
			.aspectRatio(1f)
			.clip(CircleShape)
			.background(MaterialTheme.colorScheme.surfaceVariant)
			.clickable {
				if (vibrate) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
				onClick()
			},
		contentAlignment = Alignment.Center,
	) {
		Icon(
			painter = painterResource(R.drawable.ic_add),
			contentDescription = stringResource(R.string.add_photo_content_description),
			tint = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.size(24.dp),
		)
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
			AsyncImage(
				model = sticker,
				contentDescription = trimString(sticker.name),
				contentScale = ContentScale.Fit,
				modifier = Modifier
					.weight(1f)
					.fillMaxWidth()
					.padding(top = 16.dp)
					.clickable(onClick = onDismiss),
			)
		}
	}
}
