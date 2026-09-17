package com.lukeneedham.stickerboard.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lukeneedham.stickerboard.BuildConfig
import com.lukeneedham.stickerboard.R
import com.lukeneedham.stickerboard.settings.CardBody
import com.lukeneedham.stickerboard.settings.CardHeading
import com.lukeneedham.stickerboard.settings.FilledActionButton
import com.lukeneedham.stickerboard.settings.SettingsCard
import com.lukeneedham.stickerboard.settings.SettingsTopBar

/** Debug-only tools screen, reachable from the settings screen only in debug builds. */
@Composable
fun DebugScreen(onBack: () -> Unit, onOpenCrashes: () -> Unit, modifier: Modifier = Modifier) {
	Scaffold(
		modifier = modifier,
		containerColor = MaterialTheme.colorScheme.background,
		topBar = { SettingsTopBar(stringResource(R.string.debug_heading), onBack) },
	) { innerPadding ->
		Column(
			modifier = Modifier
				.padding(innerPadding)
				.fillMaxWidth()
				.padding(horizontal = 20.dp, vertical = 8.dp),
			verticalArrangement = Arrangement.spacedBy(16.dp),
		) {
			SettingsCard {
				CardHeading(R.drawable.ic_search, stringResource(R.string.debug_crashes_heading))
				CardBody(stringResource(R.string.debug_crashes_info))
				FilledActionButton(stringResource(R.string.debug_crashes_button), onOpenCrashes)
			}
		}
	}
}

/**
 * Guards [DebugScreen] behind [BuildConfig.DEBUG] - the nav-host destination that used to be
 * DebugActivity, which bailed out the same way in case it was ever reachable in a release build.
 */
@Composable
fun DebugRoute(onBack: () -> Unit, onOpenCrashes: () -> Unit, modifier: Modifier = Modifier) {
	if (!BuildConfig.DEBUG) {
		LaunchedEffect(Unit) { onBack() }
		return
	}
	DebugScreen(onBack = onBack, onOpenCrashes = onOpenCrashes, modifier = modifier)
}
