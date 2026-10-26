@file:OptIn(ExperimentalMaterial3Api::class)

package com.lukeneedham.stickerboard.share

import com.lukeneedham.stickerboard.settings.StickerBoardSettingsTheme
import androidx.compose.ui.tooling.preview.Preview
import com.lukeneedham.stickerboard.utilities.StickerNames
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.lukeneedham.stickerboard.settings.FilledActionButton
import com.lukeneedham.stickerboard.settings.SettingsCard
import com.lukeneedham.stickerboard.settings.SettingsTopBar
import com.lukeneedham.stickerboard.navigation.Route

/**
 * Wires [ShareImportPageContent] up with [ShareImportViewModel] - the nav-host destination reached from
 * [com.lukeneedham.stickerboard.navigation.Route.ShareImport]. Picking a pack calls [onPackChosen]
 * immediately, before anything is actually copied - the caller is expected to navigate to the
 * Stickers page and run the import there. Backing out before picking a pack calls [onCancel] instead.
 */
@Composable
fun ShareImportPage(
	imageUris: List<Uri>,
	onCancel: () -> Unit,
	onPackChosen: (packName: String) -> Unit,
	modifier: Modifier = Modifier,
	viewModel: ShareImportViewModel = viewModel(),
) {
	val uiState by viewModel.uiState.collectAsStateWithLifecycle()

	ShareImportPageContent(
		imageUris = imageUris,
		packNames = uiState.packNames,
		onBack = onCancel,
		onPackSelected = onPackChosen,
		modifier = modifier,
	)
}
