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
    DEMO("演示模式（离线可用）", "内置精选赛程、积分榜与球员榜，可模拟改期"),
    GITHUB_PAGES("GitHub Pages 订阅源（推荐）", "自动聚合全网官方赛程、积分榜、球队阵容与球员榜"),
    FOOTBALL_DATA_API("官方实时 API 直连", "使用内置部署密钥直连官方数据源与实时赛况");

    companion object {
        fun fromName(name: String?): DataSourceMode {
            return entries.firstOrNull { it.name == name } ?: GITHUB_PAGES
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
        CompetitionMeta("PL", "Premier League", "英超", 0xFFE11D48, listOf("阿森纳", "曼城", "利物浦", "切尔西", "曼联", "热刺")),
        CompetitionMeta("PD", "Primera Division", "西甲", 0xFFEA580C, listOf("皇家马德里", "巴塞罗那", "马德里竞技", "毕尔巴鄂竞技")),
        CompetitionMeta("BL1", "Bundesliga", "德甲", 0xFFDC2626, listOf("拜仁慕尼黑", "多特蒙德", "勒沃库森", "RB莱比锡")),
        CompetitionMeta("SA", "Serie A", "意甲", 0xFF2563EB, listOf("国际米兰", "AC米兰", "尤文图斯", "那不勒斯", "罗马", "亚特兰大")),
        CompetitionMeta("FL1", "Ligue 1", "法甲", 0xFF0284C7, listOf("巴黎圣日耳曼", "马赛", "摩纳哥", "里昂")),
        CompetitionMeta("CL", "UEFA Champions League", "欧冠", 0xFF1D4ED8, listOf("皇家马德里", "曼城", "拜仁慕尼黑", "阿森纳", "巴塞罗那")),
        CompetitionMeta("UEL", "UEFA Europa League", "欧联杯", 0xFFF97316, listOf("曼联", "热刺", "罗马", "费内巴切")),
        CompetitionMeta("UECL", "UEFA Conference League", "欧协联", 0xFF10B981, listOf("切尔西", "佛罗伦萨", "皇家贝蒂斯")),
        CompetitionMeta("ACL", "AFC Champions League Elite", "亚冠精英赛", 0xFF0EA5E9, listOf("上海海港", "上海申花", "山东泰山", "利雅得新月", "利雅得胜利", "神户胜利船")),
        CompetitionMeta("J1", "J1 League", "日职联", 0xFFE11D48, listOf("神户胜利船", "广岛三箭", "川崎前锋", "浦和红钻", "横滨水手")),
        CompetitionMeta("J2", "J2 League", "日职乙", 0xFFFB7185, listOf("清水心跳", "横滨FC", "千叶市原", "长崎成功丸")),
        CompetitionMeta("KL1", "K League 1", "韩K联", 0xFF2563EB, listOf("蔚山HD", "全北现代", "浦项铁人", "首尔FC")),
        CompetitionMeta("AL", "A-League", "澳超", 0xFFD97706, listOf("奥克兰FC", "墨尔本胜利", "悉尼FC", "阿德莱德联")),
        CompetitionMeta("MLS", "Major League Soccer", "美职联", 0xFF4F46E5, listOf("迈阿密国际", "洛杉矶银河", "洛杉矶FC", "西雅图海湾人")),
        CompetitionMeta("ELC", "Championship", "英冠", 0xFF7C3AED, listOf("利兹联", "伯恩利", "谢菲尔德联", "桑德兰")),
        CompetitionMeta("DED", "Eredivisie", "荷甲", 0xFFF59E0B, listOf("埃因霍温", "阿贾克斯", "费耶诺德")),
        CompetitionMeta("PPL", "Primeira Liga", "葡超", 0xFF059669, listOf("葡萄牙体育", "本菲卡", "波尔图")),
        CompetitionMeta("BSA", "Brasileirão Série A", "巴甲", 0xFF16A34A, listOf("博塔弗戈", "帕尔梅拉斯", "弗拉门戈")),
        CompetitionMeta("CLI", "Copa Libertadores", "解放者杯", 0xFFB45309, listOf("博塔弗戈", "河床", "博卡青年")),
        CompetitionMeta("WC", "FIFA World Cup", "世界杯", 0xFFD97706, listOf("阿根廷国家队", "法国国家队", "巴西国家队", "西班牙国家队"), isInternational = true),
        CompetitionMeta("EC", "UEFA European Championship", "欧洲杯", 0xFFBE123C, listOf("西班牙国家队", "法国国家队", "英格兰国家队", "德国国家队"), isInternational = true),
        CompetitionMeta("UNL", "UEFA Nations League", "欧国联", 0xFF0284C7, listOf("葡萄牙国家队", "西班牙国家队", "德国国家队", "法国国家队"), isInternational = true),
        CompetitionMeta("U17WC", "FIFA U-17 World Cup", "U17世界杯", 0xFF9333EA, listOf("日本U17", "巴西U17", "英格兰U17", "西班牙U17"), isInternational = true),
        CompetitionMeta("U20AC", "AFC U-20 Asian Cup", "U20亚洲杯", 0xFF0D9488, listOf("中国U20", "日本U20", "韩国U20", "沙特阿拉伯U20"), isInternational = true),
        CompetitionMeta("INT", "International Match", "国际赛事", 0xFFE11D48, listOf("中国国家队", "日本国家队", "韩国国家队"), isInternational = true),
    )

    val defaultCodes: Set<String> = all.map { it.code }.toSet()
    val standingsCodes: List<String> = listOf("PL", "PD", "BL1", "SA", "FL1", "CL", "ELC", "DED", "PPL", "BSA")
    val internationalCodes: Set<String> = setOf("WC", "EC", "UNL", "U17WC", "U20AC", "INT")

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
    val elapsedMinutes: Int? = null,
    val apiFootballFixtureId: Int? = null,
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
    val zoneType: String = "NONE",
    val zoneLabel: String = "",
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
    val competitionCode: String = "",
    val rank: Int = 0,
    val goals: Int = 0,
    val assists: Int = 0,
    val penalties: Int = 0,
    val playedMatches: Int = 0,
    val isTransliterated: Boolean = false,
)

data class SquadMember(
    val id: String,
    val name: String,
    val zhName: String,
    val isTransliterated: Boolean = false,
    val position: String = "",
    val positionZh: String = "",
    val nationality: String = "",
    val birthYear: Int = 0,
    val shirtNumber: Int = 0,
    val portraitUrl: String = "",
)

data class TeamDetailInfo(
    val teamId: Int,
    val competitionCode: String,
    val name: String,
    val shortName: String,
    val zhName: String,
    val crest: String,
    val venue: String,
    val founded: Int,
    val clubColors: String,
    val coachName: String,
    val coachZhName: String,
    val coachNationality: String,
    val squad: List<SquadMember>,
)

data class MatchEventItem(
    val elapsed: Int,
    val extra: Int?,
    val teamName: String,
    val teamZhName: String,
    val isHomeTeam: Boolean,
    val playerName: String,
    val playerZhName: String,
    val assistName: String?,
    val assistZhName: String?,
    val type: String,
    val detail: String,
)

data class LineupPlayerItem(
    val name: String,
    val zhName: String,
    val number: Int,
    val pos: String,
)

data class MatchLineupSide(
    val teamName: String,
    val teamZhName: String,
    val formation: String,
    val coachName: String,
    val coachZhName: String,
    val startXI: List<LineupPlayerItem>,
    val substitutes: List<LineupPlayerItem>,
)

data class MatchStatPair(
    val labelZh: String,
    val homeValue: String,
    val awayValue: String,
)

data class LiveMatchDetail(
    val matchId: String,
    val fixtureId: Int,
    val elapsedMinutes: Int?,
    val statusShort: String,
    val homeScore: Int?,
    val awayScore: Int?,
    val events: List<MatchEventItem>,
    val lineups: List<MatchLineupSide>,
    val stats: List<MatchStatPair>,
    val syncedAtMillis: Long,
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
