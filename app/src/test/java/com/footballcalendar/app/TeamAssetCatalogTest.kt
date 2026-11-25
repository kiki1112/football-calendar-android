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
