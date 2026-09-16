package com.lukeneedham.stickerboard

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.lukeneedham.stickerboard.adapter.CrashListAdapter
import com.lukeneedham.stickerboard.crash.CrashStore
import com.lukeneedham.stickerboard.utilities.applyStatusBarTopInset
import com.lukeneedham.stickerboard.utilities.startLogger

/** Shows every crash recorded by the app or the keyboard, most recent first. */
class CrashesActivity : AppCompatActivity() {
	private lateinit var crashStore: CrashStore
	private lateinit var recyclerView: RecyclerView
	private lateinit var emptyText: TextView
	private var adapter: CrashListAdapter? = null

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_crashes)
		startLogger(filesDir)

		this.crashStore = CrashStore(this)

		findViewById<View>(R.id.crashesAppBar).applyStatusBarTopInset()

		val toolbar = findViewById<MaterialToolbar>(R.id.crashesToolbar)
		val navIcon = getDrawable(R.drawable.ic_back)?.mutate()
		navIcon?.setTint(getColor(R.color.app_on_primary))
		toolbar.navigationIcon = navIcon
		toolbar.setNavigationOnClickListener { finish() }

		this.recyclerView = findViewById(R.id.crashesRecyclerView)
		this.emptyText = findViewById(R.id.crashesEmptyText)
		this.recyclerView.layoutManager = LinearLayoutManager(this)
	}

	/** Re-scan crashes in case one was just recorded while this activity wasn't in the foreground. */
	override fun onResume() {
		super.onResume()
		refreshCrashes()
	}

	private fun refreshCrashes() {
		val crashes = crashStore.list()
		emptyText.visibility = if (crashes.isEmpty()) View.VISIBLE else View.GONE
		recyclerView.visibility = if (crashes.isEmpty()) View.GONE else View.VISIBLE

		val existingAdapter = adapter
		if (existingAdapter != null) {
			existingAdapter.updateItems(crashes)
			return
		}

		val newAdapter = CrashListAdapter(crashes) { crash -> openCrashDetail(crash.id) }
		recyclerView.adapter = newAdapter
		adapter = newAdapter
	}

	private fun openCrashDetail(crashId: String) {
		val intent = Intent(this, CrashDetailActivity::class.java)
		intent.putExtra(CrashDetailActivity.EXTRA_CRASH_ID, crashId)
		startActivity(intent)
	}
}
