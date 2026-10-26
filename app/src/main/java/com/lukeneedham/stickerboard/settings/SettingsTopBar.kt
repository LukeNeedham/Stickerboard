@file:OptIn(ExperimentalMaterial3Api::class)

package com.lukeneedham.stickerboard.settings

import com.lukeneedham.stickerboard.theme.AppTheme
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.lukeneedham.stickerboard.R

/** The back-button top bar shared by every non-root page in the settings app. */
@Composable
fun SettingsTopBar(
	title: String,
	onBack: () -> Unit,
	modifier: Modifier = Modifier,
	actions: @Composable RowScope.() -> Unit = {},
) {
	TopAppBar(
		modifier = modifier,
		title = { Text(text = title, style = AppTheme.typography.titleLarge) },
		navigationIcon = {
			IconButton(onClick = onBack) {
				Icon(
					painter = painterResource(R.drawable.ic_back),
					contentDescription = stringResource(R.string.back_button),
					tint = AppTheme.colors.onBackground,
				)
			}
		},
		actions = actions,
		colors = TopAppBarDefaults.topAppBarColors(
			containerColor = AppTheme.colors.background,
			titleContentColor = AppTheme.colors.onBackground,
			navigationIconContentColor = AppTheme.colors.onBackground,
			actionIconContentColor = AppTheme.colors.onBackground,
		),
	)
}
