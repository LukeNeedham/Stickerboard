@file:OptIn(ExperimentalMaterial3Api::class)

package com.lukeneedham.stickerboard.gallery

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.preference.PreferenceManager
import com.elvishew.xlog.XLog
import com.lukeneedham.stickerboard.R
import com.lukeneedham.stickerboard.model.BoardItem
import com.lukeneedham.stickerboard.model.StickerPack
import com.lukeneedham.stickerboard.prettifyPackName
import com.lukeneedham.stickerboard.settings.CardHeading
import com.lukeneedham.stickerboard.settings.FilledActionButton
import com.lukeneedham.stickerboard.settings.SettingsCard
import com.lukeneedham.stickerboard.settings.SettingsTopBar
import com.lukeneedham.stickerboard.settings.TonalActionButton
import com.lukeneedham.stickerboard.trimString
import com.lukeneedham.stickerboard.utilities.StickerImage
import com.lukeneedham.stickerboard.utilities.StickerImporter
import com.lukeneedham.stickerboard.utilities.Toaster
import com.lukeneedham.stickerboard.utilities.reimportStickersIfChanged
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Calendar
import kotlin.system.measureTimeMillis

/** Maximum number of stickers allowed in a single pack, mirrors StickerImporter's limit. */
private const val MAX_PACK_SIZE = 128

/** Bounds for iconsPerX, matching the settings screen's SeekBar range. */
private const val MIN_ICONS_PER_X = 2
private const val MAX_ICONS_PER_X = 6

/**
 * Minimum time to hold the pull-refresh indicator's `isRefreshing = true` state - mirrors
 * [com.lukeneedham.stickerboard.keyboard.KeyboardScreen]'s BoardGrid: without this floor, a
 * refresh that finishes within a single frame can flip true then false before PullToRefreshBox
 * observes a transition to animate, leaving the indicator stuck wherever the pull released it.
 */
private const val MIN_REFRESH_INDICATOR_MS = 500L

/**
 * Shows every sticker pack using the same section/grid board layout as the keyboard's own board,
 * with an extra "add photo" cell at the end of each pack's section. Tapping a sticker opens a
 * read-only enlarged preview; tapping the add-photo cell opens the device's photo picker. A card
 * above the grid shows where the stickers are sourced from and lets the user change or open that
 * folder or re-import from it - it scrolls with the rest of the page, and pulling down anywhere
 * re-imports from disk the same way the keyboard's own pull-to-refresh does.
 */
@Composable
fun StickerGalleryScreen(
	items: List<BoardItem>?,
	columns: Int,
	vibrate: Boolean,
	stickerDirDisplayName: String,
	lastUpdateDate: String,
	isRefreshing: Boolean,
	onBack: () -> Unit,
	onOpenFolder: () -> Unit,
	onChangeDirectory: () -> Unit,
	onRefresh: () -> Unit,
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
	}

	previewSticker?.let { sticker ->
		StickerPreviewDialog(sticker = sticker, onDismiss = { previewSticker = null })
	}
}

/**
 * Shows where the loaded stickers came from, how many there are, and when they were last
 * refreshed - plus buttons to open that folder in the system file browser, to change it, and to
 * re-import from it. [isRefreshing] swaps the refresh button for an inline spinner rather than
 * the app showing any toast.
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
		CardHeading(R.drawable.ic_folder, stringResource(R.string.sticker_source_heading))

		Row(verticalAlignment = Alignment.CenterVertically) {
			Icon(
				painter = painterResource(R.drawable.ic_folder),
				contentDescription = null,
				tint = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.size(16.dp),
			)
			Text(
				text = stickerDirDisplayName,
				style = MaterialTheme.typography.bodyMedium,
				fontWeight = FontWeight.SemiBold,
				color = MaterialTheme.colorScheme.onSurface,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
				modifier = Modifier.padding(start = 8.dp),
			)
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

		Row(verticalAlignment = Alignment.CenterVertically) {
			Icon(
				painter = painterResource(R.drawable.ic_recent),
				contentDescription = null,
				tint = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.size(16.dp),
			)
			Text(
				text = stringResource(R.string.sticker_source_last_refreshed, lastUpdateDate),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.padding(start = 8.dp),
			)
		}

		HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

		Row(
			horizontalArrangement = Arrangement.spacedBy(12.dp),
			modifier = Modifier.fillMaxWidth(),
		) {
			TonalActionButton(
				text = stringResource(R.string.open_folder_button),
				onClick = onOpenFolder,
				enabled = !isRefreshing,
				modifier = Modifier.weight(1f),
			)
			TonalActionButton(
				text = stringResource(R.string.update_sticker_pack_button),
				onClick = onChangeDirectory,
				enabled = !isRefreshing,
				modifier = Modifier.weight(1f),
			)
		}
		if (isRefreshing) {
			Row(
				modifier = Modifier.fillMaxWidth().height(40.dp),
				horizontalArrangement = Arrangement.Center,
				verticalAlignment = Alignment.CenterVertically,
			) {
				CircularProgressIndicator(
					modifier = Modifier.size(18.dp),
					strokeWidth = 2.dp,
					color = MaterialTheme.colorScheme.primary,
				)
				Text(
					text = stringResource(R.string.sticker_source_refreshing),
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.padding(start = 10.dp),
				)
			}
		} else {
			FilledActionButton(
				text = stringResource(R.string.reload_sticker_pack_button),
				onClick = onRefresh,
			)
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
private fun GalleryStickerCell(file: File, vibrate: Boolean, onClick: () -> Unit) {
	val haptic = LocalHapticFeedback.current
	Box(
		modifier = Modifier
			.padding(4.dp)
			.aspectRatio(1f)
			.clickable {
				if (vibrate) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
private fun GalleryAddPhotoCell(vibrate: Boolean, onClick: () -> Unit) {
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
					if (vibrate) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
 * Wires [StickerGalleryScreen] up with its real dependencies (prefs, the internal sticker dir, the
 * photo picker, the sticker source directory picker) - the nav-host destination that used to be
 * StickerGalleryActivity, and now also owns choosing/reloading the sticker source directory that
 * used to live on the settings screen.
 */
@Composable
fun GalleryRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
	val context = LocalContext.current
	val scope = rememberCoroutineScope()
	val sharedPreferences = remember { PreferenceManager.getDefaultSharedPreferences(context) }
	val backupSharedPreferences = remember { context.getSharedPreferences("backup_prefs", 0) }
	// Only ever passed through to StickerImporter, which logs warnings on it as it works - never
	// toasted, so re-importing here never pops up any message of its own (see onRefresh below).
	val toaster = remember { Toaster() }
	val internalDir = remember { File(context.filesDir, "stickers") }

	val iconsPerX = remember {
		backupSharedPreferences.getInt("iconsPerX", 4).coerceIn(MIN_ICONS_PER_X, MAX_ICONS_PER_X)
	}
	val insensitiveSort = remember { backupSharedPreferences.getBoolean("insensitiveSort", false) }
	val vibrate = remember { backupSharedPreferences.getBoolean("vibrate", true) }

	fun currentLastUpdateDate(): String {
		val default = context.getString(R.string.update_sticker_pack_info_date)
		return sharedPreferences.getString("lastUpdateDate", default) ?: default
	}

	/** The chosen source folder's own display name (e.g. "Stickers"), not its raw content:// URI -
	 * falling back to that raw path if the folder can no longer be resolved. */
	fun currentStickerDirDisplayName(): String {
		val path = sharedPreferences.getString("stickerDirPath", null)
			?: return context.getString(R.string.update_sticker_pack_info_path)
		return try {
			DocumentFile.fromTreeUri(context, Uri.parse(path))?.name ?: path
		} catch (e: Exception) {
			XLog.e("Failed to resolve a friendly name for the sticker source directory")
			XLog.e(e)
			path
		}
	}

	var stickerDirDisplayName by remember { mutableStateOf(currentStickerDirDisplayName()) }
	var lastUpdateDate by remember { mutableStateOf(currentLastUpdateDate()) }
	var isRefreshing by remember { mutableStateOf(false) }

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

	// Lifecycle.addObserver() (which this is built on) replays the events needed to bring a new
	// observer up to the current state, so this alone also covers the very first load - it fires
	// immediately here, since the screen is only ever composed while already resumed. A separate
	// LaunchedEffect(Unit) for that initial load would run concurrently with this and double the
	// work every time the gallery opens.
	LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
		scope.launch {
			val (items, dirName) = withContext(Dispatchers.IO) {
				computeBoardItems() to currentStickerDirDisplayName()
			}
			boardItems = items
			stickerDirDisplayName = dirName
		}
	}

	/**
	 * Best-effort copy of a just-added photo into the user's external sticker source directory, so
	 * that a future "Reload stickers" - which wipes and re-imports the internal copy from scratch -
	 * doesn't lose it. Silently does nothing if no source directory is configured; silently fails if
	 * the write doesn't succeed for any other reason, since the sticker is already usable from the
	 * internal copy regardless.
	 */
	fun copyToExternalSourceDir(packName: String, fileName: String, bytes: ByteArray) {
		val path = sharedPreferences.getString("stickerDirPath", null) ?: return
		try {
			val rootDir = DocumentFile.fromTreeUri(context, Uri.parse(path)) ?: return
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

	/**
	 * Copies the given gallery photo URIs into packName, up to MAX_PACK_SIZE stickers total. No
	 * toast on success/failure/limit by design - the grid updating (or not) is the feedback.
	 */
	fun addPhotosToPack(packName: String, uris: List<Uri>) {
		val packDir = File(internalDir, packName)
		scope.launch(Dispatchers.IO) {
			packDir.mkdirs()
			var packSize = packDir.listFiles { file -> file.isFile }?.size ?: 0
			var addedCount = 0
			for (uri in uris) {
				if (packSize >= MAX_PACK_SIZE) break
				if (copyPhotoToPack(uri, packDir, packName)) {
					addedCount++
					packSize++
				}
			}

			if (addedCount == 0) return@launch
			val refreshedBoardItems = computeBoardItems()

			withContext(Dispatchers.Main) {
				sharedPreferences.edit()
					.putInt(
						"numStickersImported",
						sharedPreferences.getInt("numStickersImported", 0) + addedCount,
					)
					.apply()
				boardItems = refreshedBoardItems
			}
		}
	}

	/**
	 * Opens the user's chosen external sticker source directory in their device's file browser app.
	 * Onboarding requires a sticker source directory to be chosen before this screen is reachable,
	 * so a null path here would be a bug rather than something to show the user a message about.
	 */
	fun openStickerFolder() {
		val path = sharedPreferences.getString("stickerDirPath", null) ?: return
		try {
			val treeUri = Uri.parse(path)
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
		}
	}

	/**
	 * Runs [reimport] (which does the actual disk work, off the main thread) then rescans internal
	 * storage and refreshes everything [StickerSourceCard] shows - all under the same isRefreshing
	 * spinner, held for at least [MIN_REFRESH_INDICATOR_MS] so the pull-to-refresh indicator always
	 * gets a visible transition to animate away, never a toast either way.
	 */
	fun runRefresh(reimport: suspend () -> Unit) {
		if (isRefreshing) return
		isRefreshing = true
		scope.launch {
			val (items, dirName) = withContext(Dispatchers.IO) {
				val elapsedMs = measureTimeMillis { reimport() }
				delay((MIN_REFRESH_INDICATOR_MS - elapsedMs).coerceAtLeast(0))
				computeBoardItems() to currentStickerDirDisplayName()
			}
			sharedPreferences.edit()
				.putString("lastUpdateDate", Calendar.getInstance().time.toString())
				.apply()
			lastUpdateDate = currentLastUpdateDate()
			stickerDirDisplayName = dirName
			boardItems = items
			isRefreshing = false
		}
	}

	/** Re-imports from the current source directory only if its contents actually changed - the
	 * same reload the keyboard's own pull-to-refresh performs - then rescans internal storage. */
	fun refreshStickers() {
		val path = sharedPreferences.getString("stickerDirPath", null) ?: return
		runRefresh { reimportStickersIfChanged(context, toaster, path) }
	}

	/** Switches the sticker source to [path] and does a full (re)import from it, since it's new. */
	fun changeStickerDirectory(path: String) {
		runRefresh { StickerImporter(context, toaster).importStickers(path) }
	}

	val chooseDirLauncher = rememberLauncherForActivityResult(
		ActivityResultContracts.StartActivityForResult(),
	) { result ->
		if (result.resultCode == Activity.RESULT_OK) {
			val uri = result.data?.data
			val stickerDirPath = result.data?.data.toString()
			if (uri != null) {
				val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
				context.contentResolver.takePersistableUriPermission(uri, takeFlags)
			}
			sharedPreferences.edit()
				.putString("stickerDirPath", stickerDirPath)
				.putString("recentCache", "")
				.putString("compatCache", "")
				.apply()
			changeStickerDirectory(stickerDirPath)
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
		stickerDirDisplayName = stickerDirDisplayName,
		lastUpdateDate = lastUpdateDate,
		isRefreshing = isRefreshing,
		onBack = onBack,
		onOpenFolder = { openStickerFolder() },
		onChangeDirectory = {
			val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
				addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
				addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
			}
			chooseDirLauncher.launch(intent)
		},
		onRefresh = { refreshStickers() },
		onAddPhotoClick = { packName ->
			pendingPackName = packName
			pickPhotosLauncher.launch(
				PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
			)
		},
		modifier = modifier,
	)
}
