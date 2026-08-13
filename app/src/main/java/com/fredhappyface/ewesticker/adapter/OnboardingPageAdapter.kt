package com.fredhappyface.ewesticker.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.recyclerview.widget.RecyclerView
import com.fredhappyface.ewesticker.R
import com.fredhappyface.ewesticker.utilities.OnboardingPageListener
import com.google.android.material.progressindicator.LinearProgressIndicator

private const val PAGE_WELCOME = 0
private const val PAGE_KEYBOARD = 1
private const val PAGE_FOLDER = 2
private const val PAGE_COUNT = 3

/**
 * Adapter backing the onboarding ViewPager2. There are always exactly three fixed pages: welcome,
 * enable keyboard, and choose sticker source directory.
 *
 * @property listener callbacks for the interactive buttons on the keyboard and folder pages
 */
class OnboardingPageAdapter(
	private val listener: OnboardingPageListener,
) : RecyclerView.Adapter<OnboardingPageAdapter.PageViewHolder>() {
	class PageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

	override fun getItemViewType(position: Int): Int = position

	override fun getItemCount(): Int = PAGE_COUNT

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
		val layoutId = when (viewType) {
			PAGE_KEYBOARD -> R.layout.onboarding_page_keyboard
			PAGE_FOLDER -> R.layout.onboarding_page_folder
			else -> R.layout.onboarding_page_welcome
		}
		val itemView = LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
		return PageViewHolder(itemView)
	}

	override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
		when (position) {
			PAGE_KEYBOARD -> {
				holder.itemView.findViewById<Button>(R.id.onboardingEnableKeyboardBtn)
					.setOnClickListener { listener.onEnableKeyboardClick() }
			}
			PAGE_FOLDER -> {
				val chooseDirButton = holder.itemView.findViewById<Button>(R.id.onboardingChooseDirBtn)
				val progressBar = holder.itemView
					.findViewById<LinearProgressIndicator>(R.id.onboardingProgressIndicator)
				chooseDirButton.setOnClickListener {
					listener.onChooseDirClick(chooseDirButton, progressBar)
				}
			}
		}
	}
}
