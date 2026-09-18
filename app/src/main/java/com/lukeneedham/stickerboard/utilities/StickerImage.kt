package com.lukeneedham.stickerboard.utilities

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import java.io.File

/**
 * Renders a sticker file. Every sticker render site should go through this rather than calling
 * AsyncImage directly on a sticker File, so stickers can never end up clipped to rounded corners
 * by accident - unlike incidental UI chrome, they're always shown with their natural square edges.
 */
@Composable
fun StickerImage(
	file: File,
	contentDescription: String?,
	modifier: Modifier = Modifier,
) {
	AsyncImage(
		model = file,
		contentDescription = contentDescription,
		contentScale = ContentScale.Fit,
		modifier = modifier,
	)
}
