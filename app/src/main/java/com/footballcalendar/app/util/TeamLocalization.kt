package com.footballcalendar.app.util

import com.footballcalendar.app.model.CompetitionCatalog

object TeamLocalization {
    private val teamZhMap = mapOf(
        "Arsenal FC" to "阿森纳",
        "Arsenal" to "阿森纳",
        "Manchester City FC" to "曼城",
        "Man City" to "曼城",
        "Liverpool FC" to "利物浦",
        "Liverpool" to "利物浦",
        "Chelsea FC" to "切尔西",
        "Chelsea" to "切尔西",
        "Manchester United FC" to "曼联",
        "Man United" to "曼联",
        "Tottenham Hotspur FC" to "热刺",
        "Tottenham" to "热刺",
        "Newcastle United FC" to "纽卡斯尔联",
        "Aston Villa FC" to "阿斯顿维拉",
        "Real Madrid CF" to "皇家马德里",
        "Real Madrid" to "皇家马德里",
        "FC Barcelona" to "巴塞罗那",
        "Barcelona" to "巴塞罗那",
        "Club Atlético de Madrid" to "马德里竞技",
        "Atlético Madrid" to "马德里竞技",
        "Atletico Madrid" to "马德里竞技",
        "Athletic Club" to "毕尔巴鄂竞技",
        "FC Bayern München" to "拜仁慕尼黑",
        "Bayern Munich" to "拜仁慕尼黑",
        "Borussia Dortmund" to "多特蒙德",
        "Dortmund" to "多特蒙德",
        "Bayer 04 Leverkusen" to "勒沃库森",
        "Leverkusen" to "勒沃库森",
        "RB Leipzig" to "RB莱比锡",
        "FC Internazionale Milano" to "国际米兰",
        "Inter" to "国际米兰",
        "AC Milan" to "AC米兰",
        "Milan" to "AC米兰",
        "Juventus FC" to "尤文图斯",
        "Juventus" to "尤文图斯",
        "SSC Napoli" to "那不勒斯",
        "Napoli" to "那不勒斯",
        "AS Roma" to "罗马",
        "Atalanta BC" to "亚特兰大",
        "Paris Saint-Germain FC" to "巴黎圣日耳曼",
        "PSG" to "巴黎圣日耳曼",
        "Olympique de Marseille" to "马赛",
        "AS Monaco FC" to "摩纳哥",
        "Olympique Lyonnais" to "里昂",
    )

    fun localizeTeam(fullName: String, shortName: String? = null): String {
        teamZhMap[fullName]?.let { return it }
        if (!shortName.isNullOrBlank()) {
            teamZhMap[shortName]?.let { return it }
            return shortName
        }
        return fullName
    }

    fun localizeCompetition(code: String, fallbackName: String): String =
        CompetitionCatalog.zhNameOrFallback(code, fallbackName)
}
