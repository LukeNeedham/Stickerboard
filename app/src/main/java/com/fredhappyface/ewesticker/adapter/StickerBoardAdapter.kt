package com.fredhappyface.ewesticker.adapter

import android.os.Build
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.fredhappyface.ewesticker.R
import com.fredhappyface.ewesticker.model.BoardItem
import com.fredhappyface.ewesticker.utilities.StickerClickListener
import com.fredhappyface.ewesticker.view.StickerPackViewHolder
import com.fredhappyface.ewesticker.view.StickerSectionHeaderViewHolder
import java.io.File

private const val VIEW_TYPE_HEADER = 0
private const val VIEW_TYPE_STICKER = 1

/**
 * Renders the unified, vertically-scrolling sticker board: every pack's stickers, one after
 * another, each preceded by a full-width [BoardItem.Header] row. Meant to be used with a
 * GridLayoutManager whose SpanSizeLookup gives header rows the full span count.
 */
class StickerBoardAdapter(
	private val iconSize: Int,
	private val items: List<BoardItem>,
	private val listener: StickerClickListener,
	private val gestureDetector: GestureDetector,
	private val vibrate: Boolean,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

	override fun getItemViewType(position: Int): Int =
		when (items[position]) {
			is BoardItem.Header -> VIEW_TYPE_HEADER
			is BoardItem.Sticker -> VIEW_TYPE_STICKER
		}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
		if (viewType == VIEW_TYPE_HEADER) {
			StickerSectionHeaderViewHolder(
				LayoutInflater.from(parent.context)
					.inflate(R.layout.sticker_section_header, parent, false),
			)
		} else {
			StickerPackViewHolder(
				LayoutInflater.from(parent.context)
					.inflate(R.layout.sticker_card, parent, false),
			)
		}

	override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
		when (val item = items[position]) {
			is BoardItem.Header -> {
				(holder as StickerSectionHeaderViewHolder).title.text = item.displayName
			}
			is BoardItem.Sticker -> {
				holder as StickerPackViewHolder
				holder.stickerThumbnail.isSelected = false
				holder.stickerThumbnail.load(item.file)
				holder.stickerThumbnail.layoutParams.height = iconSize
				holder.stickerThumbnail.layoutParams.width = iconSize
				holder.stickerThumbnail.tag = item.file
				holder.stickerThumbnail.setOnClickListener {
					val file = it.tag as File
					if (vibrate && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
						it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS)
					}
					listener.onStickerClicked(file)
				}
				holder.stickerThumbnail.setOnLongClickListener {
					listener.onStickerLongClicked(it.tag as File)
					return@setOnLongClickListener true
				}
				holder.stickerThumbnail.setOnTouchListener { _, event ->
					return@setOnTouchListener gestureDetector.onTouchEvent(event)
				}
			}
		}
	}

	override fun getItemCount(): Int = items.size
}
