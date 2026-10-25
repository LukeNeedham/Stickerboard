

/**
 * Wires [SettingsPageContent] up with its real dependencies (the enable-keyboard system intent) and
 * [SettingsViewModel] - the nav-host destination that used to be MainActivity itself.
 */
@Composable
fun SettingsPage(
	onViewStickers: () -> Unit,
	onOpenDebug: () -> Unit,
	modifier: Modifier = Modifier,
	viewModel: SettingsViewModel = viewModel(),
) {
	val context = LocalContext.current
	val uiState by viewModel.uiState.collectAsStateWithLifecycle()

	LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshKeyboardEnabled() }

	SettingsPageContent(
		state = uiState,
		onEnableKeyboard = { context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) },
		onTryItOutMediaReceived = viewModel::onTryItOutMediaReceived,
		onViewStickers = onViewStickers,
		onOpenDebug = onOpenDebug,
		modifier = modifier,
	)
}
