@file:OptIn(ExperimentalMaterial3Api::class)

package com.lukeneedham.stickerboard.gallery

import com.lukeneedham.stickerboard.settings.StickerBoardSettingsTheme
import androidx.compose.ui.tooling.preview.Preview
import com.lukeneedham.stickerboard.utilities.StickerFiles
import com.lukeneedham.stickerboard.utilities.StickerMedia
import com.lukeneedham.stickerboard.utilities.StickerNames
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
import com.lukeneedham.stickerboard.settings.SettingsCard
import com.lukeneedham.stickerboard.settings.SettingsTopBar
import com.lukeneedham.stickerboard.utilities.StickerImage
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Wires [GalleryPageContent] up with its real dependencies (prefs, the internal sticker dir, the
 * photo picker, the sticker source directory picker) - the nav-host destination that used to be
 * StickerGalleryActivity, and now also owns choosing/reloading the sticker source directory that
 * used to live on the settings page.
 */
@Composable
fun GalleryPage(
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
	GalleryPageContent(
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
