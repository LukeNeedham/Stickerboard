package com.lukeneedham.stickerboard.crash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lukeneedham.stickerboard.R
import com.lukeneedham.stickerboard.settings.SettingsTopBar
import java.text.DateFormat
import java.util.Date

/** Shows every crash recorded by the app or the keyboard, most recent first. */
@Composable
fun CrashesScreen(
	crashes: List<CrashRecord>,
	onBack: () -> Unit,
	onCrashClick: (CrashRecord) -> Unit,
	modifier: Modifier = Modifier,
) {
	Scaffold(
		modifier = modifier,
		containerColor = MaterialTheme.colorScheme.background,
		topBar = { SettingsTopBar(stringResource(R.string.crashes_heading), onBack) },
	) { innerPadding ->
		if (crashes.isEmpty()) {
			Box(
				modifier = Modifier.padding(innerPadding).fillMaxSize(),
				contentAlignment = Alignment.Center,
			) {
				Text(
					text = stringResource(R.string.crashes_empty),
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					textAlign = TextAlign.Center,
					modifier = Modifier.padding(32.dp),
				)
			}
		} else {
			LazyColumn(
				modifier = Modifier.padding(innerPadding).fillMaxWidth(),
				contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
				verticalArrangement = Arrangement.spacedBy(12.dp),
			) {
				items(crashes, key = { it.id }) { crash ->
					CrashCard(crash, onClick = { onCrashClick(crash) })
				}
			}
		}
	}
}

@Composable
private fun CrashCard(crash: CrashRecord, onClick: () -> Unit) {
	val dateFormat = remember { DateFormat.getDateTimeInstance() }
	Card(
		onClick = onClick,
		shape = RoundedCornerShape(24.dp),
		colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
		elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
		modifier = Modifier.fillMaxWidth(),
	) {
		Column(
			modifier = Modifier.padding(20.dp),
			verticalArrangement = Arrangement.spacedBy(4.dp),
		) {
			Text(
				text = dateFormat.format(Date(crash.timestamp)),
				style = MaterialTheme.typography.bodyMedium,
				fontWeight = FontWeight.Bold,
				color = MaterialTheme.colorScheme.primary,
			)
			Text(
				text = crash.stackTrace.lineSequence().take(3).joinToString("\n"),
				style = MaterialTheme.typography.bodyMedium,
				fontFamily = FontFamily.Monospace,
				maxLines = 3,
				overflow = TextOverflow.Ellipsis,
				color = MaterialTheme.colorScheme.onSurface,
			)
		}
	}
}
