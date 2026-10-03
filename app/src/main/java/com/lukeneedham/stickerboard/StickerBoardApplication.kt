package com.lukeneedham.stickerboard

import android.app.Application
import android.os.Build.VERSION.SDK_INT
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.SvgDecoder
import coil.decode.VideoFrameDecoder
import com.lukeneedham.stickerboard.crash.CrashHandler
import com.lukeneedham.stickerboard.utilities.startLogger

/**
 * Installs the global crash handler as early as possible - before any activity or the keyboard
 * service is created - so that a crash during their startup is still caught.
 *
 * Also provides the process-wide coil [ImageLoader], so animated images (GIF etc.) decode
 * correctly in every entry point (gallery activity, settings, keyboard service), regardless of
 * which one happens to start first.
 */
class StickerBoardApplication : Application(), ImageLoaderFactory {
	override fun onCreate() {
		super.onCreate()
		startLogger(filesDir)
		CrashHandler.install(this)
	}

	override fun newImageLoader(): ImageLoader =
		ImageLoader.Builder(this)
			.components {
				if (SDK_INT >= 28) {
					add(ImageDecoderDecoder.Factory())
				} else {
					add(GifDecoder.Factory())
				}
				add(VideoFrameDecoder.Factory())
				add(SvgDecoder.Factory())
			}
			.build()
}
