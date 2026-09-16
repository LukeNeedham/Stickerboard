@file:OptIn(ExperimentalFoundationApi::class)

package com.lukeneedham.stickerboard.onboarding

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lukeneedham.stickerboard.R
import com.lukeneedham.stickerboard.settings.CardBody
import com.lukeneedham.stickerboard.settings.FilledActionButton
import com.lukeneedham.stickerboard.settings.SettingsCard
import com.lukeneedham.stickerboard.settings.TonalActionButton
import kotlinx.coroutines.launch

private const val PAGE_WELCOME = 0
private const val PAGE_KEYBOARD = 1
private const val PAGE_FOLDER = 2
private const val PAGE_COUNT = 3
private const val LAST_PAGE_INDEX = PAGE_FOLDER

/** Everything the onboarding screen needs to render - plain state, matching the rest of the app. */
data class OnboardingUiState(
	val keyboardEnabled: Boolean,
	val folderChosen: Boolean,
	val isImporting: Boolean,
)

/**
 * Walks first-time users through what StickerBoard is, how to enable it as a keyboard, and how to
 * choose a sticker source directory, before handing off to MainActivity. A page's "next" step is
 * blocked - by disabling the next/finish button, and bouncing back a swipe that tries to skip past
 * it - until that page's requirement (if any) is met.
 */
@Composable
fun OnboardingScreen(
	state: OnboardingUiState,
	onEnableKeyboard: () -> Unit,
	onChooseDir: () -> Unit,
	onRequirementUnmet: (message: String) -> Unit,
	onFinish: () -> Unit,
	progressIndicator: @Composable () -> Unit,
	modifier: Modifier = Modifier,
) {
	val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })
	val scope = rememberCoroutineScope()
	val keyboardRequiredMessage = stringResource(R.string.onboarding_keyboard_required)
	val folderRequiredMessage = stringResource(R.string.onboarding_folder_required)

	fun isPageRequirementMet(page: Int): Boolean = when (page) {
		PAGE_KEYBOARD -> state.keyboardEnabled
		PAGE_FOLDER -> state.folderChosen
		else -> true
	}

	fun requirementMessage(page: Int): String = when (page) {
		PAGE_KEYBOARD -> keyboardRequiredMessage
		PAGE_FOLDER -> folderRequiredMessage
		else -> ""
	}

	// Blocks swiping onto a page whose predecessor's required step isn't done yet - mirrors
	// ViewPager2's onPageSelected check, but only ever bounces back a forward swipe.
	LaunchedEffect(pagerState) {
		var previousPage = pagerState.currentPage
		snapshotFlow { pagerState.currentPage }.collect { page ->
			if (page > previousPage && !isPageRequirementMet(previousPage)) {
				onRequirementUnmet(requirementMessage(previousPage))
				pagerState.animateScrollToPage(previousPage)
			} else {
				previousPage = page
			}
		}
	}

	Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
		HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
			when (page) {
				PAGE_KEYBOARD -> OnboardingKeyboardPage(onEnableKeyboard)
				PAGE_FOLDER -> OnboardingFolderPage(state.isImporting, onChooseDir, progressIndicator)
				else -> OnboardingWelcomePage()
			}
		}
		Text(
			text = stringResource(R.string.onboarding_step_label, pagerState.currentPage + 1, PAGE_COUNT),
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			textAlign = TextAlign.Center,
			modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
		)
		Row(
			modifier = Modifier.fillMaxWidth().padding(20.dp),
			horizontalArrangement = Arrangement.spacedBy(20.dp),
		) {
			if (pagerState.currentPage != PAGE_WELCOME) {
				TonalActionButton(
					text = stringResource(R.string.onboarding_back_button),
					onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
					modifier = Modifier.weight(1f),
				)
			}
			FilledActionButton(
				text = if (pagerState.currentPage == LAST_PAGE_INDEX) {
					stringResource(R.string.onboarding_finish_button)
				} else {
					stringResource(R.string.onboarding_next_button)
				},
				onClick = {
					val page = pagerState.currentPage
					if (page >= LAST_PAGE_INDEX) {
						onFinish()
					} else {
						scope.launch { pagerState.animateScrollToPage(page + 1) }
					}
				},
				enabled = isPageRequirementMet(pagerState.currentPage),
				modifier = Modifier.weight(1f),
			)
		}
	}
}

@Composable
private fun OnboardingPageContainer(
	modifier: Modifier = Modifier,
	content: @Composable ColumnScope.() -> Unit,
) {
	Column(
		modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
		content = content,
	)
}

@Composable
private fun OnboardingWelcomePage() {
	OnboardingPageContainer {
		SettingsCard {
			OnboardingHeading(stringResource(R.string.onboarding_welcome_heading))
			CardBody(stringResource(R.string.onboarding_welcome_text))
		}
	}
}

@Composable
private fun OnboardingKeyboardPage(onEnableKeyboard: () -> Unit) {
	OnboardingPageContainer {
		SettingsCard {
			OnboardingHeading(stringResource(R.string.onboarding_keyboard_heading))
			CardBody(stringResource(R.string.onboarding_keyboard_text))
			FilledActionButton(stringResource(R.string.enable_keyboard_button), onEnableKeyboard)
		}
	}
}

@Composable
private fun OnboardingFolderPage(
	isImporting: Boolean,
	onChooseDir: () -> Unit,
	progressIndicator: @Composable () -> Unit,
) {
	OnboardingPageContainer {
		SettingsCard {
			OnboardingHeading(stringResource(R.string.onboarding_folder_heading))
			CardBody(stringResource(R.string.onboarding_folder_text))
			FilledActionButton(
				stringResource(R.string.update_sticker_pack_button),
				onChooseDir,
				enabled = !isImporting,
			)
			// Always present (not gated on isImporting): StickerImporter toggles its visibility
			// directly on the underlying View as it works, the same way MainActivity's does.
			progressIndicator()
		}
	}
}

@Composable
private fun OnboardingHeading(text: String) {
	Text(
		text = text,
		style = MaterialTheme.typography.headlineSmall,
		color = MaterialTheme.colorScheme.primary,
	)
}
