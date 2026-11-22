package com.rocketdevops.mobile.ui.common

import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class TodayFlowTest {

    @Test
    fun emitsOnlyWhenTheDayChanges() = runBlocking {
        val day1 = LocalDate.of(2026, 11, 22)
        val day2 = day1.plusDays(1)
        val clockReadings = ArrayDeque(listOf(day1, day1, day1, day2, day2))
        val days = todayFlow(clock = { clockReadings.removeFirstOrNull() ?: day2 }, checkEveryMillis = 1)
            .take(2)
            .toList()
        assertEquals(listOf(day1, day2), days) // three readings of day 1, but only one emission
    }
}
