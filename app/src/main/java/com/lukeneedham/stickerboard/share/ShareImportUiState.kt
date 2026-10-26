package com.lukeneedham.stickerboard.share

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** Everything [ShareImportPageContent] needs to render: the existing packs to choose from. */
data class ShareImportUiState(val packNames: List<String> = emptyList())
