@file:OptIn(ExperimentalFoundationApi::class)

package com.lukeneedham.stickerboard.onboarding

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
