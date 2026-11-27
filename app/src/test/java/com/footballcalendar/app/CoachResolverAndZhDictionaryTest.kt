package com.footballcalendar.app

import com.footballcalendar.app.data.remote.ApiFootballCoachCareerDto
import com.footballcalendar.app.data.remote.ApiFootballCoachDto
import com.footballcalendar.app.data.remote.ApiFootballNamedIdDto
import com.footballcalendar.app.model.LiveMatchDetail
import com.footballcalendar.app.model.MatchItem
import com.footballcalendar.app.model.MatchLineupSide
import com.footballcalendar.app.model.MatchStatus
import com.footballcalendar.app.model.TeamDetailInfo
import com.footballcalendar.app.ui.theme.TeamColorRegistry
import com.footballcalendar.app.ui.theme.ThemeTokens
import com.footballcalendar.app.util.CoachResolver
import com.footballcalendar.app.util.TeamLocalization
import com.footballcalendar.app.util.ZhEntityDictionary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class CoachResolverAndZhDictionaryTest {
    @Test
    fun zhEntitiesOverridesLegacyPhoneticTransliterationLikeMourinho() {
        ZhEntityDictionary.ensureLoaded(null)

        // Fixes "何塞·莫乌里恩霍" stale mechanical transliteration with standard zh_entities.json hook
        assertEquals("穆里尼奥", TeamLocalization.preferChineseCoach("José Mourinho", "何塞·莫乌里恩霍"))
        assertEquals("阿韦洛亚", TeamLocalization.preferChineseCoach("Álvaro Arbeloa", ""))
        assertEquals("安切洛蒂", TeamLocalization.localizeCoach("Carlo Ancelotti"))
    }

    @Test
    fun latestOfficialMatchCoachOverridesStaleHistoricalTeamMetadata() {
        ZhEntityDictionary.ensureLoaded(null)

        val staleTeam = TeamDetailInfo(
            teamId = 86,
            competitionCode = "PD",
            name = "Real Madrid CF",
            shortName = "Real Madrid",
            zhName = "皇家马德里",
            crest = "",
            venue = "Estadio Santiago Bernabéu",
            founded = 1902,
            clubColors = "White",
            coachName = "José Mourinho",
            coachZhName = "何塞·莫乌里恩霍",
            coachNationality = "Portugal",
            squad = emptyList(),
        )
        val officialMatch = MatchItem(
            id = "rm-latest",
            competitionCode = "PD",
            competitionName = "Primera Division",
            competitionZhName = "西甲",
            matchday = 14,
            stage = "REGULAR_SEASON",
            utcDate = "2026-11-24T20:00:00Z",
            status = MatchStatus.FINISHED,
            homeTeamId = 86,
            homeTeamName = "Real Madrid CF",
            homeTeamShortName = "Real Madrid",
            homeTeamZhName = "皇家马德里",
            awayTeamId = 81,
            awayTeamName = "FC Barcelona",
            awayTeamShortName = "Barça",
            awayTeamZhName = "巴塞罗那",
            homeScore = 2,
            awayScore = 1,
            venue = "Bernabéu",
            lastUpdatedUtc = "2026-11-24T22:00:00Z",
        )
        val officialDetail = LiveMatchDetail(
            matchId = "rm-latest",
            statusShort = "FT",
            lineups = listOf(
                MatchLineupSide(
                    teamName = "Real Madrid CF",
                    teamZhName = "皇家马德里",
                    formation = "4-3-3",
                    coachName = "Álvaro Arbeloa",
                    coachZhName = "",
                    startXI = emptyList(),
                    substitutes = emptyList(),
                ),
            ),
        )

        val fixed = CoachResolver.applyAuthoritativeCoach(
            team = staleTeam,
            matches = listOf(officialMatch),
            detailsByMatchId = mapOf("rm-latest" to officialDetail),
        )
        assertEquals("Álvaro Arbeloa", fixed.coachName)
        assertEquals("阿韦洛亚", fixed.coachZhName)
    }

    @Test
    fun selectsActiveCareerCoachOverExpiredHistoricalTenure() {
        ZhEntityDictionary.ensureLoaded(null)

        val coaches = listOf(
            ApiFootballCoachDto(
                id = 18,
                name = "José Mourinho",
                photo = "old.png",
                career = listOf(
                    ApiFootballCoachCareerDto(
                        team = ApiFootballNamedIdDto(id = 541, name = "Real Madrid"),
                        start = "2010-05-01",
                        end = "2013-06-01",
                    ),
                ),
            ),
            ApiFootballCoachDto(
                id = 99,
                name = "Carlo Ancelotti",
                photo = "active.png",
                career = listOf(
                    ApiFootballCoachCareerDto(
                        team = ApiFootballNamedIdDto(id = 541, name = "Real Madrid"),
                        start = "2021-06-01",
                        end = null,
                    ),
                ),
            ),
        )

        val picked = CoachResolver.selectActiveApiFootballCoach(coaches, teamId = 541)
        assertNotNull(picked)
        assertEquals("Carlo Ancelotti", picked?.name)
        assertEquals("安切洛蒂", picked?.zhName)
    }

    @Test
    fun teamColorRegistryLoadsFromConfigAndFallsBackGracefully() {
        TeamColorRegistry.ensureLoaded(null)
        val realMadridColor = TeamColorRegistry.resolvePrimaryColor("Real Madrid CF")
        val barcelonaColor = TeamColorRegistry.resolvePrimaryColor("FC Barcelona")
        assertNotEquals(ThemeTokens.AccentFallback, realMadridColor)
        assertNotEquals(realMadridColor, barcelonaColor)
        assertEquals(ThemeTokens.AccentFallback, TeamColorRegistry.resolvePrimaryColor("Unknown FC 12345"))
    }
}
