package com.lukeneedham.stickerboard.utilities

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import android.text.format.DateFormat as AndroidDateFormat

object LastRefreshedFormat {
	/**
	 * Formats [epochMillis] as a short, always-local time - just hours and minutes, with the date
	 * prepended only if it isn't today, and the year only added to that date if it isn't this year.
	 * Uses [AndroidDateFormat.getBestDateTimePattern] so the result still respects the user's own
	 * locale (e.g. 12h vs 24h clock, day/month order) despite dropping seconds and the full date.
	 */
	fun format(epochMillis: Long): String {
		val locale = Locale.getDefault()
		val then = Calendar.getInstance().apply { timeInMillis = epochMillis }
		val now = Calendar.getInstance()

		val timePattern = AndroidDateFormat.getBestDateTimePattern(locale, "Hm")
		val timeText = SimpleDateFormat(timePattern, locale).format(Date(epochMillis))

		val sameDay = then.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
			then.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
		if (sameDay) return timeText

		val sameYear = then.get(Calendar.YEAR) == now.get(Calendar.YEAR)
		val dateSkeleton = if (sameYear) "MMMd" else "yMMMd"
		val datePattern = AndroidDateFormat.getBestDateTimePattern(locale, dateSkeleton)
		val dateText = SimpleDateFormat(datePattern, locale).format(Date(epochMillis))
		return "$dateText $timeText"
	}
}
