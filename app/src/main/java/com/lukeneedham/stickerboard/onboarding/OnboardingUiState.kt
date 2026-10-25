

/** Everything the onboarding page needs to render - plain state, matching the rest of the app. */
data class OnboardingUiState(
	val keyboardEnabled: Boolean,
	val isImporting: Boolean,
	// Null until stickers have been successfully loaded at least once this session.
	val loadedStickerCount: Int?,
)
