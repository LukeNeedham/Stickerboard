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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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

/**
 * Looks up [crashId] via [CrashStore] and shows it - the nav-host destination that used to be
 * CrashDetailActivity. Bails back out immediately if the crash can't be found (e.g. it was
 * already trimmed from disk), the same way CrashDetailActivity did.
 */
@Composable
fun CrashDetailRoute(crashId: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
	val context = LocalContext.current
	val crash = remember(crashId) { CrashStore(context).get(crashId) }
	if (crash == null) {
		LaunchedEffect(Unit) { onBack() }
		return
	}
	CrashDetailScreen(crash = crash, onBack = onBack, modifier = modifier)
}
