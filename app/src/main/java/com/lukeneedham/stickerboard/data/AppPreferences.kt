package com.lukeneedham.stickerboard.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * `android:allowBackup="false"` in the manifest already disables Android's cloud backup and
 * device-transfer for the whole app, so this file is never a candidate for either regardless of
 * its name - nothing extra to opt out of here.
 */
private val Context.prefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "prefs")

private object Keys {
	val STICKER_DIR_SIGNATURE = stringPreferencesKey("stickerDirSignature")
	val STICKER_DIR_PATH = stringPreferencesKey("stickerDirPath")
	val LAST_UPDATE_EPOCH_MILLIS = longPreferencesKey("lastUpdateEpochMillis")
	val RECENT_CACHE = stringPreferencesKey("recentCache")
	val COMPAT_CACHE = stringPreferencesKey("compatCache")
	val ACTIVE_PACK = stringPreferencesKey("activePack")
	val NUM_STICKERS_IMPORTED = intPreferencesKey("numStickersImported")
	val ONBOARDING_COMPLETE = booleanPreferencesKey("onboardingComplete")
	val ICONS_PER_X = intPreferencesKey("iconsPerX")
	val KEYBOARD_HEIGHT = intPreferencesKey("keyboardHeight")
}

/**
 * Wraps the app's [prefsDataStore] behind one small, typed API, replacing the raw SharedPreferences
 * + bare string keys every call site used to duplicate. Reads block on [runBlocking]: several call
 * sites (an InputMethodService callback, a ViewModel's construction-time initial state) aren't
 * suspend functions, and DataStore keeps its Preferences cached in memory after the first load -
 * like SharedPreferences' own first read, only the very first call per process actually waits on
 * disk. Writes never block the caller: they launch onto [writeScope] and complete in the
 * background, matching SharedPreferences.Editor's own `apply()`.
 */
class AppPreferences(context: Context) {
	private val appContext = context.applicationContext
	private val dataStore get() = appContext.prefsDataStore
	private val writeScope = CoroutineScope(Dispatchers.IO)

	var stickerDirSignature: String
		get() = read { it[Keys.STICKER_DIR_SIGNATURE] }.orEmpty()
		set(value) = write { it[Keys.STICKER_DIR_SIGNATURE] = value }

	/** The user-chosen external sticker source folder (a content:// tree URI), or null if none has
	 * been chosen yet. */
	var stickerDirPath: String?
		get() = read { it[Keys.STICKER_DIR_PATH] }
		set(value) = write { if (value != null) it[Keys.STICKER_DIR_PATH] = value }

	var lastUpdateEpochMillis: Long
		get() = read { it[Keys.LAST_UPDATE_EPOCH_MILLIS] } ?: -1L
		set(value) = write { it[Keys.LAST_UPDATE_EPOCH_MILLIS] = value }

	var recentCache: String
		get() = read { it[Keys.RECENT_CACHE] }.orEmpty()
		set(value) = write { it[Keys.RECENT_CACHE] = value }

	var compatCache: String
		get() = read { it[Keys.COMPAT_CACHE] }.orEmpty()
		set(value) = write { it[Keys.COMPAT_CACHE] = value }

	var activePack: String
		get() = read { it[Keys.ACTIVE_PACK] }.orEmpty()
		set(value) = write { it[Keys.ACTIVE_PACK] = value }

	var numStickersImported: Int
		get() = read { it[Keys.NUM_STICKERS_IMPORTED] } ?: 0
		set(value) = write { it[Keys.NUM_STICKERS_IMPORTED] = value }

	var onboardingComplete: Boolean
		get() = read { it[Keys.ONBOARDING_COMPLETE] } ?: false
		set(value) = write { it[Keys.ONBOARDING_COMPLETE] = value }

	var iconsPerX: Int
		get() = read { it[Keys.ICONS_PER_X] } ?: 4
		set(value) = write { it[Keys.ICONS_PER_X] = value }

	/** The height (in px) the keyboard was last dragged to, or [default] if none was saved yet. */
	fun keyboardHeightPx(default: Int): Int = read { it[Keys.KEYBOARD_HEIGHT] } ?: default

	fun saveKeyboardHeight(heightPx: Int) = write { it[Keys.KEYBOARD_HEIGHT] = heightPx }

	/** Persists the recent/compat caches and active pack together, in one write - everything that
	 * needs to survive to the next [android.view.inputmethod.InputConnection]. */
	fun persistSessionState(recentCache: String, compatCache: String, activePack: String) {
		write {
			it[Keys.RECENT_CACHE] = recentCache
			it[Keys.COMPAT_CACHE] = compatCache
			it[Keys.ACTIVE_PACK] = activePack
		}
	}

	private fun <T> read(block: (Preferences) -> T): T = runBlocking { block(dataStore.data.first()) }

	private fun write(block: (MutablePreferences) -> Unit) {
		writeScope.launch { dataStore.edit(block) }
	}
}
