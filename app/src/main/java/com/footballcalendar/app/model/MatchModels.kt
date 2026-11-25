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
    DEMO("演示模式（离线可用）", "内置俱乐部+国家队+积分榜+球星库，可模拟改期"),
    GITHUB_PAGES("GitHub Pages 订阅源（推荐）", "自动同步你的 GitHub Pages 赛程、积分榜与球星"),
    FOOTBALL_DATA_API("Football-Data.org 直连", "填入个人免费 API Key 拉取官方实时数据");

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
    val isInternational: Boolean = false,
)

object CompetitionCatalog {
    val all: List<CompetitionMeta> = listOf(
        CompetitionMeta(
            code = "PL",
            enName = "Premier League",
            zhName = "英超",
            colorArgb = 0xFFE11D48,
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
            colorArgb = 0xFF0284C7,
            popularTeams = listOf("巴黎圣日耳曼", "马赛", "摩纳哥", "里昂"),
        ),
        CompetitionMeta(
            code = "CL",
            enName = "UEFA Champions League",
            zhName = "欧冠",
            colorArgb = 0xFF1D4ED8,
            popularTeams = listOf("皇家马德里", "曼城", "拜仁慕尼黑", "阿森纳", "巴塞罗那", "国际米兰", "巴黎圣日耳曼"),
        ),
        CompetitionMeta(
            code = "WC",
            enName = "FIFA World Cup",
            zhName = "世界杯",
            colorArgb = 0xFFD97706,
            popularTeams = listOf("阿根廷国家队", "法国国家队", "巴西国家队", "西班牙国家队", "英格兰国家队"),
            isInternational = true,
        ),
        CompetitionMeta(
            code = "EC",
            enName = "UEFA European Championship",
            zhName = "欧洲杯",
            colorArgb = 0xFFBE123C,
            popularTeams = listOf("西班牙国家队", "法国国家队", "英格兰国家队", "德国国家队", "葡萄牙国家队", "荷兰国家队", "意大利国家队"),
            isInternational = true,
        ),
        CompetitionMeta(
            code = "INT",
            enName = "International Match",
            zhName = "国家队",
            colorArgb = 0xFFE11D48,
            popularTeams = listOf("中国国家队", "日本国家队", "韩国国家队", "阿根廷国家队", "法国国家队", "巴西国家队"),
            isInternational = true,
        ),
    )

    val defaultCodes: Set<String> = all.map { it.code }.toSet()
    val standingsCodes: List<String> = listOf("PL", "PD", "BL1", "SA", "FL1", "CL")
    val internationalCodes: Set<String> = setOf("WC", "EC", "INT")

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
    val homeTeamCrest: String = "",
    val awayTeamCrest: String = "",
) {
    val isInternational: Boolean
        get() = competitionCode in CompetitionCatalog.internationalCodes

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

    fun involvesTeam(teamQuery: String): Boolean {
        if (teamQuery.isBlank()) return false
        return teamQuery.equals(homeTeamZhName, ignoreCase = true) ||
            teamQuery.equals(homeTeamName, ignoreCase = true) ||
            teamQuery.equals(awayTeamZhName, ignoreCase = true) ||
            teamQuery.equals(awayTeamName, ignoreCase = true)
    }
}

data class StandingEntry(
    val competitionCode: String,
    val rank: Int,
    val teamName: String,
    val teamZhName: String,
    val teamCrest: String,
    val played: Int,
    val won: Int,
    val draw: Int,
    val lost: Int,
    val goalDifference: Int,
    val points: Int,
    val form: String = "",
)

data class StarPlayer(
    val id: String,
    val name: String,
    val zhName: String,
    val clubName: String,
    val clubZhName: String,
    val clubCrestUrl: String,
    val nationalName: String,
    val nationalZhName: String,
    val nationalCrestUrl: String,
    val positionZh: String,
    val nationalityZh: String,
    val shirtNumber: Int,
    val birthYear: Int,
    val portraitUrl: String,
    val bio: String = "",
)

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
