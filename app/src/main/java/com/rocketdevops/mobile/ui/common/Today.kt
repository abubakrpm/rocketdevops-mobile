package com.rocketdevops.mobile.ui.common

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.LocalDate

/**
 * Today's local date, re-emitted when the day changes.
 * Without it, a Home screen left open past midnight would still mark yesterday as "today"
 * in the week strip until some progress happened. Checks once a minute (cheap).
 */
fun todayFlow(
    clock: () -> LocalDate = { LocalDate.now() },
    checkEveryMillis: Long = 60_000L,
): Flow<LocalDate> = flow {
    while (true) {
        emit(clock())
        delay(checkEveryMillis)
    }
}.distinctUntilChanged()
