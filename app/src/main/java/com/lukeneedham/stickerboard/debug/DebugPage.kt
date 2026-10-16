package com.lukeneedham.stickerboard.debug

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.lukeneedham.common.ui.DebugPage
import com.lukeneedham.stickerboard.BuildConfig
import com.lukeneedham.stickerboard.R
import com.lukeneedham.stickerboard.settings.CardBody
import com.lukeneedham.stickerboard.settings.CardHeading
import com.lukeneedham.stickerboard.settings.FilledActionButton
import com.lukeneedham.stickerboard.settings.SettingsCard

/** StickerBoard's own debug tools, shown below the shared debug page's content. */
@Composable
private fun ColumnScope.DebugExtraContent(onOpenOnboarding: () -> Unit) {
	SettingsCard {
		CardHeading(R.drawable.ic_settings, stringResource(R.string.debug_onboarding_heading))
		CardBody(stringResource(R.string.debug_onboarding_info))
		FilledActionButton(stringResource(R.string.debug_onboarding_button), onOpenOnboarding)
	}
}

/**
 * The shared debug page (animation speed, logs, crashes) plus the app's own tools. Guarded behind
 * [BuildConfig.DEBUG] in case it is ever reachable in a release build.
 */
@Composable
fun DebugRoute(
	onBack: () -> Unit,
	onOpenLogs: () -> Unit,
	onOpenCrashes: () -> Unit,
	onOpenOnboarding: () -> Unit,
	modifier: Modifier = Modifier,
) {
	if (!BuildConfig.DEBUG) {
		LaunchedEffect(Unit) { onBack() }
		return
	}
	DebugPage(
		onBack = onBack,
		onOpenLogs = onOpenLogs,
		onOpenCrashes = onOpenCrashes,
		modifier = modifier,
		extraContent = { DebugExtraContent(onOpenOnboarding) },
	)
}
