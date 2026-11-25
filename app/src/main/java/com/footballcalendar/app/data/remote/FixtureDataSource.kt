package com.footballcalendar.app.data.remote

import com.footballcalendar.app.data.preferences.UserPreferences
import com.footballcalendar.app.model.CompetitionCatalog
import com.footballcalendar.app.model.DataSourceMode
import com.footballcalendar.app.model.MatchItem
import com.footballcalendar.app.model.MatchStatus
import com.footballcalendar.app.util.TeamLocalization
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

interface FixtureDataSource {
    suspend fun fetchMatches(prefs: UserPreferences): List<MatchItem>
}

class MockFixtureDataSource : FixtureDataSource {
    override suspend fun fetchMatches(prefs: UserPreferences): List<MatchItem> {
        val today = LocalDate.now(ZoneOffset.UTC)
        val nowIso = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
        val step = prefs.demoMutationStep

        // When step > 0, simulate real-world reschedules, live score updates, and new fixtures
        val liverpoolHourShift = if (step % 2 == 1) 2L else 0L
        val bayernStatus = if (step % 2 == 1) MatchStatus.POSTPONED else MatchStatus.TIMED
        val interHomeScore = if (step % 2 == 1) 2 else null
        val interAwayScore = if (step % 2 == 1) 1 else null
        val interStatus = if (step % 2 == 1) MatchStatus.LIVE else MatchStatus.TIMED

        val templates = buildList {
            add(demo("demo-1001", "PL", today.minusDays(2), 16, 30, "Arsenal FC", "Chelsea FC", MatchStatus.FINISHED, 2, 1, "Emirates Stadium", nowIso))
            add(demo("demo-1002", "PD", today.minusDays(1), 20, 0, "Real Madrid CF", "FC Barcelona", MatchStatus.FINISHED, 3, 2, "Santiago Bernabéu", nowIso))
            add(demo("demo-1003", "PL", today, (12 + liverpoolHourShift).toInt(), 30, "Liverpool FC", "Manchester City FC", MatchStatus.TIMED, null, null, "Anfield", nowIso))
            add(demo("demo-1004", "BL1", today, 17, 30, "FC Bayern München", "Borussia Dortmund", bayernStatus, null, null, "Allianz Arena", nowIso))
            add(demo("demo-1005", "SA", today, 19, 45, "FC Internazionale Milano", "AC Milan", interStatus, interHomeScore, interAwayScore, "San Siro", nowIso))
            add(demo("demo-1006", "CL", today.plusDays(1), 20, 0, "Paris Saint-Germain FC", "Real Madrid CF", MatchStatus.TIMED, null, null, "Parc des Princes", nowIso))
            add(demo("demo-1007", "CL", today.plusDays(1), 20, 0, "Arsenal FC", "FC Bayern München", MatchStatus.TIMED, null, null, "Emirates Stadium", nowIso))
            add(demo("demo-1008", "PL", today.plusDays(2), 15, 0, "Manchester United FC", "Tottenham Hotspur FC", MatchStatus.TIMED, null, null, "Old Trafford", nowIso))
            add(demo("demo-1009", "PD", today.plusDays(2), 19, 0, "Club Atlético de Madrid", "FC Barcelona", MatchStatus.TIMED, null, null, "Cívitas Metropolitano", nowIso))
            add(demo("demo-1010", "SA", today.plusDays(3), 19, 45, "Juventus FC", "SSC Napoli", MatchStatus.TIMED, null, null, "Allianz Stadium", nowIso))
            add(demo("demo-1011", "FL1", today.plusDays(4), 19, 0, "Olympique de Marseille", "Paris Saint-Germain FC", MatchStatus.TIMED, null, null, "Stade Vélodrome", nowIso))
            add(demo("demo-1012", "BL1", today.plusDays(5), 17, 30, "Bayer 04 Leverkusen", "RB Leipzig", MatchStatus.POSTPONED, null, null, "BayArena", nowIso))
            add(demo("demo-1013", "PL", today.plusDays(6), 16, 30, "Chelsea FC", "Liverpool FC", MatchStatus.TIMED, null, null, "Stamford Bridge", nowIso))
            add(demo("demo-1014", "CL", today.plusDays(8), 20, 0, "Manchester City FC", "FC Internazionale Milano", MatchStatus.SCHEDULED, null, null, "Etihad Stadium", nowIso))
            if (step > 0) {
                add(demo("demo-1015", "PL", today.plusDays(9), 18, 30, "Arsenal FC", "Liverpool FC", MatchStatus.TIMED, null, null, "Emirates Stadium", nowIso))
            }
        }
        return templates.filter { it.competitionCode in prefs.followedCompetitions }
    }

    private fun demo(
        id: String,
        compCode: String,
        date: LocalDate,
        hour: Int,
        minute: Int,
        home: String,
        away: String,
        status: MatchStatus,
        homeScore: Int?,
        awayScore: Int?,
        venue: String,
        nowIso: String,
    ): MatchItem {
        val meta = CompetitionCatalog.findByCode(compCode)
        val utcInstant = date.atTime(hour, minute).toInstant(ZoneOffset.UTC)
        return MatchItem(
            id = id,
            competitionCode = compCode,
            competitionName = meta?.enName ?: compCode,
            competitionZhName = meta?.zhName ?: compCode,
            matchday = 12,
            stage = if (compCode == "CL") "LEAGUE_PHASE" else "REGULAR_SEASON",
            utcDate = DateTimeFormatter.ISO_INSTANT.format(utcInstant),
            status = status,
            homeTeamId = home.hashCode(),
            homeTeamName = home,
            homeTeamShortName = home.replace(" FC", "").replace(" CF", ""),
            homeTeamZhName = TeamLocalization.localizeTeam(home),
            awayTeamId = away.hashCode(),
            awayTeamName = away,
            awayTeamShortName = away.replace(" FC", "").replace(" CF", ""),
            awayTeamZhName = TeamLocalization.localizeTeam(away),
            homeScore = homeScore,
            awayScore = awayScore,
            venue = venue,
            lastUpdatedUtc = nowIso,
        )
    }
}

class GitHubPagesFixtureDataSource(
    private val apiService: FeedApiService,
) : FixtureDataSource {
    override suspend fun fetchMatches(prefs: UserPreferences): List<MatchItem> {
        val normalizedUrl = normalizeFeedUrl(prefs.githubPagesUrl)
        require(normalizedUrl.startsWith("https://")) {
            "请先在设置中填写有效的 HTTPS GitHub Pages 订阅地址"
        }
        val response = apiService.fetchGitHubPagesFeed(normalizedUrl)
        return response.matches
            .map { it.toDomain() }
            .filter { prefs.followedCompetitions.isEmpty() || it.competitionCode in prefs.followedCompetitions }
    }

    companion object {
        fun normalizeFeedUrl(raw: String): String {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return ""
            return when {
                trimmed.endsWith(".json", ignoreCase = true) -> trimmed
                trimmed.endsWith("index.html", ignoreCase = true) ->
                    trimmed.removeSuffix("index.html") + "fixtures.json"
                trimmed.endsWith("/") -> trimmed + "fixtures.json"
                else -> "$trimmed/fixtures.json"
            }
        }
    }
}

class FootballDataApiFixtureDataSource(
    private val apiService: FeedApiService,
) : FixtureDataSource {
    override suspend fun fetchMatches(prefs: UserPreferences): List<MatchItem> {
        val apiKey = prefs.footballDataApiKey.trim()
        require(apiKey.isNotEmpty()) {
            "请先在设置中填写 Football-Data.org API Key"
        }
        val comps = prefs.followedCompetitions.ifEmpty { CompetitionCatalog.defaultCodes }
            .joinToString(",")
        val today = LocalDate.now(ZoneOffset.UTC)
        val windows = listOf(
            today.minusDays(2) to today.plusDays(8),
            today.plusDays(8) to today.plusDays(18),
        )
        val formatter = DateTimeFormatter.ISO_LOCAL_DATE
        val collected = linkedMapOf<String, MatchItem>()
        for ((from, to) in windows) {
            val resp = apiService.fetchFootballDataMatches(
                apiKey = apiKey,
                competitions = comps,
                dateFrom = formatter.format(from),
                dateTo = formatter.format(to),
            )
            resp.matches.mapNotNull { it.toDomain() }.forEach { match ->
                collected[match.id] = match
            }
        }
        return collected.values.toList()
    }
}

class DelegatingFixtureDataSource(
    private val mockDataSource: MockFixtureDataSource,
    private val githubPagesDataSource: GitHubPagesFixtureDataSource,
    private val footballDataApiDataSource: FootballDataApiFixtureDataSource,
) : FixtureDataSource {
    override suspend fun fetchMatches(prefs: UserPreferences): List<MatchItem> {
        return when (prefs.dataSourceMode) {
            DataSourceMode.DEMO -> mockDataSource.fetchMatches(prefs)
            DataSourceMode.GITHUB_PAGES -> githubPagesDataSource.fetchMatches(prefs)
            DataSourceMode.FOOTBALL_DATA_API -> footballDataApiDataSource.fetchMatches(prefs)
        }
    }
}
