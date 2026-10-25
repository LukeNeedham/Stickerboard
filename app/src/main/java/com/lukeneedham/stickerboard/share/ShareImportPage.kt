

/**
 * Wires [ShareImportPageContent] up with [ShareImportViewModel] - the nav-host destination reached from
 * [com.lukeneedham.stickerboard.navigation.Route.ShareImport]. Picking a pack calls [onPackChosen]
 * immediately, before anything is actually copied - the caller is expected to navigate to the
 * Stickers page and run the import there. Backing out before picking a pack calls [onCancel] instead.
 */
@Composable
fun ShareImportPage(
	imageUris: List<Uri>,
	onCancel: () -> Unit,
	onPackChosen: (packName: String) -> Unit,
	modifier: Modifier = Modifier,
	viewModel: ShareImportViewModel = viewModel(),
) {
	val uiState by viewModel.uiState.collectAsStateWithLifecycle()

	ShareImportPageContent(
		imageUris = imageUris,
		packNames = uiState.packNames,
		onBack = onCancel,
		onPackSelected = onPackChosen,
		modifier = modifier,
	)
}
