package com.lukeneedham.stickerboard.adapter

import android.os.Build
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.lukeneedham.stickerboard.R
import com.lukeneedham.stickerboard.model.BoardItem
import com.lukeneedham.stickerboard.utilities.StickerClickListener
import com.lukeneedham.stickerboard.view.StickerPackViewHolder
import com.lukeneedham.stickerboard.view.StickerSectionHeaderViewHolder
import java.io.File

private const val VIEW_TYPE_HEADER = 0
private const val VIEW_TYPE_STICKER = 1
private const val VIEW_TYPE_EMPTY_MESSAGE = 2

/**
 * Renders the unified, vertically-scrolling sticker board: every pack's stickers, one after
 * another, each preceded by a full-width [BoardItem.Header] row (and a full-width
 * [BoardItem.EmptyMessage] row instead, if the pack has no stickers). Meant to be used with a
 * GridLayoutManager whose SpanSizeLookup gives full-width rows the full span count.
 */
class StickerBoardAdapter(
	private var iconSize: Int,
	private var items: List<BoardItem>,
	private val listener: StickerClickListener,
	private val gestureDetector: GestureDetector,
	private val vibrate: Boolean,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

	/** Resize every sticker cell in place, e.g. in response to a pinch-to-zoom gesture. */
	fun updateIconSize(newIconSize: Int) {
		if (newIconSize == iconSize) return
		iconSize = newIconSize
		notifyDataSetChanged()
	}

	/** Replace the board contents in place (e.g. after the recently-used section changes). */
	fun updateItems(newItems: List<BoardItem>) {
		items = newItems
		notifyDataSetChanged()
	}

	/** Whether the item at [position] spans the full width - used by the grid's span lookup. */
	fun isFullWidth(position: Int): Boolean =
		when (items.getOrNull(position)) {
			is BoardItem.Header, is BoardItem.EmptyMessage -> true
			else -> false
		}

	override fun getItemViewType(position: Int): Int =
		when (items[position]) {
			is BoardItem.Header -> VIEW_TYPE_HEADER
			is BoardItem.EmptyMessage -> VIEW_TYPE_EMPTY_MESSAGE
			is BoardItem.Sticker -> VIEW_TYPE_STICKER
		}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
		when (viewType) {
			VIEW_TYPE_HEADER -> StickerSectionHeaderViewHolder(
				LayoutInflater.from(parent.context)
					.inflate(R.layout.sticker_section_header, parent, false),
			)
			VIEW_TYPE_EMPTY_MESSAGE -> StickerSectionHeaderViewHolder(
				LayoutInflater.from(parent.context)
					.inflate(R.layout.sticker_section_empty, parent, false),
			)
			else -> StickerPackViewHolder(
				LayoutInflater.from(parent.context)
					.inflate(R.layout.sticker_grid_item, parent, false),
			)
		}

	override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
		when (val item = items[position]) {
			is BoardItem.Header -> {
				(holder as StickerSectionHeaderViewHolder).title.text = item.displayName
			}
			is BoardItem.EmptyMessage -> {
				(holder as StickerSectionHeaderViewHolder).title.text = item.message
			}
			is BoardItem.Sticker -> {
				holder as StickerPackViewHolder
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
