package com.lukeneedham.stickerboard.crash

import android.content.Context
import android.os.Process
import com.elvishew.xlog.XLog
import kotlin.system.exitProcess

/**
 * Installs a global [Thread.UncaughtExceptionHandler] that persists every uncaught exception - from
 * any thread, in either the app's activities or the keyboard service, since both run in this same
 * process - before handing it on to whatever handler was previously installed (normally the
 * platform's own, which shows the "app has stopped" behaviour and kills the process). This means a
 * crash is recorded without changing how the app actually crashes.
 */
object CrashHandler {
	fun install(context: Context) {
		val appContext = context.applicationContext
		val previousHandler = Thread.getDefaultUncaughtExceptionHandler()

		Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
			try {
				XLog.e("Uncaught exception on thread '${thread.name}'")
				XLog.e(throwable)
			} catch (_: Throwable) {
			}

			try {
				CrashStore(appContext).record(throwable)
			} catch (_: Throwable) {
			}

			if (previousHandler != null) {
				previousHandler.uncaughtException(thread, throwable)
			} else {
				Process.killProcess(Process.myPid())
				exitProcess(10)
			}
		}
	}
}
