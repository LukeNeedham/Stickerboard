package com.lukeneedham.stickerboard.crash

/**
 * A single persisted crash.
 *
 * @param id The crash file's name, used to look the crash back up from [CrashStore].
 * @param timestamp When the crash happened, in epoch millis.
 * @param stackTrace The full stack trace text of the throwable that crashed the app.
 */
data class CrashRecord(
	val id: String,
	val timestamp: Long,
	val stackTrace: String,
)
