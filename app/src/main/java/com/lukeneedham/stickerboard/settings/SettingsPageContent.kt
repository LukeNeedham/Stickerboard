@file:OptIn(ExperimentalMaterial3Api::class)

package com.lukeneedham.stickerboard.settings

import com.lukeneedham.stickerboard.theme.AppButtonStyle
import com.lukeneedham.stickerboard.theme.AppButton
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.lukeneedham.stickerboard.theme.AppTheme
import androidx.compose.ui.tooling.preview.Preview
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.provider.Settings
import android.widget.EditText
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.lukeneedham.stickerboard.R

/**
 * The settings app's root page: the same enable-keyboard, try-it-out, view-stickers and
 * (debug-only) debug tools that MainActivity's old XML layout offered, rebuilt in Compose with a
 * modern Material 3 look. Choosing/reloading the sticker source directory lives entirely on the
 * Stickers page now, alongside its path, sticker/pack counts and last-refreshed time.
 */
@Composable
internal fun SettingsPageContent(
	state: SettingsUiState,
	onEnableKeyboard: () -> Unit,
	onTryItOutMediaReceived: (Uri) -> Unit,
	onViewStickers: () -> Unit,
	onOpenDebug: () -> Unit,
	modifier: Modifier = Modifier,
) {
	Scaffold(
		modifier = modifier,
		containerColor = AppTheme.current.colors.background,
		contentColor = AppTheme.current.colors.onBackground,
		topBar = {
			TopAppBar(
				title = {
					Text(
						text = stringResource(R.string.app_name),
						fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold,
					)
				},
				colors = TopAppBarDefaults.topAppBarColors(
					containerColor = AppTheme.current.colors.background,
					titleContentColor = AppTheme.current.colors.onBackground,
					navigationIconContentColor = AppTheme.current.colors.onBackground,
					actionIconContentColor = AppTheme.current.colors.onBackground,
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
			EnableKeyboardCard(state.keyboardEnabled, onEnableKeyboard)
			TryItOutCard(state.tryItOutLastMedia, onTryItOutMediaReceived)
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
		colors = CardDefaults.cardColors(containerColor = AppTheme.current.colors.surface, contentColor = AppTheme.current.colors.onSurface),
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
			colorFilter = ColorFilter.tint(AppTheme.current.colors.accent),
			modifier = Modifier.size(22.dp),
		)
		Spacer(Modifier.width(10.dp))
		Text(
			text = text,
			fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold,
			color = AppTheme.current.colors.onSurface,
		)
	}
}

@Composable
internal fun CardBody(text: String) {
	Text(
		text = text,
		fontSize = 14.sp, lineHeight = 20.sp,
		color = AppTheme.current.colors.textMuted,
	)
}

@Composable
private fun EnableKeyboardCard(keyboardEnabled: Boolean, onEnableKeyboard: () -> Unit) {
	SettingsCard {
		CardHeading(R.drawable.ic_settings, stringResource(R.string.enable_keyboard_heading))
		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.SpaceBetween,
			verticalAlignment = Alignment.CenterVertically,
		) {
			KeyboardStatusIndicator(keyboardEnabled)
			if (keyboardEnabled) {
				AppButton(
					text = stringResource(R.string.enable_keyboard_settings_button),
					onClick = onEnableKeyboard,
					style = AppButtonStyle.Secondary,
					compact = true,
				)
			}
		}
		if (!keyboardEnabled) {
			CardBody(stringResource(R.string.enable_keyboard_info))
			AppButton(stringResource(R.string.enable_keyboard_button), onEnableKeyboard, Modifier.fillMaxWidth())
		}
	}
}

/** Live readout of whether the StickerBoard keyboard is currently enabled in system settings. */
@Composable
internal fun KeyboardStatusIndicator(enabled: Boolean) {
	val color = if (enabled) AppTheme.current.colors.accent else AppTheme.current.colors.error
	val label = stringResource(
		if (enabled) {
			R.string.keyboard_status_enabled
		} else {
			R.string.keyboard_status_not_enabled
		},
	)
	Row(
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(8.dp),
	) {
		Box(Modifier.size(10.dp).background(color, CircleShape))
		Text(text = label, fontSize = 14.sp, lineHeight = 20.sp, color = color)
	}
}

@Composable
private fun TryItOutCard(lastMedia: Uri?, onMediaReceived: (Uri) -> Unit) {
	SettingsCard {
		CardHeading(R.drawable.ic_send, stringResource(R.string.try_it_out_heading))
		CardBody(stringResource(R.string.try_it_out_info))
		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.spacedBy(8.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			TryItOutInputField(onMediaReceived, modifier = Modifier.weight(1f))
			TryItOutMediaBox(lastMedia)
		}
	}
}

/** Shows the last sticker sent through [TryItOutInputField], or an empty placeholder before the
 * first one arrives. */
@Composable
private fun TryItOutMediaBox(media: Uri?, modifier: Modifier = Modifier) {
	val boxModifier = modifier.width(64.dp)
	if (media != null) {
		// An image's height can safely follow its width via aspect ratio - unlike text, it
		// won't get clipped when the user scales up their system font size.
		AsyncImage(
			model = media,
			contentDescription = stringResource(R.string.try_it_out_image_content_description),
			contentScale = ContentScale.Fit,
			modifier = boxModifier.aspectRatio(1f),
		)
	} else {
		Box(
			modifier = boxModifier
				.background(AppTheme.current.colors.placeholder)
				.padding(vertical = 16.dp),
			contentAlignment = Alignment.Center,
		) {
			Text(
				text = stringResource(R.string.try_it_out_placeholder_text),
				fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium,
				color = AppTheme.current.colors.textMuted,
				textAlign = TextAlign.Center,
				modifier = Modifier.padding(horizontal = 4.dp),
			)
		}
	}
}

@Composable
private fun TryItOutInputField(onMediaReceived: (Uri) -> Unit, modifier: Modifier = Modifier) {
	val hint = stringResource(R.string.try_it_out_hint)
	val currentOnMediaReceived by rememberUpdatedState(onMediaReceived)
	val textColor = AppTheme.current.colors.onSurface.toArgb()
	val hintColor = AppTheme.current.colors.textMuted.toArgb()
	val backgroundColor = AppTheme.current.colors.placeholder.toArgb()
	val paddingPx = with(LocalDensity.current) { 16.dp.roundToPx() }
	AndroidView(
		modifier = modifier.fillMaxWidth(),
		factory = { context ->
			EditText(context).apply {
				this.hint = hint
				// Sizes via its (sp-scaled) text plus padding, rather than a fixed dp height,
				// so it grows correctly when the user scales up their system font size.
				textSize = 16f // mirrors the keyboard's own body text size
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
private fun ViewStickersCard(onViewStickers: () -> Unit) {
	SettingsCard {
		CardHeading(R.drawable.ic_recent, stringResource(R.string.view_stickers_heading))
		CardBody(stringResource(R.string.view_stickers_info))
		AppButton(stringResource(R.string.view_stickers_button), onViewStickers, Modifier.fillMaxWidth())
	}
}

@Composable
private fun DebugCard(onOpenDebug: () -> Unit) {
	SettingsCard {
		CardHeading(R.drawable.ic_search, stringResource(R.string.debug_heading))
		CardBody(stringResource(R.string.debug_info))
		AppButton(stringResource(R.string.debug_open_button), onOpenDebug, Modifier.fillMaxWidth(), AppButtonStyle.Secondary)
	}
}

@Preview(showBackground = true)
@Composable
private fun Preview() {
	AppTheme {
		SettingsPageContent(
			state = SettingsUiState(keyboardEnabled = true),
			onEnableKeyboard = {},
			onTryItOutMediaReceived = {},
			onViewStickers = {},
			onOpenDebug = {},
		)
	}
}
