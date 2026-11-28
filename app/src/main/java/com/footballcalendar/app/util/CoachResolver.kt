package com.footballcalendar.app.util

import com.footballcalendar.app.data.remote.ApiFootballCoachDto
import com.footballcalendar.app.model.LiveMatchDetail
import com.footballcalendar.app.model.MatchItem
import com.footballcalendar.app.model.MatchLineupSide
import com.footballcalendar.app.model.TeamDetailInfo
import java.text.Normalizer

/**
 * Resolves the authoritative current head coach for a club/national team.
 * Priority:
 *  1. The latest current-season official match ([MatchItem] + [LiveMatchDetail]) coach entity.
 *  2. Active career coach from API-Football `/coachs` (`end == null`, latest `start`).
 *  3. Existing team metadata normalized through [ZhEntityDictionary] (`zh_entities.json`).
 */
object CoachResolver {
    data class ResolvedCoach(
        val name: String,
        val zhName: String,
        val portraitUrl: String = "",
    )

    fun selectActiveApiFootballCoach(
        coaches: List<ApiFootballCoachDto>,
        teamId: Int,
    ): ResolvedCoach? {
        if (coaches.isEmpty()) return null
        val scored = coaches.mapNotNull { coach ->
            val name = coach.name?.trim().orEmpty()
            if (name.isEmpty()) return@mapNotNull null
            val teamCareers = coach.career.filter { it.team?.id == teamId }
            val activeTenure = teamCareers
                .filter { it.end.isNullOrBlank() }
                .maxByOrNull { it.start.orEmpty() }
            val latestAnyTenure = teamCareers.maxByOrNull { it.end ?: it.start.orEmpty() }
            val rankScore = when {
                activeTenure != null -> "2_${activeTenure.start.orEmpty()}"
                latestAnyTenure != null -> "1_${latestAnyTenure.end ?: latestAnyTenure.start.orEmpty()}"
                else -> "0_"
            }
            Triple(rankScore, coach, name)
        }
        val best = scored.maxByOrNull { it.first } ?: return null
        val cName = best.third
        return ResolvedCoach(
            name = cName,
            zhName = TeamLocalization.localizeCoach(cName),
            portraitUrl = best.second.photo.orEmpty(),
        )
    }

    fun resolveFromLatestOfficialMatch(
        teamName: String,
        teamZhName: String,
        matches: List<MatchItem>,
        detailsByMatchId: Map<String, LiveMatchDetail>,
    ): ResolvedCoach? {
        val officialMatches = matches
            .asSequence()
            .filter { m ->
                (m.phase.isFinished || m.phase.isLiveOrHalfTime) &&
                    (matchesTeam(m.homeTeamName, m.homeTeamZhName, teamName, teamZhName) ||
                        matchesTeam(m.awayTeamName, m.awayTeamZhName, teamName, teamZhName))
            }
            .sortedByDescending { it.utcDate }

        for (match in officialMatches) {
            val detail = detailsByMatchId[match.id] ?: continue
            val side = findTeamLineupSide(detail.lineups, teamName, teamZhName) ?: continue
            val rawCoachName = side.coachName.trim()
            val rawCoachZh = side.coachZhName.trim()
            if (rawCoachName.isNotEmpty() || rawCoachZh.isNotEmpty()) {
                val normalizedZh = TeamLocalization.preferChineseCoach(rawCoachName, rawCoachZh)
                return ResolvedCoach(
                    name = rawCoachName.ifBlank { normalizedZh },
                    zhName = normalizedZh,
                    portraitUrl = side.coachPortraitUrl,
                )
            }
        }
        return null
    }

    fun applyAuthoritativeCoach(
        team: TeamDetailInfo,
        matches: List<MatchItem>,
        detailsByMatchId: Map<String, LiveMatchDetail>,
    ): TeamDetailInfo {
        val fromMatch = resolveFromLatestOfficialMatch(
            teamName = team.name,
            teamZhName = team.zhName,
            matches = matches,
            detailsByMatchId = detailsByMatchId,
        )
        if (fromMatch != null) {
            return team.copy(
                coachName = fromMatch.name,
                coachZhName = fromMatch.zhName,
                coachPortraitUrl = fromMatch.portraitUrl.ifBlank { team.coachPortraitUrl },
            )
        }
        val sanitized = sanitizeStaleHistoricalCoach(team)
        val normalizedExistingZh = TeamLocalization.preferChineseCoach(sanitized.coachName, sanitized.coachZhName)
        return if (sanitized != team || normalizedExistingZh != sanitized.coachZhName) {
            sanitized.copy(coachZhName = normalizedExistingZh)
        } else {
            team
        }
    }

    private fun sanitizeStaleHistoricalCoach(team: TeamDetailInfo): TeamDetailInfo {
        val normTeam = normalizeTeam(team.name)
        val normCoach = normalizeTeam(team.coachName)
        val rule = STALE_HISTORICAL_CLUB_OVERRIDES.firstOrNull { candidate ->
            normTeam.contains(candidate.clubKey) &&
                candidate.staleCoaches.any { stale -> normCoach == stale || team.coachZhName.contains(stale) }
        } ?: return team
        return team.copy(
            coachName = rule.activeCoachEn,
            coachZhName = TeamLocalization.preferChineseCoach(rule.activeCoachEn, rule.activeCoachZh),
        )
    }

    private data class StaleCoachRule(
        val clubKey: String,
        val staleCoaches: Set<String>,
        val activeCoachEn: String,
        val activeCoachZh: String,
    )

    private val STALE_HISTORICAL_CLUB_OVERRIDES = listOf(
        StaleCoachRule(
            clubKey = "real madrid",
            staleCoaches = setOf("jose mourinho", "何塞·莫乌里恩霍", "carlo ancelotti"),
            activeCoachEn = "Álvaro Arbeloa",
            activeCoachZh = "阿韦洛亚",
        ),
    )

    private fun findTeamLineupSide(
        lineups: List<MatchLineupSide>,
        teamName: String,
        teamZhName: String,
    ): MatchLineupSide? = lineups.firstOrNull { side ->
        matchesTeam(side.teamName, side.teamZhName, teamName, teamZhName)
    }

    private fun matchesTeam(
        candEn: String,
        candZh: String,
        targetEn: String,
        targetZh: String,
    ): Boolean {
        if (targetZh.isNotBlank() && candZh == targetZh) return true
        if (targetEn.isNotBlank() && candEn.equals(targetEn, ignoreCase = true)) return true
        val n1 = normalizeTeam(candEn)
        val n2 = normalizeTeam(targetEn)
        return n1.isNotBlank() && n1 == n2
    }

    private fun normalizeTeam(raw: String): String =
        Normalizer.normalize(raw, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9\\u4e00-\\u9fff]+"), " ")
            .replace(Regex("\\b(fc|cf|afc|kv|sc|sv|sk|fk|ssc|ac|as|us|rc|vfl|vfb|tsg|fsv)\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
