@file:OptIn(ExperimentalMaterial3Api::class)

package com.lukeneedham.stickerboard.share

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.lukeneedham.stickerboard.R
import com.lukeneedham.stickerboard.prettifyPackName
import com.lukeneedham.stickerboard.settings.FilledActionButton
import com.lukeneedham.stickerboard.settings.SettingsCard
import com.lukeneedham.stickerboard.settings.SettingsTopBar
import kotlinx.coroutines.delay

/** How long the success message shows before the page auto-closes. */
private const val SUCCESS_AUTO_CLOSE_DELAY_MS = 900L

/**
 * Shown when another app shares image(s) into StickerBoard: a small preview of what's being
 * imported, then either a list of existing sticker packs to add them to, or a field to name a new
 * one. Picking a pack starts the copy immediately (mirroring the Stickers page's own "add photo"
 * action, which offers no separate confirm step either) - a spinner covers the import, then a brief
 * success message before the page closes itself.
 */
@Composable
fun ShareImportPage(
	imageUris: List<Uri>,
	packNames: List<String>,
	isImporting: Boolean,
	importedCount: Int?,
	onBack: () -> Unit,
	onPackSelected: (String) -> Unit,
	modifier: Modifier = Modifier,
) {
	var newPackName by remember { mutableStateOf("") }

	Scaffold(
		modifier = modifier,
		containerColor = MaterialTheme.colorScheme.background,
		topBar = {
			SettingsTopBar(title = stringResource(R.string.share_import_heading), onBack = onBack)
		},
	) { innerPadding ->
		Column(
			modifier = Modifier
				.padding(innerPadding)
				.fillMaxSize()
				.padding(horizontal = 20.dp, vertical = 8.dp),
			verticalArrangement = Arrangement.spacedBy(16.dp),
		) {
			SharePreviewRow(imageUris)

			when {
				importedCount != null -> Text(
					text = stringResource(R.string.share_import_success, importedCount),
					style = MaterialTheme.typography.bodyLarge,
					color = MaterialTheme.colorScheme.primary,
				)

				isImporting -> Box(
					modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
					contentAlignment = Alignment.Center,
				) {
					CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
				}

				else -> {
					Text(
						text = stringResource(R.string.share_import_prompt),
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)

					NewPackRow(
						packName = newPackName,
						onPackNameChange = { newPackName = it },
						onAdd = {
							val trimmed = newPackName.trim()
							if (trimmed.isNotEmpty()) onPackSelected(trimmed)
						},
					)

					if (packNames.isNotEmpty()) {
						LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
							items(packNames) { packName ->
								PackRow(packName = packName, onClick = { onPackSelected(packName) })
							}
						}
					}
				}
			}
		}
	}
}

/** A row of small thumbnails of the images being imported, capped so a large multi-share doesn't
 * blow out the layout - the exact count is shown as text once the import succeeds instead. */
@Composable
private fun SharePreviewRow(imageUris: List<Uri>, modifier: Modifier = Modifier) {
	Row(
		horizontalArrangement = Arrangement.spacedBy(8.dp),
		modifier = modifier.fillMaxWidth(),
	) {
		imageUris.take(4).forEach { uri ->
			AsyncImage(
				model = uri,
				contentDescription = null,
				modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)),
			)
		}
	}
}

@Composable
private fun PackRow(packName: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
	SettingsCard(modifier = modifier.clickable(onClick = onClick)) {
		Row(verticalAlignment = Alignment.CenterVertically) {
			Icon(
				painter = painterResource(R.drawable.ic_folder),
				contentDescription = null,
				tint = MaterialTheme.colorScheme.primary,
				modifier = Modifier.size(22.dp),
			)
			Text(
				text = prettifyPackName(packName),
				style = MaterialTheme.typography.titleMedium,
				color = MaterialTheme.colorScheme.onSurface,
				modifier = Modifier.padding(start = 10.dp),
			)
		}
	}
}

@Composable
private fun NewPackRow(
	packName: String,
	onPackNameChange: (String) -> Unit,
	onAdd: () -> Unit,
	modifier: Modifier = Modifier,
) {
	Row(
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(8.dp),
		modifier = modifier.fillMaxWidth(),
	) {
		OutlinedTextField(
			value = packName,
			onValueChange = onPackNameChange,
			label = { Text(stringResource(R.string.share_import_new_pack_hint)) },
			singleLine = true,
			modifier = Modifier.weight(1f),
		)
		FilledActionButton(
			text = stringResource(R.string.share_import_new_pack_button),
			onClick = onAdd,
			enabled = packName.isNotBlank(),
			modifier = Modifier.weight(0.6f),
		)
	}
}

/**
 * Wires [ShareImportPage] up with [ShareImportViewModel] - the nav-host destination reached from
 * [com.lukeneedham.stickerboard.navigation.Route.ShareImport]. A moment after a successful import,
 * hands off to [onImported] (the pack it landed in, and the last file added) so the caller can jump
 * to the Stickers page with it in view; backing out before picking a pack calls [onCancel] instead.
 */
@Composable
fun ShareImportRoute(
	imageUris: List<Uri>,
	onCancel: () -> Unit,
	onImported: (packName: String, fileName: String?) -> Unit,
	modifier: Modifier = Modifier,
	viewModel: ShareImportViewModel = viewModel(),
) {
	val uiState by viewModel.uiState.collectAsStateWithLifecycle()

	LaunchedEffect(uiState.result) {
		val result = uiState.result ?: return@LaunchedEffect
		delay(SUCCESS_AUTO_CLOSE_DELAY_MS)
		onImported(result.packName, result.lastFileName)
	}

	ShareImportPage(
		imageUris = imageUris,
		packNames = uiState.packNames,
		isImporting = uiState.isImporting,
		importedCount = uiState.result?.count,
		onBack = onCancel,
		onPackSelected = { packName -> viewModel.importInto(packName, imageUris) },
		modifier = modifier,
	)
}
