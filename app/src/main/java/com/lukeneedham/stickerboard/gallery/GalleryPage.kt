

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
