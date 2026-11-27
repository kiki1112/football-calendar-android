package com.footballcalendar.app.util

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.text.Normalizer
import java.util.concurrent.atomic.AtomicBoolean

@Serializable
data class ZhEntityFileDto(
    val meta: ZhEntityMetaDto = ZhEntityMetaDto(),
    val teams: Map<String, ZhEntityEntryDto> = emptyMap(),
    val coaches: Map<String, ZhEntityEntryDto> = emptyMap(),
    val players: Map<String, ZhEntityEntryDto> = emptyMap(),
)

@Serializable
data class ZhEntityMetaDto(
    val version: String = "1.0.0",
    val maxShortZhLength: Int = 6,
)

@Serializable
data class ZhEntityEntryDto(
    val zhName: String = "",
    val shortZh: String = "",
)

data class ZhEntityEntry(
    val zhName: String,
    val shortZh: String,
)

/**
 * Standard Chinese dictionary hook (`zh_entities.json`, ARCHITECTURE.md Rule 2).
 * Maps mainstream teams, coaches, and star players to canonical Chinese names
 * and concise <= 6-char short names, eliminating mechanical transliteration drift.
 */
object ZhEntityDictionary {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val loaded = AtomicBoolean(false)

    @Volatile
    private var maxShortLen: Int = 6

    @Volatile
    private var teamsByKey: Map<String, ZhEntityEntry> = emptyMap()

    @Volatile
    private var coachesByKey: Map<String, ZhEntityEntry> = emptyMap()

    @Volatile
    private var playersByKey: Map<String, ZhEntityEntry> = emptyMap()

    fun ensureLoaded(context: Context? = null) {
        if (loaded.get() && (coachesByKey.isNotEmpty() || teamsByKey.isNotEmpty())) return
        val rawText = readAssetOrFile(context) ?: return
        loadFromJson(rawText)
        loaded.set(true)
    }

    internal fun loadFromJson(rawJson: String) {
        val parsed = runCatching { json.decodeFromString(ZhEntityFileDto.serializer(), rawJson) }.getOrNull() ?: return
        maxShortLen = parsed.meta.maxShortZhLength.coerceIn(2, 6)
        teamsByKey = buildNormalizedIndex(parsed.teams)
        coachesByKey = buildNormalizedIndex(parsed.coaches)
        playersByKey = buildNormalizedIndex(parsed.players)
    }

    fun findTeam(rawName: String?): ZhEntityEntry? {
        if (rawName.isNullOrBlank()) return null
        if (!loaded.get()) ensureLoaded(null)
        return teamsByKey[normalizeName(rawName)]
    }

    fun findCoach(rawName: String?): ZhEntityEntry? {
        if (rawName.isNullOrBlank()) return null
        if (!loaded.get()) ensureLoaded(null)
        return coachesByKey[normalizeName(rawName)]
    }

    fun findPlayer(rawName: String?): ZhEntityEntry? {
        if (rawName.isNullOrBlank()) return null
        if (!loaded.get()) ensureLoaded(null)
        return playersByKey[normalizeName(rawName)]
    }

    private fun buildNormalizedIndex(source: Map<String, ZhEntityEntryDto>): Map<String, ZhEntityEntry> {
        val map = HashMap<String, ZhEntityEntry>(source.size * 2)
        source.forEach { (key, dto) ->
            if (dto.zhName.isNotBlank()) {
                val shortCapped = dto.shortZh.ifBlank { dto.zhName }.take(maxShortLen)
                val entry = ZhEntityEntry(zhName = dto.zhName.trim(), shortZh = shortCapped.trim())
                val norm = normalizeName(key)
                if (norm.isNotBlank()) map[norm] = entry
            }
        }
        return map
    }

    private fun normalizeName(raw: String): String {
        return Normalizer.normalize(raw.trim(), Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .lowercase()
            .replace("ø", "o")
            .replace("ł", "l")
            .replace("đ", "d")
            .replace("ß", "ss")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun readAssetOrFile(context: Context?): String? {
        if (context != null) {
            runCatching {
                return context.assets.open("zh_entities.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
            }
        }
        val candidates = listOf(
            File("app/src/main/assets/zh_entities.json"),
            File("src/main/assets/zh_entities.json"),
            File("cloud-sync/dictionary/zh_entities.json"),
        )
        return candidates.firstOrNull { it.exists() }?.readText(Charsets.UTF_8)
    }
}
