package com.lukeneedham.stickerboard.debug

import com.lukeneedham.stickerboard.theme.AppButton
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
import com.lukeneedham.stickerboard.settings.SettingsCard
import com.lukeneedham.stickerboard.theme.AppTheme
import com.lukeneedham.stickerboard.settings.SettingsTopBar

@Composable
internal fun AppDebugPageContent(
	onBack: () -> Unit,
	onOpenSdkDebug: () -> Unit,
	onOpenOnboarding: () -> Unit,
	modifier: Modifier = Modifier,
) {
	Scaffold(
		modifier = modifier,
		containerColor = AppTheme.current.colors.background,
		contentColor = AppTheme.current.colors.onBackground,
		topBar = { SettingsTopBar(title = stringResource(R.string.debug_heading), onBack = onBack) },
	) { innerPadding ->
		Column(
			modifier = Modifier
				.padding(innerPadding)
				.verticalScroll(rememberScrollState())
				.fillMaxWidth()
				.padding(horizontal = 20.dp, vertical = 8.dp),
			verticalArrangement = Arrangement.spacedBy(16.dp),
		) {
			SettingsCard {
				CardHeading(R.drawable.ic_settings, stringResource(R.string.debug_sdk_heading))
				CardBody(stringResource(R.string.debug_sdk_info))
				AppButton(stringResource(R.string.debug_sdk_button), onOpenSdkDebug, Modifier.fillMaxWidth())
			}
			SettingsCard {
				CardHeading(R.drawable.ic_settings, stringResource(R.string.debug_onboarding_heading))
				CardBody(stringResource(R.string.debug_onboarding_info))
				AppButton(stringResource(R.string.debug_onboarding_button), onOpenOnboarding, Modifier.fillMaxWidth())
			}
		}
	}
}

@Preview(showBackground = true)
@Composable
private fun Preview() {
	AppTheme {
		AppDebugPageContent(onBack = {}, onOpenSdkDebug = {}, onOpenOnboarding = {})
	}
}
