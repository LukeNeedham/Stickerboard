@file:OptIn(ExperimentalFoundationApi::class)

package com.lukeneedham.stickerboard.onboarding

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lukeneedham.stickerboard.R
import com.lukeneedham.stickerboard.settings.CardBody
import com.lukeneedham.stickerboard.settings.FilledActionButton
import com.lukeneedham.stickerboard.settings.KeyboardStatusIndicator
import com.lukeneedham.stickerboard.settings.SettingsCard
import com.lukeneedham.stickerboard.settings.TonalActionButton
import kotlinx.coroutines.launch

private const val PAGE_WELCOME = 0
private const val PAGE_KEYBOARD = 1
private const val PAGE_FOLDER = 2
private const val PAGE_COUNT = 3
private const val LAST_PAGE_INDEX = PAGE_FOLDER

/** Everything the onboarding page needs to render - plain state, matching the rest of the app. */
data class OnboardingUiState(
	val keyboardEnabled: Boolean,
	val isImporting: Boolean,
	// Null until stickers have been successfully loaded at least once this session.
	val loadedStickerCount: Int?,
)

/**
 * Walks first-time users through what StickerBoard is, how to enable it as a keyboard, and how to
 * choose a sticker source directory, before handing off to MainActivity. A page's "next" step is
 * blocked - by disabling the next/finish button, and by simply not including the following page in
 * the pager yet - until that page's requirement (if any) is met.
 */
@Composable
fun OnboardingPage(
	state: OnboardingUiState,
	onEnableKeyboard: () -> Unit,
	onChooseDir: () -> Unit,
	onFinish: () -> Unit,
	modifier: Modifier = Modifier,
) {
	fun isPageRequirementMet(page: Int): Boolean = when (page) {
		PAGE_KEYBOARD -> state.keyboardEnabled
		PAGE_FOLDER -> state.loadedStickerCount != null
		else -> true
	}

	// The earliest page whose requirement isn't met yet, capped at the last page - there's nothing
	// beyond that left to protect. The pager's page count tracks this directly, so a page can't be
	// swiped to before its predecessor's requirement is met: there's simply no further page yet for
	// the gesture to move to.
	fun firstUnmetPage(): Int {
		for (page in PAGE_WELCOME until LAST_PAGE_INDEX) {
			if (!isPageRequirementMet(page)) return page
		}
		return LAST_PAGE_INDEX
	}

	val pagerState = rememberPagerState(pageCount = { firstUnmetPage() + 1 })
	val scope = rememberCoroutineScope()

	Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
		HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
			when (page) {
				PAGE_KEYBOARD -> OnboardingKeyboardPage(state.keyboardEnabled, onEnableKeyboard)
				PAGE_FOLDER -> OnboardingFolderPage(state.isImporting, state.loadedStickerCount, onChooseDir)
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
private fun OnboardingKeyboardPage(keyboardEnabled: Boolean, onEnableKeyboard: () -> Unit) {
	OnboardingPageContainer {
		SettingsCard {
			OnboardingHeading(stringResource(R.string.onboarding_keyboard_heading))
			CardBody(stringResource(R.string.onboarding_keyboard_text))
			FilledActionButton(stringResource(R.string.enable_keyboard_button), onEnableKeyboard)
			KeyboardStatusIndicator(keyboardEnabled)
		}
	}
}

@Composable
private fun OnboardingFolderPage(
	isImporting: Boolean,
	loadedStickerCount: Int?,
	onChooseDir: () -> Unit,
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
			if (isImporting) {
				Row(
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(8.dp),
				) {
					CircularProgressIndicator(
						modifier = Modifier.size(20.dp),
						strokeWidth = 2.dp,
						color = MaterialTheme.colorScheme.primary,
					)
					Text(
						text = stringResource(R.string.onboarding_folder_loading),
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
				}
			} else if (loadedStickerCount != null) {
				Text(
					text = stringResource(R.string.onboarding_folder_loaded, loadedStickerCount),
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.primary,
				)
			}
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

/**
 * Wires [OnboardingPage] up with [OnboardingViewModel] and the enable-keyboard/choose-dir system
 * intents - the nav-host destination that used to be OnboardingActivity. [onFinished] replaces
 * this destination with the settings page on the shared back stack so the user can never
 * swipe/back their way back into onboarding.
 */
@Composable
fun OnboardingRoute(
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

	OnboardingPage(
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
