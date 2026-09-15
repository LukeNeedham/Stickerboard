package com.lukeneedham.stickerboard

import android.app.Application
import com.lukeneedham.stickerboard.crash.CrashHandler
import com.lukeneedham.stickerboard.utilities.startLogger

/**
 * Installs the global crash handler as early as possible - before any activity or the keyboard
 * service is created - so that a crash during their startup is still caught.
 */
class StickerBoardApplication : Application() {
	override fun onCreate() {
		super.onCreate()
		startLogger(filesDir)
		CrashHandler.install(this)
	}
}
