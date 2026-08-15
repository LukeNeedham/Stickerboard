package com.lukeneedham.stickerboard.view

import android.view.View
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.lukeneedham.stickerboard.R

class StickerPackViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
	val stickerThumbnail: ImageView = itemView.findViewById(R.id.stickerButton)
}
