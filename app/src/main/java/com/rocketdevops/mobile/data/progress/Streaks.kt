package com.rocketdevops.mobile.data.progress

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** One day for the week strip and the "Last 7 days" chart. */
data class DayActivity(
    val date: LocalDate,
    val label: String,     // "Mon"
    val count: Int,        // lessons, labs and quizzes finished that day
    val isToday: Boolean,
) {
    val isActive: Boolean get() = count > 0
    val dayOfMonth: Int get() = date.dayOfMonth
}

/**
 * Streak rules (spec screen 8):
 * - A day counts when at least one lesson, quiz or lab was finished.
 * - Days are LOCAL dates (the phone's time zone), so finishing at 11:50 pm counts for that day.
 * - The streak is the run of active days ending today. If nothing is done yet today,
 *   yesterday's streak is still alive; it resets only after a whole missed day.
 */
object Streaks {

    fun current(activeDays: Set<Long>, today: LocalDate): Int {
        val todayDay = today.toEpochDay()
        var day = if (todayDay in activeDays) todayDay else todayDay - 1
        var length = 0
        while (day in activeDays) {
            length++
            day--
        }
        return length
    }

    /** Monday to Sunday of the current week (ISO weeks start on Monday). */
    fun week(today: LocalDate, activity: Map<Long, Int>): List<DayActivity> {
        val monday = today.with(DayOfWeek.MONDAY)
        return (0L..6L).map { offset -> dayOf(monday.plusDays(offset), today, activity) }
    }

    /** The last `count` days ending today, oldest first. */
    fun lastDays(today: LocalDate, activity: Map<Long, Int>, count: Int = 7): List<DayActivity> =
        ((count - 1) downTo 0).map { back -> dayOf(today.minusDays(back.toLong()), today, activity) }

    private fun dayOf(date: LocalDate, today: LocalDate, activity: Map<Long, Int>) = DayActivity(
        date = date,
        label = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
        count = activity[date.toEpochDay()] ?: 0,
        isToday = date == today,
    )
}
