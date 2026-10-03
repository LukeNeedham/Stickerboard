package com.lukeneedham.stickerboard.utilities

import java.io.DataInputStream
import java.io.File
import java.io.RandomAccessFile

private val VIDEO_EXTENSIONS = setOf("mp4", "m4v", "webm", "mkv", "mov", "3gp")

/**
 * Whether [file] is a moving image: a video, or a GIF / animated WebP / APNG / AVIF with at least
 * two frames. The frame count comes from walking the container's headers (no pixels are decoded),
 * so a still image in an animation-capable format isn't reported as moving. Does file IO, so call
 * it off the main thread.
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

/**
 * An animated WebP is a VP8X file with the animation flag (bit 1) set that actually holds at least
 * two `ANMF` frame chunks - counted by walking the RIFF chunk list, without decoding any pixels.
 */
private fun isAnimatedWebp(file: File): Boolean {
	RandomAccessFile(file, "r").use { raf ->
		val header = ByteArray(12)
		raf.readFully(header)
		if (String(header, 0, 4) != "RIFF" || String(header, 8, 4) != "WEBP") return false
		var frames = 0
		val chunkHeader = ByteArray(8)
		while (raf.filePointer + 8 <= raf.length()) {
			raf.readFully(chunkHeader)
			val type = String(chunkHeader, 0, 4)
			val size = littleEndianInt(chunkHeader, 4).toLong() and 0xFFFFFFFFL
			if (type == "VP8X") {
				val flags = raf.readUnsignedByte()
				if (flags and 0x02 == 0) return false
				raf.seek(raf.filePointer + size - 1 + (size and 1))
				continue
			}
			if (type == "ANMF" && ++frames >= 2) return true
			raf.seek(raf.filePointer + size + (size and 1)) // chunks are padded to even sizes
		}
		return false
	}
}

private fun littleEndianInt(bytes: ByteArray, offset: Int): Int =
	(bytes[offset].toInt() and 0xFF) or
		((bytes[offset + 1].toInt() and 0xFF) shl 8) or
		((bytes[offset + 2].toInt() and 0xFF) shl 16) or
		((bytes[offset + 3].toInt() and 0xFF) shl 24)

/** An APNG has an `acTL` chunk before its first `IDAT`, whose `num_frames` field is at least 2. */
private fun isAnimatedPng(file: File): Boolean {
	DataInputStream(file.inputStream().buffered()).use { input ->
		input.skipBytes(8) // PNG signature
		while (true) {
			val length = input.readInt()
			val type = ByteArray(4).also { input.readFully(it) }.toString(Charsets.US_ASCII)
			when (type) {
				"acTL" -> return input.readInt() >= 2
				"IDAT", "IEND" -> return false
			}
			input.skipBytes(length + 4) // chunk data + CRC
		}
	}
}

/**
 * An animated AVIF declares the `avis` brand in its leading `ftyp` box; its frame count is the
 * sample count (`stsz`) of a track inside `moov`, found by walking the ISO-BMFF box tree.
 */
private fun isAnimatedAvif(file: File): Boolean {
	RandomAccessFile(file, "r").use { raf ->
		val ftyp = ByteArray(12)
		raf.readFully(ftyp)
		if (String(ftyp, 4, 4) != "ftyp" || String(ftyp, 8, 4) != "avis") return false
		raf.seek(0)
		return hasMultipleSamples(raf, raf.length())
	}
}

private fun RandomAccessFile.bigEndianBoxSize(header: ByteArray): Long =
	((header[0].toLong() and 0xFF) shl 24) or ((header[1].toLong() and 0xFF) shl 16) or
		((header[2].toLong() and 0xFF) shl 8) or (header[3].toLong() and 0xFF)

private val AVIF_CONTAINER_BOXES = setOf("moov", "trak", "mdia", "minf", "stbl")

private fun hasMultipleSamples(raf: RandomAccessFile, end: Long): Boolean {
	val header = ByteArray(8)
	while (raf.filePointer + 8 <= end) {
		val boxStart = raf.filePointer
		raf.readFully(header)
		var size = raf.bigEndianBoxSize(header)
		val type = String(header, 4, 4)
		if (size == 1L) size = raf.readLong() else if (size == 0L) size = end - boxStart
		if (size < 8) return false
		val boxEnd = boxStart + size
		when {
			type == "stsz" -> {
				raf.skipBytes(8) // version + flags, sample_size
				return raf.readInt() >= 2
			}
			type in AVIF_CONTAINER_BOXES -> if (hasMultipleSamples(raf, minOf(boxEnd, end))) return true
		}
		raf.seek(boxEnd)
	}
	return false
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
