package com.lukeneedham.stickerboard.crash

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

/** Looks up [crashId] via [CrashStore] - the single crash [CrashDetailPage] shows, or null if it
 * can't be found (e.g. it was already trimmed from disk). */
class CrashDetailViewModel(
	application: Application,
	crashId: String,
) : AndroidViewModel(application) {
	val crash: CrashRecord? = CrashStore(application).get(crashId)

	companion object {
		fun factory(application: Application, crashId: String) = viewModelFactory {
			initializer { CrashDetailViewModel(application, crashId) }
		}
	}
}
