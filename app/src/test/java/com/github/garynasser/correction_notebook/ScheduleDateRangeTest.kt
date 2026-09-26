package com.github.garynasser.correction_notebook

import com.github.garynasser.correction_notebook.data.model.home.ScheduleRange
import com.github.garynasser.correction_notebook.data.repository.scheduleDateRange
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleDateRangeTest {
    private val friday = LocalDate.of(2026, 7, 3)

    @Test
    fun weekAlwaysRunsMondayThroughSunday() {
        assertEquals(
            LocalDate.of(2026, 6, 29) to LocalDate.of(2026, 7, 5),
            scheduleDateRange(ScheduleRange.WEEK, friday)
        )
    }

    @Test
    fun dayAndTomorrowRemainRelativeToSelectedDate() {
        assertEquals(friday to friday, scheduleDateRange(ScheduleRange.TODAY, friday))
        assertEquals(friday.plusDays(1) to friday.plusDays(1), scheduleDateRange(ScheduleRange.TOMORROW, friday))
    }
}
