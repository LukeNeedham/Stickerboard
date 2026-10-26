@file:OptIn(ExperimentalFoundationApi::class)

package com.lukeneedham.stickerboard.onboarding

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

/** Everything the onboarding page needs to render - plain state, matching the rest of the app. */
data class OnboardingUiState(
	val keyboardEnabled: Boolean,
	val isImporting: Boolean,
	// Null until stickers have been successfully loaded at least once this session.
	val loadedStickerCount: Int?,
)
