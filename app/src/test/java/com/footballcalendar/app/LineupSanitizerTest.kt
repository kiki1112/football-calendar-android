package com.footballcalendar.app

import com.footballcalendar.app.model.LineupPlayerItem
import com.footballcalendar.app.model.MatchLineupSide
import com.footballcalendar.app.util.LineupSanitizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LineupSanitizerTest {
    @Test
    fun duplicatePlayerIdsAreDedupedAndUnique11RenderWithoutRowDuplication() {
        // Simulates dirty API lineup containing Shonan Bellmare / Ventforet Kofu duplicate IDs
        // (#37, #15, #3, #32 repeated across overlapping DEF/MID "DM" tags).
        val dirtyStarters = listOf(
            LineupPlayerItem(name = "GK One", zhName = "门将一", number = 1, pos = "GK", playerId = "p-1"),
            LineupPlayerItem(name = "Back Three", zhName = "三号后卫", number = 3, pos = "CB", playerId = "p-3"),
            LineupPlayerItem(name = "Back Fifteen", zhName = "十五号后卫", number = 15, pos = "LB", playerId = "p-15"),
            LineupPlayerItem(name = "Back ThirtyTwo", zhName = "三十二号", number = 32, pos = "RB", playerId = "p-32"),
            LineupPlayerItem(name = "Wing ThirtySeven", zhName = "三十七号", number = 37, pos = "DM", playerId = "p-37"),
            // Dirty duplicates injected by feed:
            LineupPlayerItem(name = "Wing ThirtySeven", zhName = "三十七号", number = 37, pos = "DM", playerId = "p-37"),
            LineupPlayerItem(name = "Back Fifteen", zhName = "十五号后卫", number = 15, pos = "LB", playerId = "p-15"),
            LineupPlayerItem(name = "Mid Six", zhName = "六号中场", number = 6, pos = "CDM", playerId = "p-6"),
            LineupPlayerItem(name = "Mid Eight", zhName = "八号中场", number = 8, pos = "CM", playerId = "p-8"),
            LineupPlayerItem(name = "Mid Ten", zhName = "十号前腰", number = 10, pos = "CAM", playerId = "p-10"),
            LineupPlayerItem(name = "Fwd Nine", zhName = "九号前锋", number = 9, pos = "ST", playerId = "p-9"),
            LineupPlayerItem(name = "Fwd Eleven", zhName = "十一号前锋", number = 11, pos = "LW", playerId = "p-11"),
            LineupPlayerItem(name = "Fwd Seven", zhName = "七号前锋", number = 7, pos = "RW", playerId = "p-7"),
        )

        val deduped = LineupSanitizer.dedupeStarters(dirtyStarters)
        assertEquals(11, deduped.size)
        assertTrue(LineupSanitizer.canRenderPitch(dirtyStarters))

        val rows = LineupSanitizer.buildPitchFormationRows(
            starters = dirtyStarters,
            formation = "", // Force position-bucket fallback where "DM"/"CDM" previously duplicated!
            reverseRows = false,
        )
        val flattenedIds = rows.flatten().map { it.effectivePlayerId }
        assertEquals(11, flattenedIds.size)
        assertEquals(11, flattenedIds.distinct().size)
    }

    @Test
    fun starterCountOtherThanElevenAutomaticallyDegradesPitchRendering() {
        val nineUniqueWithDuplicates = (1..9).map { idx ->
            LineupPlayerItem(
                name = "Player $idx",
                zhName = "球员$idx",
                number = idx,
                pos = if (idx == 1) "GK" else "CM",
                playerId = "u-$idx",
            )
        } + listOf(
            LineupPlayerItem(name = "Player 3", zhName = "球员3", number = 3, pos = "CM", playerId = "u-3"),
            LineupPlayerItem(name = "Player 5", zhName = "球员5", number = 5, pos = "CM", playerId = "u-5"),
        )

        // Raw list had 11 entries, but dedupe leaves only 9 unique players -> MUST degrade!
        assertEquals(11, nineUniqueWithDuplicates.size)
        assertFalse(LineupSanitizer.canRenderPitch(nineUniqueWithDuplicates))
        assertTrue(LineupSanitizer.buildPitchFormationRows(nineUniqueWithDuplicates, "4-4-2", false).isEmpty())

        val validEleven = (1..11).map { idx ->
            LineupPlayerItem(name = "Valid $idx", zhName = "合法$idx", number = idx, pos = "CM", playerId = "v-$idx")
        }
        val homeSide = MatchLineupSide("Shonan Bellmare", "湘南丽海", "4-4-2", "", "", validEleven, emptyList())
        val awayDirtySide = MatchLineupSide("Ventforet Kofu", "甲府风林", "4-4-2", "", "", nineUniqueWithDuplicates, emptyList())
        assertFalse(LineupSanitizer.canRenderMatchPitch(homeSide, awayDirtySide))
    }
}
