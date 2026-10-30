package com.lukeneedham.stickerboard.gallery

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.viewModelScope
import com.lukeneedham.common.logger.Logger
import com.lukeneedham.stickerboard.R
import com.lukeneedham.stickerboard.data.AppPreferences
import com.lukeneedham.stickerboard.model.BoardItem
import com.lukeneedham.stickerboard.model.StickerPack
import com.lukeneedham.stickerboard.utilities.StickerImporter
import com.lukeneedham.stickerboard.utilities.LastRefreshedFormat
import com.lukeneedham.stickerboard.utilities.StickerFiles
import com.lukeneedham.stickerboard.utilities.StickerNames
import com.lukeneedham.stickerboard.utilities.Toaster
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.system.measureTimeMillis
import android.text.format.DateFormat as AndroidDateFormat

/** Everything [GalleryPageContent] needs to render, besides the fixed [GalleryViewModel.columns]
 * setting, which doesn't change within the page's lifetime. */
data class GalleryUiState(
	val items: List<BoardItem>? = null,
	val stickerDirDisplayName: String = "",
	val lastUpdateDate: String = "",
	val isRefreshing: Boolean = false,
	/** A sticker (or, if [scrollToFileName] is null, just a pack) to scroll the grid to as soon as
	 * [items] reflects it - e.g. one just added by [runPendingImport]. Cleared once consumed. */
	val scrollToPackName: String? = null,
	val scrollToFileName: String? = null,
	/** Stickers whose delete is currently in flight - [GalleryPageContent] overlays a loading
	 * indicator on their cell until they actually disappear from [items]. */
	val deletingStickers: Set<File> = emptySet(),
)
