package com.footballcalendar.app

import com.footballcalendar.app.model.CompetitionCatalog
import com.footballcalendar.app.util.TeamAssetCatalog
import com.footballcalendar.app.util.TeamLocalization
import org.junit.Assert.assertEquals
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
        assertEquals("布莱顿", TeamLocalization.localizeTeam("Brighton & Hove Albion FC", "Brighton Hove"))
        assertEquals("伯恩茅斯", TeamLocalization.localizeTeam("AFC Bournemouth", "Bournemouth"))
        assertEquals("布伦特福德", TeamLocalization.localizeTeam("Brentford FC", "Brentford"))
        assertEquals("利兹联", TeamLocalization.localizeTeam("Leeds United FC", "Leeds United"))
        assertEquals("赫尔城", TeamLocalization.localizeTeam("Hull City AFC", "Hull City"))
        assertEquals("考文垂", TeamLocalization.localizeTeam("Coventry City FC", "Coventry City"))
        assertEquals("布鲁日", TeamLocalization.localizeTeam("Club Brugge KV", "Club Brugge"))
        assertEquals("桑德兰", TeamLocalization.localizeTeam("Sunderland AFC", "Sunderland"))
    }

    @Test
    fun starPlayersCatalogContainsVerifiedPortraitsAndInternationalCompetitionsExist() {
        assertTrue(TeamAssetCatalog.starPlayers.size >= 16)
        assertTrue(TeamAssetCatalog.starPlayers.all { it.portraitUrl.startsWith("https://") })
        assertTrue("WC" in CompetitionCatalog.defaultCodes)
        assertTrue("EC" in CompetitionCatalog.defaultCodes)
        assertTrue("INT" in CompetitionCatalog.defaultCodes)
    }
}
