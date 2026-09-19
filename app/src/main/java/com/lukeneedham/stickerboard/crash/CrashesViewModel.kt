package com.lukeneedham.stickerboard.crash

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Owns [CrashesScreen]'s data - the list of every crash recorded in [CrashStore]. */
class CrashesViewModel(application: Application) : AndroidViewModel(application) {
	private val crashStore = CrashStore(application)

	private val _crashes = MutableStateFlow(crashStore.list())
	val crashes: StateFlow<List<CrashRecord>> = _crashes.asStateFlow()

	/** Re-scans crashes from disk, in case one was recorded since the last load. */
	fun refresh() {
		_crashes.value = crashStore.list()
	}
}
