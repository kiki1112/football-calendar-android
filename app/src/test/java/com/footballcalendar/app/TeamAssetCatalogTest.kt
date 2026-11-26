package com.footballcalendar.app

import com.footballcalendar.app.data.remote.EspnLeagueCatalog
import com.footballcalendar.app.data.remote.EspnSummaryResponse
import com.footballcalendar.app.data.remote.WikidataSparqlResponse
import com.footballcalendar.app.model.CompetitionCatalog
import com.footballcalendar.app.util.TeamAssetCatalog
import com.footballcalendar.app.util.TeamLocalization
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TeamAssetCatalogTest {
    @Test
    fun resolvesClubAndNationalTeamLogosAndLocalization() {
        assertTrue(TeamAssetCatalog.resolveTeamLogo("Arsenal FC").startsWith("https://"))
        assertTrue(TeamAssetCatalog.resolveTeamLogo("Argentina").startsWith("https://"))
        assertTrue(TeamAssetCatalog.resolveTeamLogo("France").startsWith("https://"))
        assertEquals("阿根廷国家队", TeamLocalization.localizeTeam("Argentina"))
        assertEquals("中国国家队", TeamLocalization.localizeTeam("China PR"))
        assertEquals("日本U17", TeamLocalization.localizeTeam("Japan U17"))
        assertEquals("神户胜利船", TeamLocalization.localizeTeam("Vissel Kobe"))
        assertEquals("迈阿密国际", TeamLocalization.localizeTeam("Inter Miami CF"))
        assertEquals("布莱顿", TeamLocalization.localizeTeam("Brighton & Hove Albion FC", "Brighton Hove"))
        assertEquals("伯恩茅斯", TeamLocalization.localizeTeam("AFC Bournemouth", "Bournemouth"))
        assertEquals("布伦特福德", TeamLocalization.localizeTeam("Brentford FC", "Brentford"))
        assertEquals("利兹联", TeamLocalization.localizeTeam("Leeds United FC", "Leeds United"))
        assertEquals("赫尔城", TeamLocalization.localizeTeam("Hull City AFC", "Hull City"))
        assertEquals("考文垂", TeamLocalization.localizeTeam("Coventry City FC", "Coventry City"))
        assertEquals("布鲁日", TeamLocalization.localizeTeam("Club Brugge KV", "Club Brugge"))
        assertEquals("桑德兰", TeamLocalization.localizeTeam("Sunderland AFC", "Sunderland"))
        assertEquals("埃尔弗斯贝格", TeamLocalization.localizeTeam("SV 07 Elversberg", "Elversberg"))
        assertEquals("帕德博恩", TeamLocalization.localizeTeam("SC Paderborn 07", "SC Paderborn"))
        assertEquals("桑坦德竞技", TeamLocalization.localizeTeam("Real Racing Club de Santander", "Santander"))
        assertEquals("拉科鲁尼亚", TeamLocalization.localizeTeam("RC Deportivo de La Coruña", "Deportivo"))
        assertEquals("马拉加", TeamLocalization.localizeTeam("Málaga CF", "Málaga"))
        assertEquals("萨巴赫", TeamLocalization.localizeTeam("Sabah FK", "Sabah FK"))
    }

    @Test
    fun starPlayersCatalogContainsVerifiedPortraitsAndExpandedCompetitionsExist() {
        assertTrue(TeamAssetCatalog.starPlayers.size >= 16)
        assertTrue(TeamAssetCatalog.starPlayers.all { it.portraitUrl.startsWith("https://") })
        val requiredCodes = listOf(
            "PL", "PD", "BL1", "SA", "FL1", "CL", "UEL", "UECL",
            "ACL", "J1", "J2", "KL1", "AL", "MLS",
            "WC", "EC", "UNL", "U17WC", "U20AC", "INT",
        )
        requiredCodes.forEach { code ->
            assertTrue("Missing competition $code", code in CompetitionCatalog.defaultCodes)
        }
    }

    @Test
    fun playerLocalizationTranslatesStarsAndTransliteratesYouthPlayers() {
        val (mbappeZh, mbappeTr) = TeamLocalization.localizePlayer("Kylian Mbappé")
        assertEquals("姆巴佩", mbappeZh)
        assertFalse(mbappeTr)

        val (youthZh, youthTr) = TeamLocalization.localizePlayer("Mateo Bramble")
        assertTrue(youthTr)
        assertTrue(TeamLocalization.containsCjk(youthZh))
        assertTrue(youthZh.contains("马特奥"))
    }

    @Test
    fun standingZonesClassifyPromotionPlayoffAndRelegation() {
        assertEquals("UCL" to "欧冠正赛区", TeamLocalization.determineStandingZone("PL", 1, 20))
        assertEquals("RELEGATION" to "降级区", TeamLocalization.determineStandingZone("PL", 19, 20))
        assertEquals("PLAYOFF" to "升级附加赛区", TeamLocalization.determineStandingZone("ELC", 4, 24))
        assertEquals("RELEGATION_PLAYOFF" to "升降级附加赛区", TeamLocalization.determineStandingZone("BL1", 16, 18))
        assertEquals("PLAYOFF" to "淘汰赛附加赛区", TeamLocalization.determineStandingZone("CL", 12, 36))
    }

    @Test
    fun espnLeaguesAndZonesLocalizeCleanly() {
        assertEquals("uefa.europa", EspnLeagueCatalog.slugForCode("UEL"))
        assertEquals("MLS", EspnLeagueCatalog.codeForSlug("usa.1"))
        assertEquals("东部联盟", TeamLocalization.localizeEspnGroup("Eastern Conference"))
        assertEquals("东亚区", TeamLocalization.localizeEspnGroup("East Region"))
        assertEquals("", TeamLocalization.localizeEspnGroup("Regular Season"))
        assertEquals(
            "UEL" to "欧联杯区",
            TeamLocalization.localizeEspnZone("UEFA Europa League", "PL", 5, 20),
        )
        assertEquals(
            "PLAYOFF" to "季后/淘汰附加赛区",
            TeamLocalization.localizeEspnZone("Finals Series", "AL", 3, 12),
        )
        assertEquals("比尔森胜利", TeamLocalization.localizeTeam("Plzen"))
        assertTrue(TeamAssetCatalog.resolveTeamLogo("Norway").endsWith("/nor.png"))
        assertTrue(TeamAssetCatalog.resolveTeamLogo("埃及国家队").endsWith("/egy.png"))
        assertEquals("PL", CompetitionCatalog.findByNameOrAlias("English Premier League")?.code)
    }

    @Test
    fun espnSummaryBackfillsEventsAndTranslatesCommentary() {
        val parser = Json { ignoreUnknownKeys = true; isLenient = true }
        val sampleJson = """
            {
              "header": {
                "competitions": [{
                  "status": {"displayClock": "90'+4'", "type": {"state": "post", "name": "STATUS_FULL_TIME"}},
                  "competitors": [
                    {"homeAway": "home", "score": "2", "team": {"id": "349", "displayName": "AFC Bournemouth"}},
                    {"homeAway": "away", "score": "0", "team": {"id": "393", "displayName": "Nottingham Forest"}}
                  ]
                }]
              },
              "keyEvents": [{
                "clock": {"displayValue": "19'"},
                "type": {"text": "Goal - Regular"},
                "team": {"id": "349", "displayName": "AFC Bournemouth"},
                "participants": [
                  {"athlete": {"displayName": "Evanilson"}},
                  {"athlete": {"displayName": "James Hill"}}
                ]
              }],
              "commentary": [
                {
                  "sequence": 1,
                  "time": {"displayValue": "45'"},
                  "text": "Substitution, Nottingham Forest. Igor Jesus replaces Dan Ndoye.",
                  "play": {
                    "type": {"type": "substitution", "text": "Substitution"},
                    "clock": {"displayValue": "45'"},
                    "team": {"id": "393", "displayName": "Nottingham Forest"},
                    "participants": [
                      {"athlete": {"displayName": "Igor Jesus"}},
                      {"athlete": {"displayName": "Dan Ndoye"}}
                    ]
                  }
                },
                {
                  "sequence": 2,
                  "time": {"displayValue": "45'+3'"},
                  "text": "Goal! Bournemouth 2, Nottingham Forest 0. Justin Kluivert scores.",
                  "play": {
                    "type": {"type": "goal", "text": "Goal"},
                    "clock": {"displayValue": "45'+3'"},
                    "team": {"id": "349", "displayName": "AFC Bournemouth"},
                    "participants": [
                      {"athlete": {"displayName": "Justin Kluivert"}},
                      {"athlete": {"displayName": "Marcus Tavernier"}}
                    ]
                  }
                }
              ]
            }
        """.trimIndent()
        val parsed = parser.decodeFromString(EspnSummaryResponse.serializer(), sampleJson)
        val detail = parsed.toLiveDetail(matchId = "espn-765", eventId = "765")
        assertEquals(3, detail.events.size)
        val goal2 = detail.events.first { it.playerName == "Justin Kluivert" }
        assertEquals(45, goal2.elapsed)
        assertEquals(3, goal2.extra)
        assertEquals("Goal", goal2.type)
        val subEv = detail.events.first { it.type == "subst" }
        assertEquals("Igor Jesus", subEv.playerName)
        assertEquals("Dan Ndoye", subEv.assistName)
        assertEquals(2, detail.commentary.size)
        assertTrue(detail.commentary[0].textZh.contains("战术换人"))
        assertTrue(detail.commentary[1].textZh.contains("破门得分"))

        val wdSample = """{"results":{"bindings":[{"label":{"value":"Rodrigo Hernández Cascante"},"img":{"value":"http://commons.wikimedia.org/wiki/Special:FilePath/Rodri.jpg"}}]}}"""
        val wd = parser.decodeFromString(WikidataSparqlResponse.serializer(), wdSample)
        assertEquals(1, wd.results?.bindings?.size)
    }
}
