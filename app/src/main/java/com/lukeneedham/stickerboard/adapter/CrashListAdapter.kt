package com.lukeneedham.stickerboard.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.lukeneedham.stickerboard.R
import com.lukeneedham.stickerboard.crash.CrashRecord
import java.text.DateFormat
import java.util.Date

/** Shows each crash's timestamp and the top 3 lines of its stack trace. */
class CrashListAdapter(
	private var items: List<CrashRecord>,
	private val onClick: (CrashRecord) -> Unit,
) : RecyclerView.Adapter<CrashListAdapter.ViewHolder>() {
	private val dateFormat: DateFormat = DateFormat.getDateTimeInstance()

	class ViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
		val timestamp: TextView = itemView.findViewById(R.id.crashItemTimestamp)
		val stackTrace: TextView = itemView.findViewById(R.id.crashItemStackTrace)
	}

	fun updateItems(newItems: List<CrashRecord>) {
		items = newItems
		notifyDataSetChanged()
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
		val view =
			LayoutInflater.from(parent.context).inflate(R.layout.crash_list_item, parent, false)
		return ViewHolder(view)
	}

	override fun onBindViewHolder(holder: ViewHolder, position: Int) {
		val crash = items[position]
		holder.timestamp.text = dateFormat.format(Date(crash.timestamp))
		holder.stackTrace.text = crash.stackTrace.lineSequence().take(3).joinToString("\n")
		holder.itemView.setOnClickListener { onClick(crash) }
	}

	override fun getItemCount(): Int = items.size
}
