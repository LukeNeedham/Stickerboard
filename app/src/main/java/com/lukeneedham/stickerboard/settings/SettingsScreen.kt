@file:OptIn(ExperimentalMaterial3Api::class)

package com.lukeneedham.stickerboard.settings

import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.widget.EditText
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import coil.compose.AsyncImage
import com.lukeneedham.stickerboard.R

/** Everything the settings screen needs to render - plain state, matching the rest of the app. */
data class SettingsUiState(
	val stickerDirPath: String,
	val lastUpdateDate: String,
	val numStickersImported: Int,
	val isImporting: Boolean,
	val tryItOutMedia: List<Uri> = emptyList(),
	val showDebugCard: Boolean = false,
)

/**
 * The settings app's root screen: the same enable-keyboard, try-it-out, update-sticker-pack,
 * view-stickers and (debug-only) debug tools that MainActivity's old XML layout offered, rebuilt
 * in Compose with a modern Material 3 look. [progressIndicator] is supplied by the caller as a
 * slot so MainActivity can keep handing StickerImporter the real progress View it mutates directly.
 */
@Composable
fun SettingsScreen(
	state: SettingsUiState,
	onEnableKeyboard: () -> Unit,
	onTryItOutMediaReceived: (Uri) -> Unit,
	onChooseDir: () -> Unit,
	onReloadStickers: () -> Unit,
	onViewStickers: () -> Unit,
	onOpenDebug: () -> Unit,
	progressIndicator: @Composable () -> Unit,
	modifier: Modifier = Modifier,
) {
	Scaffold(
		modifier = modifier,
		containerColor = MaterialTheme.colorScheme.background,
		topBar = {
			TopAppBar(
				title = {
					Text(
						text = stringResource(R.string.app_name),
						fontWeight = FontWeight.Bold,
					)
				},
				colors = TopAppBarDefaults.topAppBarColors(
					containerColor = MaterialTheme.colorScheme.background,
					titleContentColor = MaterialTheme.colorScheme.onBackground,
				),
			)
		},
	) { innerPadding ->
		Column(
			modifier = Modifier
				.padding(innerPadding)
				.verticalScroll(rememberScrollState())
				.fillMaxWidth()
				.padding(horizontal = 20.dp, vertical = 8.dp),
			verticalArrangement = Arrangement.spacedBy(16.dp),
		) {
			EnableKeyboardCard(onEnableKeyboard)
			TryItOutCard(state.tryItOutMedia, onTryItOutMediaReceived)
			UpdateStickerPackCard(state, onChooseDir, onReloadStickers, progressIndicator)
			ViewStickersCard(onViewStickers)
			if (state.showDebugCard) {
				DebugCard(onOpenDebug)
			}
			Spacer(Modifier.height(8.dp))
		}
	}
}

/** Common modern card shell: soft rounded corners, a flat tonal surface, no harsh elevation. */
@Composable
internal fun SettingsCard(
	modifier: Modifier = Modifier,
	content: @Composable ColumnScope.() -> Unit,
) {
	Card(
		modifier = modifier.fillMaxWidth(),
		shape = RoundedCornerShape(24.dp),
		colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
		elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
	) {
		Column(
			modifier = Modifier.padding(20.dp),
			verticalArrangement = Arrangement.spacedBy(12.dp),
			content = content,
		)
	}
}

@Composable
internal fun CardHeading(iconRes: Int, text: String) {
	Row(verticalAlignment = Alignment.CenterVertically) {
		Image(
			painter = painterResource(iconRes),
			contentDescription = null,
			colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
			modifier = Modifier.size(22.dp),
		)
		Spacer(Modifier.width(10.dp))
		Text(
			text = text,
			style = MaterialTheme.typography.titleMedium,
			color = MaterialTheme.colorScheme.onSurface,
		)
	}
}

@Composable
internal fun CardBody(text: String) {
	Text(
		text = text,
		style = MaterialTheme.typography.bodyMedium,
		color = MaterialTheme.colorScheme.onSurfaceVariant,
	)
}

@Composable
private fun EnableKeyboardCard(onEnableKeyboard: () -> Unit) {
	SettingsCard {
		CardHeading(R.drawable.ic_settings, stringResource(R.string.enable_keyboard_heading))
		FilledActionButton(stringResource(R.string.enable_keyboard_button), onEnableKeyboard)
	}
}

@Composable
private fun TryItOutCard(media: List<Uri>, onMediaReceived: (Uri) -> Unit) {
	SettingsCard {
		CardHeading(R.drawable.ic_send, stringResource(R.string.try_it_out_heading))
		CardBody(stringResource(R.string.try_it_out_info))
		TryItOutInputField(onMediaReceived)
		if (media.isNotEmpty()) {
			LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
				items(media) { uri ->
					AsyncImage(
						model = uri,
						contentDescription = stringResource(R.string.try_it_out_image_content_description),
						modifier = Modifier
							.size(dimensionResource(R.dimen.try_it_out_image_size))
							.clip(RoundedCornerShape(12.dp)),
					)
				}
			}
		}
	}
}

@Composable
private fun TryItOutInputField(onMediaReceived: (Uri) -> Unit, modifier: Modifier = Modifier) {
	val hint = stringResource(R.string.try_it_out_hint)
	val currentOnMediaReceived by rememberUpdatedState(onMediaReceived)
	val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
	val hintColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
	val backgroundColor = MaterialTheme.colorScheme.surfaceVariant.toArgb()
	val paddingPx = with(LocalDensity.current) { dimensionResource(R.dimen.card_margin).roundToPx() }
	AndroidView(
		modifier = modifier
			.fillMaxWidth()
			.heightIn(min = 48.dp),
		factory = { context ->
			EditText(context).apply {
				this.hint = hint
				setTextColor(textColor)
				setHintTextColor(hintColor)
				setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
				background = GradientDrawable().apply {
					setColor(backgroundColor)
					cornerRadius = 16f * resources.displayMetrics.density
				}
				ViewCompat.setOnReceiveContentListener(this, arrayOf("image/*", "video/*")) { _, payload ->
					val split = payload.partition { it.uri != null }
					val mediaContent = split.first
					if (mediaContent != null) {
						for (i in 0 until mediaContent.clip.itemCount) {
							mediaContent.clip.getItemAt(i).uri?.let { uri -> currentOnMediaReceived(uri) }
						}
					}
					split.second
				}
			}
		},
	)
}

@Composable
private fun UpdateStickerPackCard(
	state: SettingsUiState,
	onChooseDir: () -> Unit,
	onReloadStickers: () -> Unit,
	progressIndicator: @Composable () -> Unit,
) {
	SettingsCard {
		CardHeading(R.drawable.ic_folder, stringResource(R.string.update_sticker_pack_heading))
		CardBody(stringResource(R.string.update_sticker_pack_info))
		InfoRow(stringResource(R.string.update_sticker_pack_info_path_lbl), state.stickerDirPath)
		InfoRow(stringResource(R.string.update_sticker_pack_info_date_lbl), state.lastUpdateDate)
		InfoRow(
			stringResource(R.string.update_sticker_pack_info_total_lbl),
			state.numStickersImported.toString(),
		)
		FilledActionButton(
			stringResource(R.string.update_sticker_pack_button),
			onChooseDir,
			enabled = !state.isImporting,
		)
		TonalActionButton(
			stringResource(R.string.reload_sticker_pack_button),
			onReloadStickers,
			enabled = !state.isImporting,
		)
		// Always present (not gated on isImporting): StickerImporter toggles its visibility
		// directly on the underlying View as it works, the same way it always has.
		progressIndicator()
	}
}

@Composable
private fun InfoRow(label: String, value: String) {
	Row {
		Text(
			text = label,
			style = MaterialTheme.typography.bodyMedium,
			fontWeight = FontWeight.SemiBold,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
		Text(
			text = value,
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurface,
		)
	}
}

@Composable
private fun ViewStickersCard(onViewStickers: () -> Unit) {
	SettingsCard {
		CardHeading(R.drawable.ic_recent, stringResource(R.string.view_stickers_heading))
		CardBody(stringResource(R.string.view_stickers_info))
		FilledActionButton(stringResource(R.string.view_stickers_button), onViewStickers)
	}
}

@Composable
private fun DebugCard(onOpenDebug: () -> Unit) {
	SettingsCard {
		CardHeading(R.drawable.ic_search, stringResource(R.string.debug_heading))
		CardBody(stringResource(R.string.debug_info))
		TonalActionButton(stringResource(R.string.debug_open_button), onOpenDebug)
	}
}

@Composable
internal fun FilledActionButton(
	text: String,
	onClick: () -> Unit,
	enabled: Boolean = true,
	modifier: Modifier = Modifier,
) {
	Button(
		onClick = onClick,
		enabled = enabled,
		shape = RoundedCornerShape(16.dp),
		modifier = modifier.fillMaxWidth(),
	) {
		Text(text)
	}
}

@Composable
internal fun TonalActionButton(
	text: String,
	onClick: () -> Unit,
	enabled: Boolean = true,
	modifier: Modifier = Modifier,
) {
	FilledTonalButton(
		onClick = onClick,
		enabled = enabled,
		shape = RoundedCornerShape(16.dp),
		modifier = modifier.fillMaxWidth(),
	) {
		Text(text)
	}
}
