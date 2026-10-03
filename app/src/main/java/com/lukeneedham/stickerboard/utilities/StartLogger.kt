package com.lukeneedham.stickerboard.utilities

import com.elvishew.xlog.LogConfiguration
import com.elvishew.xlog.LogLevel
import com.elvishew.xlog.XLog
import com.elvishew.xlog.printer.AndroidPrinter
import com.elvishew.xlog.printer.file.FilePrinter
import com.elvishew.xlog.printer.file.naming.DateFileNameGenerator
import java.io.File

object StartLogger {
	/** Initialises XLog with Android and file printers, unless it has already been started. */
	fun start(filesDir: File) {
		try {
			XLog.i("startLogger if not already started")
		} catch (e: IllegalStateException) {
			val logConfig = LogConfiguration.Builder().logLevel(LogLevel.ALL).tag("StickerBoard").build()
			val androidPrinter =
				AndroidPrinter(true) // Printer that print the log using android.util.Log
			val filePrinter = FilePrinter.Builder(
				File(filesDir, "logs").path
			).fileNameGenerator(DateFileNameGenerator()).build()

			XLog.init(logConfig, androidPrinter, filePrinter)
		}
	}
}
