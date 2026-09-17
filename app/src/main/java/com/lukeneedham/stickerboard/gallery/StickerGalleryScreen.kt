package com.lukeneedham.stickerboard.gallery

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
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
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.preference.PreferenceManager
import coil.compose.AsyncImage
import com.elvishew.xlog.XLog
import com.lukeneedham.stickerboard.R
import com.lukeneedham.stickerboard.model.BoardItem
import com.lukeneedham.stickerboard.model.StickerPack
import com.lukeneedham.stickerboard.prettifyPackName
import com.lukeneedham.stickerboard.settings.SettingsTopBar
import com.lukeneedham.stickerboard.trimString
import com.lukeneedham.stickerboard.utilities.Toaster
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Maximum number of stickers allowed in a single pack, mirrors StickerImporter's limit. */
private const val MAX_PACK_SIZE = 128

/** Bounds for iconsPerX, matching the settings screen's SeekBar range. */
private const val MIN_ICONS_PER_X = 2
private const val MAX_ICONS_PER_X = 6

/**
 * Shows every sticker pack using the same section/grid board layout as the keyboard's own board,
 * with an extra "add photo" cell at the end of each pack's section. Tapping a sticker opens a
 * read-only enlarged preview; tapping the add-photo cell opens the device's photo picker.
 */
@Composable
fun StickerGalleryScreen(
	items: List<BoardItem>?,
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
		if (items == null) {
			Box(
				modifier = Modifier.padding(innerPadding).fillMaxSize(),
				contentAlignment = Alignment.Center,
			) {
				CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
			}
		} else if (items.isEmpty()) {
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

/**
 * Wires [StickerGalleryScreen] up with its real dependencies (prefs, the internal sticker dir, the
 * photo picker) - the nav-host destination that used to be StickerGalleryActivity.
 */
@Composable
fun GalleryRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
	val context = LocalContext.current
	val scope = rememberCoroutineScope()
	val sharedPreferences = remember { PreferenceManager.getDefaultSharedPreferences(context) }
	val backupSharedPreferences = remember { context.getSharedPreferences("backup_prefs", 0) }
	val toaster = remember { Toaster(context) }
	val internalDir = remember { File(context.filesDir, "stickers") }

	val iconsPerX = remember {
		backupSharedPreferences.getInt("iconsPerX", 4).coerceIn(MIN_ICONS_PER_X, MAX_ICONS_PER_X)
	}
	val insensitiveSort = remember { backupSharedPreferences.getBoolean("insensitiveSort", false) }
	val vibrate = remember { backupSharedPreferences.getBoolean("vibrate", true) }

	fun sortedPackNames(loadedPacks: Map<String, StickerPack>): List<String> =
		if (insensitiveSort) {
			loadedPacks.keys.sortedWith(String.CASE_INSENSITIVE_ORDER)
		} else {
			loadedPacks.keys.sorted()
		}

	fun computeBoardItems(): List<BoardItem> {
		val packs =
			internalDir.listFiles { file ->
				file.isDirectory && !file.absolutePath.contains("__compatSticker__")
			} ?: arrayOf()

		val loadedPacks = HashMap<String, StickerPack>()
		for (file in packs) {
			val pack = StickerPack(file)
			if (pack.stickerList.isNotEmpty()) {
				loadedPacks[file.name] = pack
			}
		}

		val items = mutableListOf<BoardItem>()
		for (packName in sortedPackNames(loadedPacks)) {
			val stickers = loadedPacks[packName]?.stickerList ?: continue
			items.add(BoardItem.Header(packName, prettifyPackName(packName)))
			for (sticker in stickers) {
				items.add(BoardItem.Sticker(sticker, packName))
			}
			items.add(BoardItem.AddPhoto(packName))
		}
		return items
	}

	// Starts null (loading) rather than computing synchronously: computeBoardItems() does disk I/O
	// that's heavy enough to jank the nav-transition animation into this screen if run on the main
	// thread during initial composition.
	var boardItems by remember { mutableStateOf<List<BoardItem>?>(null) }

	suspend fun refreshBoardItems() {
		boardItems = withContext(Dispatchers.IO) { computeBoardItems() }
	}

	LaunchedEffect(Unit) { refreshBoardItems() }
	LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { scope.launch { refreshBoardItems() } }

	/**
	 * Best-effort copy of a just-added photo into the user's external sticker source directory, so
	 * that a future "Reload stickers" - which wipes and re-imports the internal copy from scratch -
	 * doesn't lose it. Silently does nothing if no source directory is configured; silently fails if
	 * the write doesn't succeed for any other reason, since the sticker is already usable from the
	 * internal copy regardless.
	 */
	fun copyToExternalSourceDir(packName: String, fileName: String, bytes: ByteArray) {
		val stickerDirPath = sharedPreferences.getString("stickerDirPath", null) ?: return
		try {
			val rootDir = DocumentFile.fromTreeUri(context, Uri.parse(stickerDirPath)) ?: return
			val packDocDir = rootDir.findFile(packName) ?: rootDir.createDirectory(packName) ?: return
			val newFile = packDocDir.createFile("application/octet-stream", fileName) ?: return
			context.contentResolver.openOutputStream(newFile.uri)?.use { it.write(bytes) }
		} catch (e: Exception) {
			XLog.e("There was an error copying a gallery photo into the external sticker source directory!")
			XLog.e(e)
		}
	}

	/**
	 * Copies a single gallery photo into packDir - and, best-effort, into the matching pack folder
	 * of the external sticker source directory too - returning true if the internal copy (the one
	 * the keyboard actually reads) succeeded.
	 */
	fun copyPhotoToPack(uri: Uri, packDir: File, packName: String): Boolean {
		return try {
			val mimeType = context.contentResolver.getType(uri)
			val extension =
				mimeType?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) } ?: "jpg"
			val fileName = "gallery_${System.currentTimeMillis()}_${System.nanoTime()}.$extension"

			val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return false
			File(packDir, fileName).outputStream().use { it.write(bytes) }

			copyToExternalSourceDir(packName, fileName, bytes)
			true
		} catch (e: IOException) {
			XLog.e("There was an IOException when copying a gallery photo into a pack!")
			XLog.e(e)
			false
		}
	}

	/** Copies the given gallery photo URIs into packName, up to MAX_PACK_SIZE stickers total. */
	fun addPhotosToPack(packName: String, uris: List<Uri>) {
		val packDir = File(internalDir, packName)
		scope.launch(Dispatchers.IO) {
			packDir.mkdirs()
			var packSize = packDir.listFiles { file -> file.isFile }?.size ?: 0
			var addedCount = 0
			var skippedLimit = false
			for (uri in uris) {
				if (packSize >= MAX_PACK_SIZE) {
					skippedLimit = true
					break
				}
				if (copyPhotoToPack(uri, packDir, packName)) {
					addedCount++
					packSize++
				}
			}

			val refreshedBoardItems = if (addedCount > 0) computeBoardItems() else null

			withContext(Dispatchers.Main) {
				val displayName = prettifyPackName(packName)
				if (addedCount > 0) {
					toaster.toast(context.getString(R.string.add_photo_050, addedCount, displayName))
					sharedPreferences.edit()
						.putInt(
							"numStickersImported",
							sharedPreferences.getInt("numStickersImported", 0) + addedCount,
						)
						.apply()
					boardItems = refreshedBoardItems
				} else if (!skippedLimit) {
					toaster.toast(context.getString(R.string.add_photo_051, displayName))
				}
				if (skippedLimit) {
					toaster.toast(context.getString(R.string.imported_032, MAX_PACK_SIZE, displayName))
				}
			}
		}
	}

	/**
	 * Opens the user's chosen external sticker source directory in their device's file browser app,
	 * if one is configured and something can handle it.
	 */
	fun openStickerFolder() {
		val stickerDirPath = sharedPreferences.getString("stickerDirPath", null)
		if (stickerDirPath == null) {
			toaster.toast(context.getString(R.string.open_folder_missing_dir))
			return
		}
		try {
			val treeUri = Uri.parse(stickerDirPath)
			val docUri =
				DocumentsContract.buildDocumentUriUsingTree(
					treeUri,
					DocumentsContract.getTreeDocumentId(treeUri),
				)
			val intent =
				Intent(Intent.ACTION_VIEW).apply {
					setDataAndType(docUri, DocumentsContract.Document.MIME_TYPE_DIR)
					addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
				}
			context.startActivity(intent)
		} catch (e: Exception) {
			XLog.e("Failed to open the sticker source directory in a file browser app")
			XLog.e(e)
			toaster.toast(context.getString(R.string.open_folder_052))
		}
	}

	var pendingPackName by remember { mutableStateOf<String?>(null) }
	val pickPhotosLauncher =
		rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
			val packName = pendingPackName
			pendingPackName = null
			if (packName != null && uris.isNotEmpty()) {
				addPhotosToPack(packName, uris)
			}
		}

	StickerGalleryScreen(
		items = boardItems,
		columns = iconsPerX,
		vibrate = vibrate,
		onBack = onBack,
		onOpenFolder = { openStickerFolder() },
		onAddPhotoClick = { packName ->
			pendingPackName = packName
			pickPhotosLauncher.launch(
				PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
			)
		},
		modifier = modifier,
	)
}
