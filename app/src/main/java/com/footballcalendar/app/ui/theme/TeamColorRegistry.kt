package com.footballcalendar.app.ui.theme

import android.content.Context
import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.text.Normalizer
import java.util.concurrent.atomic.AtomicBoolean

@Serializable
data class TeamColorFileDto(
    val meta: TeamColorMetaDto = TeamColorMetaDto(),
    val teams: Map<String, TeamColorEntryDto> = emptyMap(),
)

@Serializable
data class TeamColorMetaDto(
    val version: String = "1.0.0",
    val fallback: TeamColorFallbackDto = TeamColorFallbackDto(),
)

@Serializable
data class TeamColorFallbackDto(
    val primaryHex: String = "#C8102E",
    val secondaryHex: String = "#960B22",
    val accentStripeHex: String = "#C8102E",
)

@Serializable
data class TeamColorEntryDto(
    val zhName: String = "",
    val shortZh: String = "",
    val enName: String = "",
    val league: String = "",
    val primaryHex: String = "#C8102E",
    val secondaryHex: String = "#960B22",
    val accentStripeHex: String = "#C8102E",
)

data class TeamColorScheme(
    val primary: Color,
    val secondary: Color,
    val accentStripe: Color,
)

/**
 * Registry of calibrated club and national team colors loaded from `team_colors.json`,
 * falling back to [ThemeTokens.AccentFallback] (#C8102E) for uncatalogued teams.
 */
object TeamColorRegistry {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val loaded = AtomicBoolean(false)

    @Volatile
    private var entriesByKey: Map<String, TeamColorScheme> = emptyMap()

    @Volatile
    private var fallbackScheme = TeamColorScheme(
        primary = ThemeTokens.AccentFallback,
        secondary = ThemeTokens.AccentDeep,
        accentStripe = ThemeTokens.AccentFallback,
    )

    fun ensureLoaded(context: Context? = null) {
        if (loaded.get() && entriesByKey.isNotEmpty()) return
        val rawText = readAssetOrFile(context) ?: return
        loadFromJson(rawText)
        loaded.set(true)
    }

    internal fun loadFromJson(rawJson: String) {
        val parsed = runCatching { json.decodeFromString(TeamColorFileDto.serializer(), rawJson) }.getOrNull() ?: return
        fallbackScheme = TeamColorScheme(
            primary = parseHexOrNull(parsed.meta.fallback.primaryHex) ?: ThemeTokens.AccentFallback,
            secondary = parseHexOrNull(parsed.meta.fallback.secondaryHex) ?: ThemeTokens.AccentFallback,
            accentStripe = parseHexOrNull(parsed.meta.fallback.accentStripeHex) ?: ThemeTokens.AccentFallback,
        )
        val map = HashMap<String, TeamColorScheme>(parsed.teams.size * 4)
        parsed.teams.forEach { (slug, entry) ->
            val scheme = TeamColorScheme(
                primary = parseHexOrNull(entry.primaryHex) ?: fallbackScheme.primary,
                secondary = parseHexOrNull(entry.secondaryHex) ?: fallbackScheme.secondary,
                accentStripe = parseHexOrNull(entry.accentStripeHex) ?: fallbackScheme.accentStripe,
            )
            listOf(slug, entry.enName, entry.zhName, entry.shortZh).forEach { key ->
                val norm = normalizeTeamKey(key)
                if (norm.isNotBlank()) {
                    map[norm] = scheme
                }
            }
        }
        entriesByKey = map
    }

    fun resolvePrimaryColor(vararg candidates: String?): Color {
        if (!loaded.get()) ensureLoaded(null)
        for (raw in candidates) {
            if (raw.isNullOrBlank()) continue
            val norm = normalizeTeamKey(raw)
            entriesByKey[norm]?.let { return it.primary }
        }
        return fallbackScheme.primary
    }

    fun resolveScheme(vararg candidates: String?): TeamColorScheme {
        if (!loaded.get()) ensureLoaded(null)
        for (raw in candidates) {
            if (raw.isNullOrBlank()) continue
            val norm = normalizeTeamKey(raw)
            entriesByKey[norm]?.let { return it }
        }
        return fallbackScheme
    }

    private fun parseHexOrNull(hex: String?): Color? {
        val cleaned = hex?.trim()?.removePrefix("#").orEmpty()
        if (cleaned.length != 6 && cleaned.length != 8) return null
        val value = cleaned.toLongOrNull(16) ?: return null
        val argb = if (cleaned.length == 6) (0xFF000000L or value) else value
        return Color(argb)
    }

    private fun normalizeTeamKey(raw: String): String {
        val stripped = Normalizer.normalize(raw, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .lowercase()
            .replace("_", " ")
            .replace("&", " ")
            .replace(".", " ")
            .replace("-", " ")
            .replace(Regex("\\b(fc|cf|afc|sc|sv|sk|fk|ssc|ac|as|sl|kv|bc|de|futbol|balompie|club|1909|1907|04|nt)\\b"), " ")
            .replace("国家队", "")
            .replace(Regex("\\s+"), " ")
            .trim()
        return stripped
    }

    private fun readAssetOrFile(context: Context?): String? {
        if (context != null) {
            runCatching {
                return context.assets.open("team_colors.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
            }
        }
        val candidates = listOf(
            File("app/src/main/assets/team_colors.json"),
            File("src/main/assets/team_colors.json"),
            File("cloud-sync/dictionary/team_colors.json"),
        )
        return candidates.firstOrNull { it.exists() }?.readText(Charsets.UTF_8)
    }
}
