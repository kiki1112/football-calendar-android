package com.footballcalendar.app

import com.footballcalendar.app.util.MatchTimeFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class MatchTimeFormatterTest {
    private val beijingZone = ZoneId.of("Asia/Shanghai")

    @Test
    fun earlyMorningMatchGroupsIntoPreviousWatchNightWhenEnabled() {
        // 2026-11-25T19:00:00Z == 2026-11-26 03:00 Asia/Shanghai
        val utc = "2026-11-25T19:00:00Z"

        assertTrue(MatchTimeFormatter.isEarlyMorning(utc, beijingZone))
        assertEquals("03:00", MatchTimeFormatter.formatKickoffTime(utc, beijingZone))
        assertEquals(
            LocalDate.of(2026, 11, 25),
            MatchTimeFormatter.displayDateFor(utc, beijingZone, watchNightGrouping = true),
        )
        assertEquals(
            LocalDate.of(2026, 11, 26),
            MatchTimeFormatter.displayDateFor(utc, beijingZone, watchNightGrouping = false),
        )
    }

    @Test
    fun eveningMatchDoesNotShiftDate() {
        // 2026-11-25T12:30:00Z == 2026-11-25 20:30 Asia/Shanghai
        val utc = "2026-11-25T12:30:00Z"

        assertFalse(MatchTimeFormatter.isEarlyMorning(utc, beijingZone))
        assertEquals("20:30", MatchTimeFormatter.formatKickoffTime(utc, beijingZone))
        assertEquals(
            LocalDate.of(2026, 11, 25),
            MatchTimeFormatter.displayDateFor(utc, beijingZone, watchNightGrouping = true),
        )
    }
}
