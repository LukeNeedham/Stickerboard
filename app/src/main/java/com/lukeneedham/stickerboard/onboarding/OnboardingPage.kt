

/**
 * Wires [OnboardingPageContent] up with [OnboardingViewModel] and the enable-keyboard/choose-dir system
 * intents - the nav-host destination that used to be OnboardingActivity. [onFinished] replaces
 * this destination with the settings page on the shared back stack so the user can never
 * swipe/back their way back into onboarding.
 */
@Composable
fun OnboardingPage(
	onFinished: () -> Unit,
	modifier: Modifier = Modifier,
	viewModel: OnboardingViewModel = viewModel(),
) {
	val context = LocalContext.current
	val uiState by viewModel.uiState.collectAsStateWithLifecycle()

	LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshRequirements() }

	val chooseDirResultLauncher = rememberLauncherForActivityResult(
		ActivityResultContracts.StartActivityForResult(),
	) { result ->
		if (result.resultCode == Activity.RESULT_OK) {
			viewModel.onDirectorySelected(result.data?.data)
		}
	}

	OnboardingPageContent(
		state = uiState,
		onEnableKeyboard = { context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) },
		onChooseDir = {
			val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
				addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
				addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
			}
			chooseDirResultLauncher.launch(intent)
		},
		onFinish = {
			viewModel.onFinish()
			onFinished()
		},
		modifier = modifier,
	)
}
