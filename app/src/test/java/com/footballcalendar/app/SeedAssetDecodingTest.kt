package com.footballcalendar.app

import com.footballcalendar.app.data.remote.GitHubFeedResponse
import com.footballcalendar.app.data.repository.FixtureRepository
import com.footballcalendar.app.model.CompetitionCatalog
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File

class SeedAssetDecodingTest {
    @Test
    fun decodesBundledSeedBothAsGzipAndPlainJson() {
        val candidates = listOf(
            File("app/src/main/assets/seed_fixtures.json.gz"),
            File("src/main/assets/seed_fixtures.json.gz"),
        )
        val assetFile = candidates.firstOrNull { it.exists() }
            ?: error("Could not locate seed_fixtures.json.gz from ${File(".").absolutePath}")

        val rawJsonFromGz = assetFile.inputStream().use { FixtureRepository.decodeSeedStream(it) }
        val rawJsonFromPlain = ByteArrayInputStream(rawJsonFromGz.toByteArray(Charsets.UTF_8)).use {
            FixtureRepository.decodeSeedStream(it)
        }
        assertTrue(rawJsonFromGz == rawJsonFromPlain)

        val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }
        val parsed = json.decodeFromString(GitHubFeedResponse.serializer(), rawJsonFromPlain)
        val payload = parsed.toSyncPayload(CompetitionCatalog.defaultCodes)

        assertTrue("Expected >400 matches in bundled seed", payload.matches.size > 400)
        assertTrue("Expected >300 standings in bundled seed", payload.standings.size > 300)
        assertTrue("Expected >400 teams in bundled seed", payload.teams.size > 400)
        assertTrue("Expected >200 players in bundled seed", payload.players.size > 200)
        assertTrue("Expected >15 preloaded liveDetails in bundled seed", payload.liveDetails.size > 15)
    }
}
