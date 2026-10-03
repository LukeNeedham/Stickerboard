package com.lukeneedham.stickerboard.utilities

import java.io.DataInputStream
import java.io.File

private val VIDEO_EXTENSIONS = setOf("mp4", "m4v", "webm", "mkv", "mov", "3gp")

/**
 * Whether [file] is a moving image: a multi-frame GIF, a video, or an animated WebP / APNG / AVIF. Containers
 * that can hold either kind are told apart by sniffing their header, so a plain still WebP or PNG
 * isn't reported as animated. Does file IO, so call it off the main thread.
 */
fun isAnimatedSticker(file: File): Boolean {
	val extension = file.extension.lowercase()
	return when {
		extension == "gif" -> runCatching { gifHasMultipleFrames(file) }.getOrDefault(false)
		extension in VIDEO_EXTENSIONS -> true
		extension == "webp" -> runCatching { isAnimatedWebp(file) }.getOrDefault(false)
		extension == "png" || extension == "apng" -> runCatching { isAnimatedPng(file) }.getOrDefault(false)
		extension == "avif" -> runCatching { isAnimatedAvif(file) }.getOrDefault(false)
		else -> false
	}
}

/** An animated WebP is a VP8X file with the animation flag (bit 1) set in its feature flags. */
private fun isAnimatedWebp(file: File): Boolean {
	val header = ByteArray(21)
	file.inputStream().use { if (it.read(header) < header.size) return false }
	val isWebp = String(header, 0, 4) == "RIFF" && String(header, 8, 4) == "WEBP"
	return isWebp && String(header, 12, 4) == "VP8X" && header[20].toInt() and 0x02 != 0
}

/** An APNG has an `acTL` chunk somewhere before its first `IDAT` chunk. */
private fun isAnimatedPng(file: File): Boolean {
	DataInputStream(file.inputStream().buffered()).use { input ->
		input.skipBytes(8) // PNG signature
		while (true) {
			val length = input.readInt()
			val type = ByteArray(4).also { input.readFully(it) }.toString(Charsets.US_ASCII)
			when (type) {
				"acTL" -> return true
				"IDAT", "IEND" -> return false
			}
			input.skipBytes(length + 4) // chunk data + CRC
		}
	}
}

/** An animated AVIF declares the `avis` brand in its leading `ftyp` box. */
private fun isAnimatedAvif(file: File): Boolean {
	val header = ByteArray(16)
	file.inputStream().use { if (it.read(header) < header.size) return false }
	return String(header, 4, 4) == "ftyp" && String(header, 8, 4) == "avis"
}

/**
 * Walks the GIF block structure, counting image descriptors, and stops as soon as it finds a second
 * frame - so a single-frame GIF isn't reported as animated, without decoding any pixel data.
 */
private fun gifHasMultipleFrames(file: File): Boolean {
	DataInputStream(file.inputStream().buffered()).use { input ->
		input.skipBytes(6) // "GIF87a" / "GIF89a"
		input.skipBytes(4) // logical screen width + height
		val screenFlags = input.readUnsignedByte()
		input.skipBytes(2) // background colour index + pixel aspect ratio
		if (screenFlags and 0x80 != 0) input.skipBytes(3 shl ((screenFlags and 0x07) + 1))

		fun skipSubBlocks() {
			while (true) {
				val size = input.readUnsignedByte()
				if (size == 0) return
				input.skipBytes(size)
			}
		}

		var frames = 0
		while (true) {
			when (input.readUnsignedByte()) {
				0x21 -> { // extension: label, then sub-blocks
					input.skipBytes(1)
					skipSubBlocks()
				}
				0x2C -> { // image descriptor
					if (++frames >= 2) return true
					input.skipBytes(8) // left, top, width, height
					val imageFlags = input.readUnsignedByte()
					if (imageFlags and 0x80 != 0) input.skipBytes(3 shl ((imageFlags and 0x07) + 1))
					input.skipBytes(1) // LZW minimum code size
					skipSubBlocks()
				}
				else -> return false // trailer (0x3B) or anything unexpected
			}
		}
	}
}
