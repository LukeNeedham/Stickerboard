package com.lukeneedham.stickerboard

import android.net.Uri
import androidx.compose.runtime.Composable
import com.lukeneedham.stickerboard.keyboard.AppTheme
import com.lukeneedham.stickerboard.settings.ProvideAppDebugSettings
import com.lukeneedham.stickerboard.settings.StickerBoardSettingsTheme

/**
 * The one composable [MainActivity] calls: the theme and providers the UI sits in, then [AppNavigation]. Keep it wrapped
 * in [ProvideAppDebugSettings] so a debug setting added to the library later reaches every screen.
 *
 * @param sharedImageUris the images another app shared into StickerBoard, if [MainActivity] was opened by Share.
 * @param onCancelShareImport called when the user backs out of picking a pack for the shared images.
 */
@Composable
fun Root(
	sharedImageUris: List<Uri>,
	onCancelShareImport: () -> Unit,
) {
	AppTheme {
		StickerBoardSettingsTheme {
			ProvideAppDebugSettings {
				AppNavigation(
					sharedImageUris = sharedImageUris,
					onCancelShareImport = onCancelShareImport,
				)
			}
		}
	}
}
