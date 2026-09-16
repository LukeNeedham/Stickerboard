@file:OptIn(ExperimentalMaterial3Api::class)

package com.lukeneedham.stickerboard.settings

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.lukeneedham.stickerboard.R

/** The back-button top bar shared by every non-root screen in the settings app. */
@Composable
fun SettingsTopBar(
	title: String,
	onBack: () -> Unit,
	modifier: Modifier = Modifier,
	actions: @Composable RowScope.() -> Unit = {},
) {
	TopAppBar(
		modifier = modifier,
		title = { Text(text = title, fontWeight = FontWeight.Bold) },
		navigationIcon = {
			IconButton(onClick = onBack) {
				Icon(
					painter = painterResource(R.drawable.ic_back),
					contentDescription = stringResource(R.string.back_button),
				)
			}
		},
		actions = actions,
		colors = TopAppBarDefaults.topAppBarColors(
			containerColor = MaterialTheme.colorScheme.background,
			titleContentColor = MaterialTheme.colorScheme.onBackground,
		),
	)
}
