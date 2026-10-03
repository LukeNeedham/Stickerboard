package com.lukeneedham.stickerboard.utilities

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.decode.BitmapFactoryDecoder
import coil.request.ImageRequest
import java.io.File

/**
 * Renders a sticker file. Every sticker render site should go through this rather than calling
 * AsyncImage directly on a sticker File, so stickers can never end up clipped to rounded corners
 * by accident - unlike incidental UI chrome, they're always shown with their natural square edges.
 *
 * With [animate] false, GIF/WebP stickers decode to a single static frame - used in the gallery
 * grid, where running dozens of animations at once makes scrolling janky.
 */
@Composable
fun StickerImage(
	file: File,
	contentDescription: String?,
	modifier: Modifier = Modifier,
	animate: Boolean = true,
) {
	val context = LocalContext.current
	val model = remember(file, animate) {
		val builder = ImageRequest.Builder(context).data(file)
		if (!animate && file.extension.lowercase() in STATIC_DECODABLE_EXTENSIONS) {
			builder.decoderFactory(BitmapFactoryDecoder.Factory())
		}
		builder.build()
	}
	AsyncImage(
		model = model,
		contentDescription = contentDescription,
		contentScale = ContentScale.Fit,
		modifier = modifier,
	)
}

private val STATIC_DECODABLE_EXTENSIONS = setOf("gif", "webp", "png", "jpg", "jpeg", "bmp")
