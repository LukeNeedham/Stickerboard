package com.lukeneedham.stickerboard.crash

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/** Max number of crashes kept on disk - older crashes are deleted once this limit is exceeded. */
private const val MAX_CRASHES = 50

/**
 * Persists crashes to disk (as one file per crash under `filesDir/crashes`), and lists/reads them
 * back. Shared by the app's activities and the keyboard service, since both run in the same
 * process and can both crash.
 */
class CrashStore(context: Context) {
	private val dir = File(context.applicationContext.filesDir, "crashes")

	/** Persists [throwable] as a new crash record. Never throws. */
	fun record(throwable: Throwable) {
		try {
			dir.mkdirs()
			val timestamp = System.currentTimeMillis()
			val file = File(dir, "${timestamp}_${System.nanoTime()}.txt")
			file.writeText("$timestamp\n${stackTraceOf(throwable)}")
			trimToMax()
		} catch (_: Throwable) {
			// Persisting a crash must never itself throw - that would replace the original
			// crash with a new one part-way through handling it.
		}
	}

	/** All persisted crashes, most recent first. */
	fun list(): List<CrashRecord> =
		(dir.listFiles() ?: emptyArray())
			.mapNotNull(::parse)
			.sortedByDescending { it.timestamp }

	/** The crash with the given [id] (its file name), or null if it can't be found. */
	fun get(id: String): CrashRecord? = parse(File(dir, id))

	private fun parse(file: File): CrashRecord? {
		if (!file.isFile) return null
		val text = file.readText()
		val firstLineEnd = text.indexOf('\n')
		if (firstLineEnd == -1) return null
		val timestamp = text.substring(0, firstLineEnd).toLongOrNull() ?: return null
		val stackTrace = text.substring(firstLineEnd + 1)
		return CrashRecord(file.name, timestamp, stackTrace)
	}

	private fun trimToMax() {
		val files = dir.listFiles() ?: return
		val excess = files.size - MAX_CRASHES
		if (excess <= 0) return
		// File names start with the crash's timestamp, so sorting by name sorts oldest-first.
		files.sortedBy { it.name }.take(excess).forEach { it.delete() }
	}

	private fun stackTraceOf(throwable: Throwable): String {
		val writer = StringWriter()
		throwable.printStackTrace(PrintWriter(writer))
		return writer.toString()
	}
}
