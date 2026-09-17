@file:OptIn(ExperimentalMaterial3Api::class)

package com.lukeneedham.stickerboard.settings

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.provider.Settings
import android.widget.EditText
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.preference.PreferenceManager
import coil.compose.AsyncImage
import com.lukeneedham.stickerboard.BuildConfig
import com.lukeneedham.stickerboard.R
import com.lukeneedham.stickerboard.utilities.StickerImporter
import com.lukeneedham.stickerboard.utilities.Toaster
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

/** Everything the settings screen needs to render - plain state, matching the rest of the app. */
data class SettingsUiState(
	val isImporting: Boolean,
	val tryItOutMedia: List<Uri> = emptyList(),
	val showDebugCard: Boolean = false,
)

/**
 * The settings app's root screen: the same enable-keyboard, try-it-out, update-sticker-pack,
 * view-stickers and (debug-only) debug tools that MainActivity's old XML layout offered, rebuilt
 * in Compose with a modern Material 3 look. The sticker source directory's path, sticker/pack
 * counts, last-refreshed time and reload action live on the Stickers page instead - this card is
 * only about choosing where stickers come from in the first place.
 */
@Composable
fun SettingsScreen(
	state: SettingsUiState,
	onEnableKeyboard: () -> Unit,
	onTryItOutMediaReceived: (Uri) -> Unit,
	onChooseDir: () -> Unit,
	onViewStickers: () -> Unit,
	onOpenDebug: () -> Unit,
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
			UpdateStickerPackCard(state.isImporting, onChooseDir)
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
private fun UpdateStickerPackCard(isImporting: Boolean, onChooseDir: () -> Unit) {
	SettingsCard {
		CardHeading(R.drawable.ic_folder, stringResource(R.string.update_sticker_pack_heading))
		CardBody(stringResource(R.string.update_sticker_pack_info))
		FilledActionButton(
			stringResource(R.string.update_sticker_pack_button),
			onChooseDir,
			enabled = !isImporting,
		)
		if (isImporting) {
			Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
				CircularProgressIndicator(
					modifier = Modifier.size(24.dp),
					strokeWidth = 2.dp,
					color = MaterialTheme.colorScheme.primary,
				)
			}
		}
	}
}

@Composable
internal fun InfoRow(label: String, value: String) {
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

/**
 * Wires [SettingsScreen] up with its real dependencies (prefs, the sticker importer, the
 * enable-keyboard/choose-dir system intents) - the nav-host destination that used to be
 * MainActivity itself.
 */
@Composable
fun SettingsRoute(
	onViewStickers: () -> Unit,
	onOpenDebug: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val context = LocalContext.current
	val scope = rememberCoroutineScope()
	val sharedPreferences = remember { PreferenceManager.getDefaultSharedPreferences(context) }
	// Only ever passed through to StickerImporter, which logs warnings on it as it works - never
	// toasted, so choosing a directory here never pops up any message of its own.
	val toaster = remember { Toaster() }

	var uiState by remember {
		mutableStateOf(
			SettingsUiState(
				isImporting = false,
				showDebugCard = BuildConfig.DEBUG,
			),
		)
	}

	// No toast on start/finish by design - progress is shown inline on the card itself (a spinner
	// while isImporting). The imported count, path and last-refreshed time now live on the
	// Stickers page instead of here.
	fun importStickers(stickerDirPath: String) {
		uiState = uiState.copy(isImporting = true)
		scope.launch(Dispatchers.IO) {
			val totalStickers = StickerImporter(context, toaster).importStickers(stickerDirPath)
			withContext(Dispatchers.Main) {
				sharedPreferences.edit().putInt("numStickersImported", totalStickers).apply()
				uiState = uiState.copy(isImporting = false)
			}
		}
	}

	val chooseDirResultLauncher = rememberLauncherForActivityResult(
		ActivityResultContracts.StartActivityForResult(),
	) { result ->
		if (result.resultCode == Activity.RESULT_OK) {
			val uri = result.data?.data
			val stickerDirPath = result.data?.data.toString()
			if (uri != null) {
				val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
				context.contentResolver.takePersistableUriPermission(uri, takeFlags)
			}
			sharedPreferences.edit()
				.putString("stickerDirPath", stickerDirPath)
				.putString("lastUpdateDate", Calendar.getInstance().time.toString())
				.putString("recentCache", "")
				.putString("compatCache", "")
				.apply()
			importStickers(stickerDirPath)
		}
	}

	SettingsScreen(
		state = uiState,
		onEnableKeyboard = { context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) },
		onTryItOutMediaReceived = { uri ->
			uiState = uiState.copy(tryItOutMedia = listOf(uri) + uiState.tryItOutMedia)
		},
		onChooseDir = {
			val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
				addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
				addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
			}
			chooseDirResultLauncher.launch(intent)
		},
		onViewStickers = onViewStickers,
		onOpenDebug = onOpenDebug,
		modifier = modifier,
	)
}
