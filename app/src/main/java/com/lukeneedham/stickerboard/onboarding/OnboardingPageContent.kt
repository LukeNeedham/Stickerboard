

@file:OptIn(ExperimentalFoundationApi::class)

package com.lukeneedham.stickerboard.onboarding

import com.lukeneedham.stickerboard.theme.AppButtonStyle
import com.lukeneedham.stickerboard.theme.AppButton
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.lukeneedham.stickerboard.theme.AppTheme
import androidx.compose.ui.tooling.preview.Preview
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import com.lukeneedham.stickerboard.settings.KeyboardStatusIndicator
import com.lukeneedham.stickerboard.settings.SettingsCard
import kotlinx.coroutines.launch

private object OnboardingPageConstants {
	const val PAGE_WELCOME = 0

	const val PAGE_KEYBOARD = 1

	const val PAGE_FOLDER = 2

	const val PAGE_COUNT = 3

	const val LAST_PAGE_INDEX = PAGE_FOLDER
}

/**
 * Walks first-time users through what StickerBoard is, how to enable it as a keyboard, and how to
 * choose a sticker source directory, before handing off to MainActivity. A page's "next" step is
 * blocked - by disabling the next/finish button, and by simply not including the following page in
 * the pager yet - until that page's requirement (if any) is met.
 */
@Composable
internal fun OnboardingPageContent(
	state: OnboardingUiState,
	onEnableKeyboard: () -> Unit,
	onChooseDir: () -> Unit,
	onFinish: () -> Unit,
	modifier: Modifier = Modifier,
) {
	fun isPageRequirementMet(page: Int): Boolean = when (page) {
		OnboardingPageConstants.PAGE_KEYBOARD -> state.keyboardEnabled
		OnboardingPageConstants.PAGE_FOLDER -> state.loadedStickerCount != null
		else -> true
	}

	// The earliest page whose requirement isn't met yet, capped at the last page - there's nothing
	// beyond that left to protect. The pager's page count tracks this directly, so a page can't be
	// swiped to before its predecessor's requirement is met: there's simply no further page yet for
	// the gesture to move to.
	fun firstUnmetPage(): Int {
		for (page in OnboardingPageConstants.PAGE_WELCOME until OnboardingPageConstants.LAST_PAGE_INDEX) {
			if (!isPageRequirementMet(page)) return page
		}
		return OnboardingPageConstants.LAST_PAGE_INDEX
	}

	val pagerState = rememberPagerState(pageCount = { firstUnmetPage() + 1 })
	val scope = rememberCoroutineScope()

	Column(
		modifier
			.fillMaxSize()
			.background(AppTheme.current.colors.background)
			// The only page without a Scaffold/TopAppBar of its own to absorb system bar insets -
			// every other page gets this for free from Scaffold's default contentWindowInsets.
			.windowInsetsPadding(WindowInsets.systemBars),
	) {
		HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
			when (page) {
				OnboardingPageConstants.PAGE_KEYBOARD -> OnboardingKeyboardPage(state.keyboardEnabled, onEnableKeyboard)
				OnboardingPageConstants.PAGE_FOLDER -> OnboardingFolderPage(state.isImporting, state.loadedStickerCount, onChooseDir)
				else -> OnboardingWelcomePage()
			}
		}
		Text(
			text = stringResource(R.string.onboarding_step_label, pagerState.currentPage + 1, OnboardingPageConstants.PAGE_COUNT),
			fontSize = 14.sp, lineHeight = 20.sp,
			color = AppTheme.current.colors.textMuted,
			textAlign = TextAlign.Center,
			modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
		)
		Row(
			modifier = Modifier.fillMaxWidth().padding(20.dp),
			horizontalArrangement = Arrangement.spacedBy(20.dp),
		) {
			if (pagerState.currentPage != OnboardingPageConstants.PAGE_WELCOME) {
				AppButton(
					text = stringResource(R.string.onboarding_back_button),
					onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
					modifier = Modifier.weight(1f),
					style = AppButtonStyle.Secondary,
				)
			}
			AppButton(
				text = if (pagerState.currentPage == OnboardingPageConstants.LAST_PAGE_INDEX) {
					stringResource(R.string.onboarding_finish_button)
				} else {
					stringResource(R.string.onboarding_next_button)
				},
				onClick = {
					val page = pagerState.currentPage
					if (page >= OnboardingPageConstants.LAST_PAGE_INDEX) {
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
			CardBody(stringResource(R.string.enable_keyboard_info))
			AppButton(stringResource(R.string.enable_keyboard_button), onEnableKeyboard, Modifier.fillMaxWidth())
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
			AppButton(
				stringResource(R.string.update_sticker_pack_button),
				onChooseDir,
				Modifier.fillMaxWidth(),
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
						color = AppTheme.current.colors.accent,
					)
					Text(
						text = stringResource(R.string.onboarding_folder_loading),
						fontSize = 14.sp, lineHeight = 20.sp,
						color = AppTheme.current.colors.textMuted,
					)
				}
			} else if (loadedStickerCount != null) {
				Text(
					text = stringResource(R.string.onboarding_folder_loaded, loadedStickerCount),
					fontSize = 14.sp, lineHeight = 20.sp,
					color = AppTheme.current.colors.accent,
				)
			}
		}
	}
}

@Composable
private fun OnboardingHeading(text: String) {
	Text(
		text = text,
		fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold,
		color = AppTheme.current.colors.accent,
	)
}

@Preview(showBackground = true)
@Composable
private fun Preview() {
	AppTheme {
		OnboardingPageContent(
			state = OnboardingUiState(keyboardEnabled = false, isImporting = false, loadedStickerCount = null),
			onEnableKeyboard = {},
			onChooseDir = {},
			onFinish = {},
		)
	}
}
