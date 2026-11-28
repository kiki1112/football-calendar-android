package com.footballcalendar.app.data.remote

import com.footballcalendar.app.data.preferences.UserPreferences
import com.footballcalendar.app.model.CompetitionCatalog
import com.footballcalendar.app.model.DataSourceMode
import com.footballcalendar.app.model.LiveMatchDetail
import com.footballcalendar.app.model.MatchItem
import com.footballcalendar.app.model.MatchStatus
import com.footballcalendar.app.model.StandingEntry
import com.footballcalendar.app.model.StarPlayer
import com.footballcalendar.app.model.TeamDetailInfo
import com.footballcalendar.app.util.TeamAssetCatalog
import com.footballcalendar.app.util.TeamLocalization
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

data class SyncPayload(
    val matches: List<MatchItem>,
    val standings: List<StandingEntry>,
    val players: List<StarPlayer>,
    val teams: List<TeamDetailInfo> = emptyList(),
    val liveDetails: Map<String, LiveMatchDetail> = emptyMap(),
)

interface FixtureDataSource {
    suspend fun fetchAll(prefs: UserPreferences): SyncPayload
}

class MockFixtureDataSource : FixtureDataSource {
    override suspend fun fetchAll(prefs: UserPreferences): SyncPayload {
        val today = LocalDate.now(ZoneOffset.UTC)
        val nowIso = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
        val step = prefs.demoMutationStep

        val liverpoolHourShift = if (step % 2 == 1) 2L else 0L
        val bayernStatus = if (step % 2 == 1) MatchStatus.POSTPONED else MatchStatus.TIMED
        val interHomeScore = if (step % 2 == 1) 2 else null
        val interAwayScore = if (step % 2 == 1) 1 else null
        val interStatus = if (step % 2 == 1) MatchStatus.LIVE else MatchStatus.TIMED

        val templates = buildList {
            add(demo("demo-1001", "PL", today.minusDays(2), 16, 30, "Arsenal FC", "Chelsea FC", MatchStatus.FINISHED, 2, 1, "Emirates Stadium", nowIso))
            add(demo("demo-1002", "PD", today.minusDays(1), 20, 0, "Real Madrid CF", "FC Barcelona", MatchStatus.FINISHED, 3, 2, "Santiago Bernabéu", nowIso))
            add(demo("demo-1003", "PL", today, (12 + liverpoolHourShift).toInt(), 30, "Liverpool FC", "Manchester City FC", MatchStatus.TIMED, null, null, "Anfield", nowIso, lotteryCode = "周六008", lotteryHandicap = -1))
            add(demo("demo-1004", "BL1", today, 17, 30, "FC Bayern München", "Borussia Dortmund", bayernStatus, null, null, "Allianz Arena", nowIso, lotteryCode = "周六011", lotteryHandicap = -1))
            add(demo("demo-1005", "SA", today, 19, 45, "FC Internazionale Milano", "AC Milan", interStatus, interHomeScore, interAwayScore, "San Siro", nowIso, lotteryCode = "周六015", lotteryHandicap = -1))
            add(demo("demo-1006", "J1", today, 10, 0, "Vissel Kobe", "Yokohama F. Marinos", MatchStatus.TIMED, null, null, "Noevir Stadium Kobe", nowIso))
            add(demo("demo-1007", "ACL", today, 11, 0, "Shanghai Port", "Kawasaki Frontale", MatchStatus.TIMED, null, null, "上汽浦东足球场", nowIso))
            add(demo("demo-1008", "UEL", today.plusDays(1), 20, 0, "Manchester United FC", "AS Roma", MatchStatus.TIMED, null, null, "Old Trafford", nowIso, lotteryCode = "周日003", lotteryHandicap = -1))
            add(demo("demo-1009", "WC", today.plusDays(3), 0, 0, "Argentina", "Brazil", MatchStatus.TIMED, null, null, "Estadio Monumental", nowIso))
            add(demo("demo-1010", "U20AC", today.plusDays(3), 11, 35, "China PR U20", "Japan U20", MatchStatus.TIMED, null, null, "深圳青少年基地", nowIso))
            add(demo("demo-1011", "EC", today.plusDays(4), 19, 45, "England", "Germany", MatchStatus.TIMED, null, null, "Wembley Stadium", nowIso))
            add(demo("demo-1012", "U17WC", today.plusDays(5), 15, 0, "Japan U17", "Brazil U17", MatchStatus.TIMED, null, null, "Aspire Zone", nowIso))
            if (step > 0) {
                add(demo("demo-1018", "PL", today.plusDays(10), 18, 30, "Arsenal FC", "Liverpool FC", MatchStatus.TIMED, null, null, "Emirates Stadium", nowIso))
            }
        }

        return SyncPayload(
            matches = templates.filter { it.competitionCode in prefs.followedCompetitions },
            standings = demoStandings(),
            players = TeamAssetCatalog.starPlayers,
            teams = emptyList(),
        )
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
        lotteryCode: String? = null,
        lotteryHandicap: Int? = null,
    ): MatchItem {
        val meta = CompetitionCatalog.findByCode(compCode)
        val utcInstant = date.atTime(hour, minute).toInstant(ZoneOffset.UTC)
        val deadlineUtcMs = if (lotteryCode != null) utcInstant.minusSeconds(600).toEpochMilli() else null
        return MatchItem(
            id = id,
            competitionCode = compCode,
            competitionName = meta?.enName ?: compCode,
            competitionZhName = meta?.zhName ?: compCode,
            matchday = if (compCode in CompetitionCatalog.internationalCodes) null else 12,
            stage = if (compCode in CompetitionCatalog.internationalCodes) "INTERNATIONAL" else "REGULAR_SEASON",
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
            homeTeamCrest = TeamAssetCatalog.resolveTeamLogo(home),
            awayTeamCrest = TeamAssetCatalog.resolveTeamLogo(away),
            lotteryCode = lotteryCode,
            lotteryHandicap = lotteryHandicap,
            lotteryDeadlineUtc = deadlineUtcMs,
        )
    }

    companion object {
        fun demoStandings(): List<StandingEntry> {
            val raw = mapOf(
                "PL" to listOf(
                    row("PL", 1, "Liverpool FC", 12, 10, 1, 1, 18, 31, "W,W,D,W,W", 20),
                    row("PL", 2, "Arsenal FC", 12, 8, 4, 0, 14, 28, "W,D,W,W,W", 20),
                    row("PL", 3, "Manchester City FC", 12, 8, 2, 2, 12, 26, "W,W,L,D,W", 20),
                    row("PL", 4, "Chelsea FC", 12, 7, 2, 3, 9, 23, "D,W,W,L,W", 20),
                    row("PL", 5, "Aston Villa FC", 12, 6, 2, 4, 5, 20, "W,L,D,W,D", 20),
                    row("PL", 6, "Newcastle United FC", 12, 5, 4, 3, 4, 19, "D,W,W,L,D", 20),
                ),
                "PD" to listOf(
                    row("PD", 1, "FC Barcelona", 13, 11, 0, 2, 22, 33, "W,W,W,L,W", 20),
                    row("PD", 2, "Real Madrid CF", 13, 9, 3, 1, 16, 30, "W,D,W,W,W", 20),
                    row("PD", 3, "Club Atlético de Madrid", 13, 7, 5, 1, 11, 26, "W,W,D,W,D", 20),
                    row("PD", 4, "Athletic Club", 13, 6, 5, 2, 7, 23, "D,W,D,W,W", 20),
                ),
                "BL1" to listOf(
                    row("BL1", 1, "FC Bayern München", 11, 9, 2, 0, 24, 29, "W,W,W,D,W", 18),
                    row("BL1", 2, "Bayer 04 Leverkusen", 11, 6, 5, 0, 12, 23, "D,W,D,W,W", 18),
                    row("BL1", 3, "RB Leipzig", 11, 6, 3, 2, 8, 21, "W,L,W,D,W", 18),
                    row("BL1", 4, "Borussia Dortmund", 11, 6, 1, 4, 5, 19, "W,L,W,W,L", 18),
                ),
                "SA" to listOf(
                    row("SA", 1, "SSC Napoli", 12, 9, 1, 2, 13, 28, "W,W,L,W,W", 20),
                    row("SA", 2, "FC Internazionale Milano", 12, 8, 3, 1, 15, 27, "D,W,W,W,D", 20),
                    row("SA", 3, "Atalanta BC", 12, 8, 1, 3, 14, 25, "W,W,W,W,L", 20),
                    row("SA", 4, "Juventus FC", 12, 6, 6, 0, 11, 24, "D,W,D,D,W", 20),
                ),
                "FL1" to listOf(
                    row("FL1", 1, "Paris Saint-Germain FC", 12, 10, 2, 0, 23, 32, "W,W,W,D,W", 18),
                    row("FL1", 2, "AS Monaco FC", 12, 8, 2, 2, 12, 26, "W,D,W,L,W", 18),
                    row("FL1", 3, "Olympique de Marseille", 12, 7, 2, 3, 9, 23, "L,W,W,D,W", 18),
                    row("FL1", 4, "Olympique Lyonnais", 12, 6, 3, 3, 6, 21, "W,D,W,W,L", 18),
                ),
                "CL" to listOf(
                    row("CL", 1, "Liverpool FC", 5, 5, 0, 0, 11, 15, "W,W,W,W,W", 36),
                    row("CL", 2, "FC Internazionale Milano", 5, 4, 1, 0, 7, 13, "D,W,W,W,W", 36),
                    row("CL", 3, "FC Barcelona", 5, 4, 0, 1, 10, 12, "L,W,W,W,W", 36),
                    row("CL", 4, "Arsenal FC", 5, 3, 1, 1, 6, 10, "W,D,W,L,W", 36),
                ),
            )
            return raw.values.flatten()
        }

        private fun row(
            code: String,
            rank: Int,
            name: String,
            p: Int,
            w: Int,
            d: Int,
            l: Int,
            gd: Int,
            pts: Int,
            form: String,
            total: Int,
        ): StandingEntry {
            val (zType, zLabel) = TeamLocalization.determineStandingZone(code, rank, total)
            return StandingEntry(
                competitionCode = code,
                rank = rank,
                teamName = name,
                teamZhName = TeamLocalization.localizeTeam(name),
                teamCrest = TeamAssetCatalog.resolveTeamLogo(name),
                played = p,
                won = w,
                draw = d,
                lost = l,
                goalDifference = gd,
                points = pts,
                form = form,
                zoneType = zType,
                zoneLabel = zLabel,
            )
        }
    }
}

class GitHubPagesFixtureDataSource(
    private val apiService: FeedApiService,
) : FixtureDataSource {
    override suspend fun fetchAll(prefs: UserPreferences): SyncPayload {
        val normalizedUrl = normalizeFeedUrl(prefs.githubPagesUrl)
        require(normalizedUrl.startsWith("https://")) {
            "请先在设置中填写有效的 HTTPS GitHub Pages 订阅地址"
        }
        val response = apiService.fetchGitHubPagesFeed(normalizedUrl)
        return response.toSyncPayload(prefs.followedCompetitions)
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

class DelegatingFixtureDataSource(
    private val mockDataSource: MockFixtureDataSource,
    private val githubPagesDataSource: GitHubPagesFixtureDataSource,
) : FixtureDataSource {
    override suspend fun fetchAll(prefs: UserPreferences): SyncPayload {
        return when (prefs.dataSourceMode) {
            DataSourceMode.DEMO -> mockDataSource.fetchAll(prefs)
            DataSourceMode.GITHUB_PAGES -> githubPagesDataSource.fetchAll(prefs)
        }
    }
}
