package com.lukeneedham.stickerboard.debug

import com.lukeneedham.stickerboard.theme.AppTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lukeneedham.stickerboard.BuildConfig
import com.lukeneedham.stickerboard.R
import com.lukeneedham.stickerboard.settings.CardBody
import com.lukeneedham.stickerboard.settings.CardHeading
import com.lukeneedham.stickerboard.settings.FilledActionButton
import com.lukeneedham.stickerboard.settings.SettingsCard
import com.lukeneedham.stickerboard.settings.SettingsTopBar

/**
 * StickerBoard's own debug page: its own tools, and a card that opens the shared debug library's page
 * (animation speed, logs, crashes). That page shows only the library's things and has no slot for
 * anything else, so app-specific tools go here. Guarded behind [BuildConfig.DEBUG] in case it is ever
 * reachable in a release build.
 */
@Composable
fun AppDebugPage(
	onBack: () -> Unit,
	onOpenSdkDebug: () -> Unit,
	onOpenOnboarding: () -> Unit,
	modifier: Modifier = Modifier,
) {
	if (!BuildConfig.DEBUG) {
		LaunchedEffect(Unit) { onBack() }
		return
	}
	AppDebugPageContent(onBack, onOpenSdkDebug, onOpenOnboarding, modifier)
}
