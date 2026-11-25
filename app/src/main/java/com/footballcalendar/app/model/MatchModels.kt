package com.footballcalendar.app.model

enum class MatchStatus(val zhLabel: String) {
    TIMED("未开赛"),
    SCHEDULED("待定开球"),
    LIVE("进行中"),
    IN_PLAY("进行中"),
    PAUSED("中场休息"),
    FINISHED("已完赛"),
    POSTPONED("已延期"),
    SUSPENDED("已暂停"),
    CANCELLED("已取消"),
    UNKNOWN("未知");

    val isCancelledOrPostponed: Boolean
        get() = this == POSTPONED || this == SUSPENDED || this == CANCELLED

    val isLive: Boolean
        get() = this == LIVE || this == IN_PLAY || this == PAUSED

    companion object {
        fun fromRaw(raw: String?): MatchStatus {
            if (raw.isNullOrBlank()) return UNKNOWN
            return entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) } ?: UNKNOWN
        }
    }
}

enum class DataSourceMode(val displayName: String, val description: String) {
    DEMO("演示模式（离线可用）", "使用内置模拟赛程，支持一键测试改期更新"),
    GITHUB_PAGES("GitHub Pages 订阅源（推荐）", "免客户端 Key，自动从你的 GitHub Pages JSON 同步"),
    FOOTBALL_DATA_API("Football-Data.org 直连", "填入个人免费 API Key 直接拉取官方实时赛程");

    companion object {
        fun fromName(name: String?): DataSourceMode {
            return entries.firstOrNull { it.name == name } ?: DEMO
        }
    }
}

data class CompetitionMeta(
    val code: String,
    val enName: String,
    val zhName: String,
    val colorArgb: Long,
    val popularTeams: List<String>,
)

object CompetitionCatalog {
    val all: List<CompetitionMeta> = listOf(
        CompetitionMeta(
            code = "PL",
            enName = "Premier League",
            zhName = "英超",
            colorArgb = 0xFF9333EA,
            popularTeams = listOf("阿森纳", "曼城", "利物浦", "切尔西", "曼联", "热刺", "纽卡斯尔联", "阿斯顿维拉"),
        ),
        CompetitionMeta(
            code = "PD",
            enName = "Primera Division",
            zhName = "西甲",
            colorArgb = 0xFFEA580C,
            popularTeams = listOf("皇家马德里", "巴塞罗那", "马德里竞技", "毕尔巴鄂竞技"),
        ),
        CompetitionMeta(
            code = "BL1",
            enName = "Bundesliga",
            zhName = "德甲",
            colorArgb = 0xFFDC2626,
            popularTeams = listOf("拜仁慕尼黑", "多特蒙德", "勒沃库森", "RB莱比锡"),
        ),
        CompetitionMeta(
            code = "SA",
            enName = "Serie A",
            zhName = "意甲",
            colorArgb = 0xFF2563EB,
            popularTeams = listOf("国际米兰", "AC米兰", "尤文图斯", "那不勒斯", "罗马", "亚特兰大"),
        ),
        CompetitionMeta(
            code = "FL1",
            enName = "Ligue 1",
            zhName = "法甲",
            colorArgb = 0xFF0891B2,
            popularTeams = listOf("巴黎圣日耳曼", "马赛", "摩纳哥", "里昂"),
        ),
        CompetitionMeta(
            code = "CL",
            enName = "UEFA Champions League",
            zhName = "欧冠",
            colorArgb = 0xFF4F46E5,
            popularTeams = listOf("皇家马德里", "曼城", "拜仁慕尼黑", "阿森纳", "巴塞罗那", "国际米兰", "巴黎圣日耳曼"),
        ),
    )

    val defaultCodes: Set<String> = all.map { it.code }.toSet()

    fun findByCode(code: String): CompetitionMeta? =
        all.firstOrNull { it.code.equals(code, ignoreCase = true) }

    fun zhNameOrFallback(code: String, fallback: String): String =
        findByCode(code)?.zhName ?: fallback
}

data class MatchItem(
    val id: String,
    val competitionCode: String,
    val competitionName: String,
    val competitionZhName: String,
    val matchday: Int?,
    val stage: String,
    val utcDate: String,
    val status: MatchStatus,
    val homeTeamId: Int,
    val homeTeamName: String,
    val homeTeamShortName: String,
    val homeTeamZhName: String,
    val awayTeamId: Int,
    val awayTeamName: String,
    val awayTeamShortName: String,
    val awayTeamZhName: String,
    val homeScore: Int?,
    val awayScore: Int?,
    val venue: String,
    val lastUpdatedUtc: String,
) {
    fun involvesAnyTeam(favorites: Set<String>): Boolean {
        if (favorites.isEmpty()) return false
        return favorites.any { fav ->
            fav.equals(homeTeamZhName, ignoreCase = true) ||
                fav.equals(homeTeamName, ignoreCase = true) ||
                fav.equals(homeTeamShortName, ignoreCase = true) ||
                fav.equals(awayTeamZhName, ignoreCase = true) ||
                fav.equals(awayTeamName, ignoreCase = true) ||
                fav.equals(awayTeamShortName, ignoreCase = true)
        }
    }
}

enum class ScheduleChangeType {
    ADDED,
    RESCHEDULED,
    POSTPONED,
    SCORE_UPDATED,
}

data class ScheduleChange(
    val match: MatchItem,
    val type: ScheduleChangeType,
    val summary: String,
)

data class SyncSummary(
    val syncedAtUtc: String,
    val totalMatches: Int,
    val changes: List<ScheduleChange>,
    val sourceMode: DataSourceMode,
    val message: String,
)
