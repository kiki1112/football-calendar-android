package com.footballcalendar.app.util

import com.footballcalendar.app.model.LineupPlayerItem
import com.footballcalendar.app.model.MatchLineupSide

/**
 * Pure sanitation and tactical pitch layout engine for starting XIs.
 * Enforces `distinctBy { it.effectivePlayerId }` and requires exactly 11 unique starters
 * before allowing pitch rendering (degrading to a clean text list otherwise).
 */
object LineupSanitizer {
    const val REQUIRED_STARTERS_COUNT = 11

    fun dedupeStarters(starters: List<LineupPlayerItem>): List<LineupPlayerItem> =
        starters.distinctBy { it.effectivePlayerId }

    fun canRenderPitch(starters: List<LineupPlayerItem>): Boolean =
        dedupeStarters(starters).size == REQUIRED_STARTERS_COUNT

    fun canRenderMatchPitch(homeSide: MatchLineupSide, awaySide: MatchLineupSide?): Boolean {
        if (!canRenderPitch(homeSide.startXI)) return false
        if (awaySide != null && !canRenderPitch(awaySide.startXI)) return false
        return true
    }

    fun buildPitchFormationRows(
        starters: List<LineupPlayerItem>,
        formation: String,
        reverseRows: Boolean,
    ): List<List<LineupPlayerItem>> {
        val deduped = dedupeStarters(starters)
        if (deduped.size != REQUIRED_STARTERS_COUNT) return emptyList()

        val rows = mutableListOf<List<LineupPlayerItem>>()
        val formParts = formation.split("-").mapNotNull { it.trim().toIntOrNull() }.filter { it > 0 }
        if (formParts.sum() == deduped.size - 1) {
            rows += listOf(deduped[0])
            var cursor = 1
            for (cnt in formParts) {
                val end = (cursor + cnt).coerceAtMost(deduped.size)
                if (cursor < end) {
                    rows += deduped.subList(cursor, end)
                }
                cursor = end
            }
        } else {
            val unassigned = deduped.toMutableList()
            val gks = unassigned.filter {
                val p = it.pos.trim().uppercase()
                p == "G" || p == "GK" || p.contains("GOAL")
            }.ifEmpty { listOf(unassigned.first()) }
            unassigned.removeAll(gks.toSet())

            val defs = unassigned.filter { isDefenderPos(it.pos) }
            unassigned.removeAll(defs.toSet())

            val mids = unassigned.filter { isMidfielderPos(it.pos) }
            unassigned.removeAll(mids.toSet())

            val fwds = unassigned.toList()
            unassigned.clear()

            rows += gks
            if (defs.isNotEmpty()) rows += defs
            if (mids.isNotEmpty()) rows += mids
            if (fwds.isNotEmpty()) rows += fwds

            if (rows.size <= 1) {
                rows.clear()
                rows += listOf(deduped.first())
                deduped.drop(1).chunked(4).forEach { rows += it }
            }
        }
        return if (reverseRows) rows.reversed() else rows
    }

    private fun isDefenderPos(pos: String): Boolean {
        val p = pos.trim().uppercase()
        if (p.isEmpty()) return false
        // Defensive midfielders ("DM", "CDM", "LDM", "RDM") belong in midfield, never defence.
        if (p == "DM" || p == "CDM" || p == "LDM" || p == "RDM" || p.endsWith("M")) return false
        return p in setOf("D", "DF", "DEF", "CB", "LB", "RB", "LCB", "RCB", "LWB", "RWB", "WB", "SW") ||
            p.contains("DEF") ||
            (p.contains("BACK") && !p.contains("MID"))
    }

    private fun isMidfielderPos(pos: String): Boolean {
        val p = pos.trim().uppercase()
        if (p.isEmpty()) return false
        return p == "M" || p == "MF" || p == "MID" ||
            p in setOf("DM", "CDM", "CM", "LM", "RM", "AM", "CAM", "LDM", "RDM", "LAM", "RAM") ||
            p.contains("MID")
    }
}
