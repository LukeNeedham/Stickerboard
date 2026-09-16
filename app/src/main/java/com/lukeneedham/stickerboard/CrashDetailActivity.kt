package com.lukeneedham.stickerboard

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.lukeneedham.stickerboard.crash.CrashStore
import com.lukeneedham.stickerboard.utilities.applyStatusBarTopInset
import com.lukeneedham.stickerboard.utilities.startLogger
import java.text.DateFormat
import java.util.Date

/** Shows a single crash's full, copyable stack trace. */
class CrashDetailActivity : AppCompatActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_crash_detail)
		startLogger(filesDir)

		findViewById<android.view.View>(R.id.crashDetailAppBar).applyStatusBarTopInset()

		val toolbar = findViewById<MaterialToolbar>(R.id.crashDetailToolbar)
		val navIcon = getDrawable(R.drawable.ic_back)?.mutate()
		navIcon?.setTint(getColor(R.color.app_on_primary))
		toolbar.navigationIcon = navIcon
		toolbar.setNavigationOnClickListener { finish() }

		val crashId = intent.getStringExtra(EXTRA_CRASH_ID)
		val crash = crashId?.let { CrashStore(this).get(it) }

		if (crash == null) {
			finish()
			return
		}

		findViewById<TextView>(R.id.crashDetailTimestamp).text =
			DateFormat.getDateTimeInstance().format(Date(crash.timestamp))
		findViewById<TextView>(R.id.crashDetailStackTrace).text = crash.stackTrace
	}

	companion object {
		const val EXTRA_CRASH_ID = "crash_id"
	}
}
