package com.lukeneedham.stickerboard.utilities

import java.io.File

/** Parsing and display formatting of sticker file names and pack directory names. */
object StickerNames {
	/**
	 * trimString
	 *
	 * for strings longer than 32 chars, trim to 32 chars and add ellipsis ...
	 *
	 *  @param str: String
	 *  @return String
	 */
	fun trim(str: String?): String {
		if (str == null) {
			return "null"
		}
		if (str.length > 32) {
			return str.substring(0, 32) + "..."
		}
		return str
	}

	/**
	 * Split a name (a sticker filename or pack directory name) into individual words on hyphens,
	 * underscores, and spaces - so e.g. "happy-cat_meme" becomes ["happy", "cat", "meme"]. Shared by
	 * every call site that needs a name's constituent terms, for both search and display.
	 */
	fun splitNameIntoTerms(name: String): List<String> {
		return name.split('-', '_', ' ').filter { it.isNotEmpty() }
	}

	/**
	 * prettifyPackName
	 *
	 * Turn a sticker pack's directory name into a readable section header, e.g. "cat_memes" ->
	 * "Cat Memes"
	 *
	 *  @param name: String
	 *  @return String
	 */
	fun prettifyPackName(name: String): String {
		return splitNameIntoTerms(name).joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
	}

	/**
	 * A sticker's search terms: its file name (without extension) and its pack's directory name,
	 * each split into individual words - so e.g. sticker "happy-cat_meme.png" in pack "funny_memes"
	 * is searchable by "happy", "cat", "meme", "funny", or "memes" individually, not just as a match
	 * against the whole file name.
	 */
	fun searchTerms(file: File): List<String> =
		splitNameIntoTerms(file.nameWithoutExtension) + splitNameIntoTerms(file.parentFile?.name ?: "")
}
