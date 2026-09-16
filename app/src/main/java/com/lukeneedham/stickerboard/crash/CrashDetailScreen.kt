package com.lukeneedham.stickerboard.crash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lukeneedham.stickerboard.R
import com.lukeneedham.stickerboard.settings.SettingsTopBar
import java.text.DateFormat
import java.util.Date

/** Shows a single crash's full, copyable stack trace. */
@Composable
fun CrashDetailScreen(crash: CrashRecord, onBack: () -> Unit, modifier: Modifier = Modifier) {
	Scaffold(
		modifier = modifier,
		containerColor = MaterialTheme.colorScheme.background,
		topBar = { SettingsTopBar(stringResource(R.string.crash_detail_heading), onBack) },
	) { innerPadding ->
		Column(
			modifier = Modifier
				.padding(innerPadding)
				.verticalScroll(rememberScrollState())
				.fillMaxWidth()
				.padding(20.dp),
			verticalArrangement = Arrangement.spacedBy(8.dp),
		) {
			Text(
				text = DateFormat.getDateTimeInstance().format(Date(crash.timestamp)),
				style = MaterialTheme.typography.bodyMedium,
				fontWeight = FontWeight.Bold,
				color = MaterialTheme.colorScheme.primary,
			)
			SelectionContainer {
				Text(
					text = crash.stackTrace,
					style = MaterialTheme.typography.bodyMedium,
					fontFamily = FontFamily.Monospace,
					color = MaterialTheme.colorScheme.onSurface,
				)
			}
		}
	}
}
