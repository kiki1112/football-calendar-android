#!/usr/bin/env python3
"""Football match schedule, standings, teams/squads, and player rankings feed generator (v3).

Outputs into --output-dir:
  - fixtures.json : Normalized feed (matches, zone-tagged standings, full teams/squads, player rankings)
  - football.ics  : Combined RFC 5545 iCalendar subscription file
  - ics/<CODE>.ics: Per-competition iCalendar subscription files
  - index.html    : Apple Red & White web dashboard
"""

from __future__ import annotations

import argparse
import dataclasses
import datetime as dt
import html
import json
import os
import pathlib
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from typing import Any

from localization import (
    PLAYER_ZH_MAP,
    TEAM_ZH_MAP,
    _normalize_team_key,
    localize_espn_group,
    localize_espn_zone,
    localize_player,
    localize_position,
    localize_team,
)

DEFAULT_COMPETITIONS = [
    "PL", "PD", "BL1", "SA", "FL1", "CL",
    "UEL", "UECL", "ACL", "J1", "J2", "KL1", "AL", "MLS",
    "ELC", "DED", "PPL", "BSA", "CLI",
    "WC", "EC", "UNL", "U17WC", "U20AC",
]
FD_COMPETITIONS = ["PL", "PD", "BL1", "SA", "FL1", "CL", "ELC", "DED", "PPL", "BSA", "CLI", "WC", "EC"]
STANDINGS_COMPETITIONS = [
    "PL", "PD", "BL1", "SA", "FL1", "CL",
    "UEL", "UECL", "ACL", "J1", "AL", "MLS",
    "ELC", "DED", "PPL", "BSA",
]
INTERNATIONAL_CODES = {"WC", "EC", "UNL", "U17WC", "U20AC", "INT"}

ESPN_SLUG_BY_CODE: dict[str, str] = {
    "PL": "eng.1",
    "PD": "esp.1",
    "BL1": "ger.1",
    "SA": "ita.1",
    "FL1": "fra.1",
    "CL": "uefa.champions",
    "UEL": "uefa.europa",
    "UECL": "uefa.europa.conf",
    "ACL": "afc.champions",
    "J1": "jpn.1",
    "AL": "aus.1",
    "MLS": "usa.1",
    "ELC": "eng.2",
    "DED": "ned.1",
    "PPL": "por.1",
    "BSA": "bra.1",
    "CLI": "conmebol.libertadores",
    "WC": "fifa.world",
    "EC": "uefa.euro",
    "UNL": "uefa.nations",
    "U17WC": "fifa.world.u17",
}
ESPN_CODE_BY_SLUG: dict[str, str] = {v: k for k, v in ESPN_SLUG_BY_CODE.items()}
ESPN_STANDINGS_CODES = ["UEL", "UECL", "ACL", "J1", "AL", "MLS"]

COMPETITION_META: dict[str, dict[str, str]] = {
    "PL": {"name": "Premier League", "zhName": "英超", "country": "英格兰", "color": "#E11D48"},
    "PD": {"name": "Primera Division", "zhName": "西甲", "country": "西班牙", "color": "#EA580C"},
    "BL1": {"name": "Bundesliga", "zhName": "德甲", "country": "德国", "color": "#DC2626"},
    "SA": {"name": "Serie A", "zhName": "意甲", "country": "意大利", "color": "#2563EB"},
    "FL1": {"name": "Ligue 1", "zhName": "法甲", "country": "法国", "color": "#0284C7"},
    "CL": {"name": "UEFA Champions League", "zhName": "欧冠", "country": "欧洲", "color": "#1D4ED8"},
    "UEL": {"name": "UEFA Europa League", "zhName": "欧联杯", "country": "欧洲", "color": "#F97316"},
    "UECL": {"name": "UEFA Conference League", "zhName": "欧协联", "country": "欧洲", "color": "#10B981"},
    "ACL": {"name": "AFC Champions League Elite", "zhName": "亚冠精英赛", "country": "亚洲", "color": "#0EA5E9"},
    "J1": {"name": "J1 League", "zhName": "日职联", "country": "日本", "color": "#E11D48"},
    "J2": {"name": "J2 League", "zhName": "日职乙", "country": "日本", "color": "#FB7185"},
    "KL1": {"name": "K League 1", "zhName": "韩K联", "country": "韩国", "color": "#2563EB"},
    "AL": {"name": "A-League", "zhName": "澳超", "country": "澳大利亚", "color": "#D97706"},
    "MLS": {"name": "Major League Soccer", "zhName": "美职联", "country": "北美", "color": "#4F46E5"},
    "ELC": {"name": "Championship", "zhName": "英冠", "country": "英格兰", "color": "#7C3AED"},
    "DED": {"name": "Eredivisie", "zhName": "荷甲", "country": "荷兰", "color": "#F59E0B"},
    "PPL": {"name": "Primeira Liga", "zhName": "葡超", "country": "葡萄牙", "color": "#059669"},
    "BSA": {"name": "Brasileirão Série A", "zhName": "巴甲", "country": "巴西", "color": "#16A34A"},
    "CLI": {"name": "Copa Libertadores", "zhName": "解放者杯", "country": "南美", "color": "#B45309"},
    "WC": {"name": "FIFA World Cup", "zhName": "世界杯", "country": "国家队", "color": "#D97706"},
    "EC": {"name": "UEFA European Championship", "zhName": "欧洲杯", "country": "国家队", "color": "#BE123C"},
    "UNL": {"name": "UEFA Nations League", "zhName": "欧国联", "country": "国家队", "color": "#0284C7"},
    "U17WC": {"name": "FIFA U-17 World Cup", "zhName": "U17世界杯", "country": "国家队", "color": "#9333EA"},
    "U20AC": {"name": "AFC U-20 Asian Cup", "zhName": "U20亚洲杯", "country": "国家队", "color": "#0D9488"},
    "INT": {"name": "International Match", "zhName": "国际赛事", "country": "国家队", "color": "#E11D48"},
}

API_FOOTBALL_LEAGUE_MAP: dict[int, str] = {
    3: "UEL",
    848: "UECL",
    17: "ACL",
    98: "J1",
    99: "J2",
    292: "KL1",
    188: "AL",
    253: "MLS",
    5: "UNL",
    587: "U17WC",
    965: "U20AC",
    1153: "U20AC",
    1: "WC",
    30: "WC",
    31: "WC",
    32: "WC",
    33: "WC",
    34: "WC",
    37: "WC",
    4: "EC",
    960: "EC",
    1012: "INT",
    10: "INT",
    39: "PL",
    140: "PD",
    78: "BL1",
    135: "SA",
    61: "FL1",
    2: "CL",
    40: "ELC",
    88: "DED",
    94: "PPL",
    71: "BSA",
}

TEAM_LOGO_MAP: dict[str, str] = {
    "Arsenal FC": "https://thumb.wikimedia.org/wikipedia/en/thumb/5/53/Arsenal_FC.svg/330px-Arsenal_FC.svg.png",
    "Manchester City FC": "https://thumb.wikimedia.org/wikipedia/en/thumb/e/eb/Manchester_City_FC_badge.svg/330px-Manchester_City_FC_badge.svg.png",
    "Liverpool FC": "https://thumb.wikimedia.org/wikipedia/en/thumb/0/0c/Liverpool_FC.svg/330px-Liverpool_FC.svg.png",
    "Chelsea FC": "https://thumb.wikimedia.org/wikipedia/en/thumb/c/cc/Chelsea_FC.svg/330px-Chelsea_FC.svg.png",
    "Manchester United FC": "https://thumb.wikimedia.org/wikipedia/en/thumb/7/7a/Manchester_United_FC_crest.svg/330px-Manchester_United_FC_crest.svg.png",
    "Tottenham Hotspur FC": "https://thumb.wikimedia.org/wikipedia/en/thumb/b/b4/Tottenham_Hotspur.svg/330px-Tottenham_Hotspur.svg.png",
    "Newcastle United FC": "https://thumb.wikimedia.org/wikipedia/en/thumb/8/87/Newcastle_United_FC_2026_crest.svg/330px-Newcastle_United_FC_2026_crest.svg.png",
    "Aston Villa FC": "https://thumb.wikimedia.org/wikipedia/en/thumb/9/9a/Aston_Villa_FC_new_crest.svg/330px-Aston_Villa_FC_new_crest.svg.png",
    "Real Madrid CF": "https://thumb.wikimedia.org/wikipedia/en/thumb/5/56/Real_Madrid_CF.svg/330px-Real_Madrid_CF.svg.png",
    "FC Barcelona": "https://thumb.wikimedia.org/wikipedia/en/thumb/4/47/FC_Barcelona_%28crest%29.svg/330px-FC_Barcelona_%28crest%29.svg.png",
    "Club Atlético de Madrid": "https://thumb.wikimedia.org/wikipedia/en/thumb/f/f9/Atletico_Madrid_Logo_2024.svg/330px-Atletico_Madrid_Logo_2024.svg.png",
    "Athletic Club": "https://thumb.wikimedia.org/wikipedia/en/thumb/9/98/Club_Athletic_Bilbao_logo.svg/330px-Club_Athletic_Bilbao_logo.svg.png",
    "FC Bayern München": "https://thumb.wikimedia.org/wikipedia/commons/thumb/8/8d/FC_Bayern_M%C3%BCnchen_logo_%282024%29.svg/330px-FC_Bayern_M%C3%BCnchen_logo_%282024%29.svg.png",
    "Borussia Dortmund": "https://thumb.wikimedia.org/wikipedia/commons/thumb/6/67/Borussia_Dortmund_logo.svg/330px-Borussia_Dortmund_logo.svg.png",
    "Bayer 04 Leverkusen": "https://thumb.wikimedia.org/wikipedia/en/thumb/5/59/Bayer_04_Leverkusen_logo.svg/330px-Bayer_04_Leverkusen_logo.svg.png",
    "RB Leipzig": "https://thumb.wikimedia.org/wikipedia/en/thumb/0/04/RB_Leipzig_2014_logo.svg/330px-RB_Leipzig_2014_logo.svg.png",
    "FC Internazionale Milano": "https://thumb.wikimedia.org/wikipedia/commons/thumb/0/05/FC_Internazionale_Milano_2021.svg/330px-FC_Internazionale_Milano_2021.svg.png",
    "AC Milan": "https://thumb.wikimedia.org/wikipedia/commons/thumb/d/d0/Logo_of_AC_Milan.svg/330px-Logo_of_AC_Milan.svg.png",
    "Juventus FC": "https://thumb.wikimedia.org/wikipedia/commons/thumb/e/ed/Juventus_FC_-_logo_black_%28Italy%2C_2020%29.svg/330px-Juventus_FC_-_logo_black_%28Italy%2C_2020%29.svg.png",
    "SSC Napoli": "https://thumb.wikimedia.org/wikipedia/commons/thumb/4/4d/SSC_Napoli_2025_%28white_and_azure%29.svg/330px-SSC_Napoli_2025_%28white_and_azure%29.svg.png",
    "AS Roma": "https://thumb.wikimedia.org/wikipedia/en/thumb/f/f7/AS_Roma_logo_%282017%29.svg/330px-AS_Roma_logo_%282017%29.svg.png",
    "Atalanta BC": "https://thumb.wikimedia.org/wikipedia/en/thumb/f/f2/Atalanta_BC_new_logo.svg/330px-Atalanta_BC_new_logo.svg.png",
    "Paris Saint-Germain FC": "https://thumb.wikimedia.org/wikipedia/en/thumb/a/a7/Paris_Saint-Germain_F.C..svg/330px-Paris_Saint-Germain_F.C..svg.png",
    "Olympique de Marseille": "https://thumb.wikimedia.org/wikipedia/commons/thumb/4/4f/Olympique_de_Marseille_2026_logo.svg/330px-Olympique_de_Marseille_2026_logo.svg.png",
    "AS Monaco FC": "https://thumb.wikimedia.org/wikipedia/en/thumb/c/cf/LogoASMonacoFC2021.svg/330px-LogoASMonacoFC2021.svg.png",
    "Olympique Lyonnais": "https://thumb.wikimedia.org/wikipedia/en/thumb/1/1c/Olympique_Lyonnais_logo.svg/330px-Olympique_Lyonnais_logo.svg.png",
    "France": "https://thumb.wikimedia.org/wikipedia/en/thumb/1/12/France_national_football_team_seal.svg/330px-France_national_football_team_seal.svg.png",
    "Argentina": "https://thumb.wikimedia.org/wikipedia/en/thumb/c/c1/Argentina_national_football_team_logo.svg/330px-Argentina_national_football_team_logo.svg.png",
    "Spain": "https://thumb.wikimedia.org/wikipedia/en/thumb/3/39/Spain_national_football_team_crest.svg/330px-Spain_national_football_team_crest.svg.png",
    "England": "https://thumb.wikimedia.org/wikipedia/en/thumb/8/8b/England_national_football_team_crest.svg/330px-England_national_football_team_crest.svg.png",
    "Germany": "https://thumb.wikimedia.org/wikipedia/en/thumb/e/e3/DFBEagle.svg/330px-DFBEagle.svg.png",
    "Brazil": "https://thumb.wikimedia.org/wikipedia/commons/thumb/3/32/Confedera%C3%A7%C3%A3o_Brasileira_de_Futebol_logo_%282020%29.svg/330px-Confedera%C3%A7%C3%A3o_Brasileira_de_Futebol_logo_%282020%29.svg.png",
    "Portugal": "https://thumb.wikimedia.org/wikipedia/en/thumb/e/e4/Portugal_national_football_team_logo.svg/330px-Portugal_national_football_team_logo.svg.png",
    "Netherlands": "https://thumb.wikimedia.org/wikipedia/en/thumb/7/78/Netherlands_national_football_team_logo.svg/330px-Netherlands_national_football_team_logo.svg.png",
    "Italy": "https://thumb.wikimedia.org/wikipedia/commons/thumb/b/bf/Logo_Italy_National_Football_Team_-_2023.svg/330px-Logo_Italy_National_Football_Team_-_2023.svg.png",
    "China PR": "https://thumb.wikimedia.org/wikipedia/en/thumb/0/03/China_national_football_team.svg/330px-China_national_football_team.svg.png",
    "Japan": "https://thumb.wikimedia.org/wikipedia/en/thumb/8/84/Japan_national_football_team_crest.svg/330px-Japan_national_football_team_crest.svg.png",
    "South Korea": "https://thumb.wikimedia.org/wikipedia/en/thumb/4/41/South_Korea_national_football_team.svg/330px-South_Korea_national_football_team.svg.png",
}

STAR_PLAYERS_CATALOG: list[dict[str, Any]] = [
    {
        "id": "p-mbappe", "name": "Kylian Mbappé", "zhName": "姆巴佩",
        "clubName": "Real Madrid CF", "clubZhName": "皇家马德里",
        "nationalName": "France", "nationalZhName": "法国国家队",
        "positionZh": "前锋", "nationalityZh": "法国", "shirtNumber": 9, "birthYear": 1998,
        "portraitUrl": "https://thumb.wikimedia.org/wikipedia/commons/thumb/9/95/Kylian_Mbappe_France_v_Senegal_16_June_2026-391_%28cropped%29.jpg/330px-Kylian_Mbappe_France_v_Senegal_16_June_2026-391_%28cropped%29.jpg",
    },
    {
        "id": "p-haaland", "name": "Erling Haaland", "zhName": "哈兰德",
        "clubName": "Manchester City FC", "clubZhName": "曼城",
        "nationalName": "Norway", "nationalZhName": "挪威国家队",
        "positionZh": "中锋", "nationalityZh": "挪威", "shirtNumber": 9, "birthYear": 2000,
        "portraitUrl": "https://thumb.wikimedia.org/wikipedia/commons/thumb/4/43/Erling_Haaland_Morocco_v_Norway_7_June_2026-51.jpg/330px-Erling_Haaland_Morocco_v_Norway_7_June_2026-51.jpg",
    },
    {
        "id": "p-yamal", "name": "Lamine Yamal", "zhName": "亚马尔",
        "clubName": "FC Barcelona", "clubZhName": "巴塞罗那",
        "nationalName": "Spain", "nationalZhName": "西班牙国家队",
        "positionZh": "右边锋", "nationalityZh": "西班牙", "shirtNumber": 19, "birthYear": 2007,
        "portraitUrl": "https://thumb.wikimedia.org/wikipedia/commons/thumb/1/13/Lamine_Yamal_France_v_Spain_7.24.26-142.jpg/330px-Lamine_Yamal_France_v_Spain_7.24.26-142.jpg",
    },
    {
        "id": "p-bellingham", "name": "Jude Bellingham", "zhName": "贝林厄姆",
        "clubName": "Real Madrid CF", "clubZhName": "皇家马德里",
        "nationalName": "England", "nationalZhName": "英格兰国家队",
        "positionZh": "前腰", "nationalityZh": "英格兰", "shirtNumber": 5, "birthYear": 2003,
        "portraitUrl": "https://thumb.wikimedia.org/wikipedia/commons/thumb/2/23/Jude_Bellingham_England_v_Ghana_23_June_2026-061_%28cropped%29.jpg/330px-Jude_Bellingham_England_v_Ghana_23_June_2026-061_%28cropped%29.jpg",
    },
    {
        "id": "p-vinicius", "name": "Vinícius Júnior", "zhName": "维尼修斯",
        "clubName": "Real Madrid CF", "clubZhName": "皇家马德里",
        "nationalName": "Brazil", "nationalZhName": "巴西国家队",
        "positionZh": "左边锋", "nationalityZh": "巴西", "shirtNumber": 7, "birthYear": 2000,
        "portraitUrl": "https://thumb.wikimedia.org/wikipedia/commons/thumb/d/df/Vin%C3%ADcius_J%C3%BAnior_%282024%29.jpg/330px-Vin%C3%ADcius_J%C3%BAnior_%282024%29.jpg",
    },
    {
        "id": "p-salah", "name": "Mohamed Salah", "zhName": "萨拉赫",
        "clubName": "Liverpool FC", "clubZhName": "利物浦",
        "nationalName": "Egypt", "nationalZhName": "埃及国家队",
        "positionZh": "右边锋", "nationalityZh": "埃及", "shirtNumber": 11, "birthYear": 1992,
        "portraitUrl": "https://thumb.wikimedia.org/wikipedia/commons/thumb/4/4a/Mohamed_Salah_2018.jpg/330px-Mohamed_Salah_2018.jpg",
    },
    {
        "id": "p-saka", "name": "Bukayo Saka", "zhName": "萨卡",
        "clubName": "Arsenal FC", "clubZhName": "阿森纳",
        "nationalName": "England", "nationalZhName": "英格兰国家队",
        "positionZh": "右边锋", "nationalityZh": "英格兰", "shirtNumber": 7, "birthYear": 2001,
        "portraitUrl": "https://thumb.wikimedia.org/wikipedia/commons/thumb/7/75/Bukayo_Saka_England_v_Ghana_23_June_2026-263_%28cropped%29.jpg/330px-Bukayo_Saka_England_v_Ghana_23_June_2026-263_%28cropped%29.jpg",
    },
    {
        "id": "p-kane", "name": "Harry Kane", "zhName": "凯恩",
        "clubName": "FC Bayern München", "clubZhName": "拜仁慕尼黑",
        "nationalName": "England", "nationalZhName": "英格兰国家队",
        "positionZh": "中锋", "nationalityZh": "英格兰", "shirtNumber": 9, "birthYear": 1993,
        "portraitUrl": "https://thumb.wikimedia.org/wikipedia/commons/thumb/4/4b/Harry_Kane_2023.jpg/330px-Harry_Kane_2023.jpg",
    },
    {
        "id": "p-musiala", "name": "Jamal Musiala", "zhName": "穆西亚拉",
        "clubName": "FC Bayern München", "clubZhName": "拜仁慕尼黑",
        "nationalName": "Germany", "nationalZhName": "德国国家队",
        "positionZh": "前腰", "nationalityZh": "德国", "shirtNumber": 42, "birthYear": 2003,
        "portraitUrl": "https://thumb.wikimedia.org/wikipedia/commons/thumb/a/a2/Jamal_Musiala_2022.jpg/330px-Jamal_Musiala_2022.jpg",
    },
    {
        "id": "p-lautaro", "name": "Lautaro Martínez", "zhName": "劳塔罗·马丁内斯",
        "clubName": "FC Internazionale Milano", "clubZhName": "国际米兰",
        "nationalName": "Argentina", "nationalZhName": "阿根廷国家队",
        "positionZh": "中锋", "nationalityZh": "阿根廷", "shirtNumber": 10, "birthYear": 1997,
        "portraitUrl": "https://thumb.wikimedia.org/wikipedia/commons/thumb/6/60/Lautaro_Mart%C3%ADnez_%28cropped%29.jpg/330px-Lautaro_Mart%C3%ADnez_%28cropped%29.jpg",
    },
    {
        "id": "p-palmer", "name": "Cole Palmer", "zhName": "帕尔默",
        "clubName": "Chelsea FC", "clubZhName": "切尔西",
        "nationalName": "England", "nationalZhName": "英格兰国家队",
        "positionZh": "前腰", "nationalityZh": "英格兰", "shirtNumber": 20, "birthYear": 2002,
        "portraitUrl": "https://thumb.wikimedia.org/wikipedia/commons/thumb/c/c8/Cole_Palmer_Chelsea_%28cropped%29.jpg/330px-Cole_Palmer_Chelsea_%28cropped%29.jpg",
    },
    {
        "id": "p-pedri", "name": "Pedri", "zhName": "佩德里",
        "clubName": "FC Barcelona", "clubZhName": "巴塞罗那",
        "nationalName": "Spain", "nationalZhName": "西班牙国家队",
        "positionZh": "中前卫", "nationalityZh": "西班牙", "shirtNumber": 8, "birthYear": 2002,
        "portraitUrl": "https://thumb.wikimedia.org/wikipedia/commons/thumb/3/3e/Pedri_2021.jpg/330px-Pedri_2021.jpg",
    },
    {
        "id": "p-dembele", "name": "Ousmane Dembélé", "zhName": "登贝莱",
        "clubName": "Paris Saint-Germain FC", "clubZhName": "巴黎圣日耳曼",
        "nationalName": "France", "nationalZhName": "法国国家队",
        "positionZh": "边锋", "nationalityZh": "法国", "shirtNumber": 10, "birthYear": 1997,
        "portraitUrl": "https://thumb.wikimedia.org/wikipedia/commons/thumb/a/a7/Ousmane_Demb%C3%A9l%C3%A9_2018.jpg/330px-Ousmane_Demb%C3%A9l%C3%A9_2018.jpg",
    },
    {
        "id": "p-messi", "name": "Lionel Messi", "zhName": "梅西",
        "clubName": "Inter Miami CF", "clubZhName": "迈阿密国际",
        "nationalName": "Argentina", "nationalZhName": "阿根廷国家队",
        "positionZh": "前锋", "nationalityZh": "阿根廷", "shirtNumber": 10, "birthYear": 1987,
        "portraitUrl": "https://thumb.wikimedia.org/wikipedia/commons/thumb/b/b4/Lionel-Messi-Argentina-2022-FIFA-World-Cup_%28cropped%29.jpg/330px-Lionel-Messi-Argentina-2022-FIFA-World-Cup_%28cropped%29.jpg",
    },
    {
        "id": "p-ronaldo", "name": "Cristiano Ronaldo", "zhName": "C罗",
        "clubName": "Al-Nassr FC", "clubZhName": "利雅得胜利",
        "nationalName": "Portugal", "nationalZhName": "葡萄牙国家队",
        "positionZh": "中锋", "nationalityZh": "葡萄牙", "shirtNumber": 7, "birthYear": 1985,
        "portraitUrl": "https://thumb.wikimedia.org/wikipedia/commons/thumb/d/d7/Cristiano_Ronaldo_playing_for_Al_Nassr_FC_against_Persepolis%2C_September_2023_%28cropped%29.jpg/330px-Cristiano_Ronaldo_playing_for_Al_Nassr_FC_against_Persepolis%2C_September_2023_%28cropped%29.jpg",
    },
    {
        "id": "p-son", "name": "Son Heung-min", "zhName": "孙兴慜",
        "clubName": "Tottenham Hotspur FC", "clubZhName": "热刺",
        "nationalName": "South Korea", "nationalZhName": "韩国国家队",
        "positionZh": "前锋", "nationalityZh": "韩国", "shirtNumber": 7, "birthYear": 1992,
        "portraitUrl": "https://thumb.wikimedia.org/wikipedia/commons/thumb/c/c1/Team_Korea_Russia_WorldCup_Son_Heung-min.jpg/330px-Team_Korea_Russia_WorldCup_Son_Heung-min.jpg",
    },
]

_PORTRAIT_BY_NAME = {_normalize_team_key(p["name"]): p["portraitUrl"] for p in STAR_PLAYERS_CATALOG}

STATUS_ZH_MAP: dict[str, str] = {
    "TIMED": "未开赛",
    "SCHEDULED": "待定开球",
    "LIVE": "进行中",
    "IN_PLAY": "进行中",
    "PAUSED": "中场休息",
    "FINISHED": "已完赛",
    "POSTPONED": "已延期",
    "SUSPENDED": "已暂停",
    "CANCELLED": "已取消",
}


@dataclasses.dataclass(frozen=True)
class NormalizedMatch:
    id: str
    competitionCode: str
    competitionName: str
    competitionZhName: str
    matchday: int | None
    stage: str
    utcDate: str
    status: str
    homeTeamId: int
    homeTeamName: str
    homeTeamShortName: str
    homeTeamZhName: str
    homeTeamCrest: str
    awayTeamId: int
    awayTeamName: str
    awayTeamShortName: str
    awayTeamZhName: str
    awayTeamCrest: str
    homeScore: int | None
    awayScore: int | None
    venue: str
    lastUpdatedUtc: str
    elapsedMinutes: int | None = None
    apiFootballFixtureId: int | None = None
    espnSlug: str | None = None
    espnEventId: str | None = None

    def to_dict(self) -> dict[str, Any]:
        return dataclasses.asdict(self)


def resolve_team_logo(team_name: str, api_crest: str | None = None) -> str:
    if api_crest and api_crest.strip():
        return api_crest.strip()
    return TEAM_LOGO_MAP.get(team_name, "")


def parse_utc_iso(iso_str: str) -> dt.datetime:
    cleaned = iso_str.strip()
    if cleaned.endswith("Z"):
        cleaned = cleaned[:-1] + "+00:00"
    parsed = dt.datetime.fromisoformat(cleaned)
    if parsed.tzinfo is None:
        return parsed.replace(tzinfo=dt.timezone.utc)
    return parsed.astimezone(dt.timezone.utc)


def format_utc_iso(ts: dt.datetime) -> str:
    return ts.astimezone(dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def determine_standing_zone(code: str, rank: int, total_rows: int) -> tuple[str, str]:
    """Returns (zoneType, zoneLabel) for league table classification."""
    c = code.upper()
    if c == "PL":
        if rank <= 4:
            return ("UCL", "欧冠正赛区")
        if rank == 5:
            return ("UEL", "欧联杯资格区")
        if total_rows >= 18 and rank >= total_rows - 2:
            return ("RELEGATION", "降级区")
    elif c == "PD":
        if rank <= 4:
            return ("UCL", "欧冠正赛区")
        if rank in (5, 6):
            return ("UEL", "欧联/欧协联区")
        if total_rows >= 18 and rank >= total_rows - 2:
            return ("RELEGATION", "降级区")
    elif c == "BL1":
        if rank <= 4:
            return ("UCL", "欧冠正赛区")
        if rank == 5:
            return ("UEL", "欧联杯区")
        if rank == 6:
            return ("UECL", "欧协联附加赛区")
        if total_rows >= 18 and rank == total_rows - 2:
            return ("RELEGATION_PLAYOFF", "升降级附加赛区")
        if total_rows >= 18 and rank >= total_rows - 1:
            return ("RELEGATION", "降级区")
    elif c == "SA":
        if rank <= 4:
            return ("UCL", "欧冠正赛区")
        if rank == 5:
            return ("UEL", "欧联杯区")
        if rank == 6:
            return ("UECL", "欧协联资格区")
        if total_rows >= 18 and rank >= total_rows - 2:
            return ("RELEGATION", "降级区")
    elif c == "FL1":
        if rank <= 3:
            return ("UCL", "欧冠正赛区")
        if rank == 4:
            return ("UCL_QUAL", "欧冠资格赛区")
        if rank == 5:
            return ("UEL", "欧联杯区")
        if rank == 6:
            return ("UECL", "欧协联附加赛区")
        if total_rows >= 18 and rank == total_rows - 2:
            return ("RELEGATION_PLAYOFF", "升降级附加赛区")
        if total_rows >= 18 and rank >= total_rows - 1:
            return ("RELEGATION", "降级区")
    elif c == "CL":
        if rank <= 8:
            return ("UCL", "16强直通区")
        if rank <= 24:
            return ("PLAYOFF", "淘汰赛附加赛区")
        return ("RELEGATION", "出局区")
    elif c == "ELC":
        if rank <= 2:
            return ("PROMOTION", "英超直通升级区")
        if rank <= 6:
            return ("PLAYOFF", "升级附加赛区")
        if total_rows >= 20 and rank >= total_rows - 2:
            return ("RELEGATION", "英甲降级区")
    elif c in {"DED", "PPL"}:
        if rank <= 2:
            return ("UCL", "欧冠正赛/资格区")
        if rank == 3:
            return ("UEL", "欧联资格区")
        if rank == 4:
            return ("UECL", "欧协联资格区")
        if total_rows >= 16 and rank == total_rows - 2:
            return ("RELEGATION_PLAYOFF", "保级附加赛区")
        if total_rows >= 16 and rank >= total_rows - 1:
            return ("RELEGATION", "降级区")
    elif c == "BSA":
        if rank <= 4:
            return ("UCL", "解放者杯正赛区")
        if rank <= 6:
            return ("UCL_QUAL", "解放者杯资格区")
        if rank <= 12:
            return ("UECL", "南美杯区")
        if total_rows >= 18 and rank >= total_rows - 3:
            return ("RELEGATION", "降级区")
    return ("NONE", "")


def normalize_api_match(raw: dict[str, Any], fallback_code: str) -> NormalizedMatch:
    comp = raw.get("competition") or {}
    comp_code = (comp.get("code") or fallback_code or "OTHER").upper()
    meta = COMPETITION_META.get(comp_code, {})
    comp_name = comp.get("name") or meta.get("name") or comp_code
    comp_zh = meta.get("zhName") or comp_name

    home = raw.get("homeTeam") or {}
    away = raw.get("awayTeam") or {}
    home_name = home.get("name") or "TBD"
    home_short = home.get("shortName") or home.get("tla") or home_name
    away_name = away.get("name") or "TBD"
    away_short = away.get("shortName") or away.get("tla") or away_name

    score_full = (raw.get("score") or {}).get("fullTime") or {}
    utc_raw = raw.get("utcDate") or format_utc_iso(dt.datetime.now(dt.timezone.utc))
    last_updated = raw.get("lastUpdated") or utc_raw

    return NormalizedMatch(
        id=f"fd-{raw.get('id')}",
        competitionCode=comp_code,
        competitionName=comp_name,
        competitionZhName=comp_zh,
        matchday=raw.get("matchday"),
        stage=raw.get("stage") or "REGULAR_SEASON",
        utcDate=format_utc_iso(parse_utc_iso(utc_raw)),
        status=(raw.get("status") or "SCHEDULED").upper(),
        homeTeamId=int(home.get("id") or 0),
        homeTeamName=home_name,
        homeTeamShortName=home_short,
        homeTeamZhName=localize_team(home_name, home_short),
        homeTeamCrest=resolve_team_logo(home_name, home.get("crest")),
        awayTeamId=int(away.get("id") or 0),
        awayTeamName=away_name,
        awayTeamShortName=away_short,
        awayTeamZhName=localize_team(away_name, away_short),
        awayTeamCrest=resolve_team_logo(away_name, away.get("crest")),
        homeScore=score_full.get("home"),
        awayScore=score_full.get("away"),
        venue=raw.get("venue") or "",
        lastUpdatedUtc=format_utc_iso(parse_utc_iso(last_updated)),
    )


def _map_af_status(short_status: str) -> str:
    s = (short_status or "").upper()
    if s in {"FT", "AET", "PEN"}:
        return "FINISHED"
    if s in {"1H", "2H", "ET", "P", "LIVE"}:
        return "LIVE"
    if s == "HT":
        return "PAUSED"
    if s in {"PST", "CANC", "ABD", "AWD", "WO"}:
        return "POSTPONED"
    if s == "SUSP":
        return "SUSPENDED"
    if s == "TBD":
        return "SCHEDULED"
    return "TIMED"


def normalize_af_fixture(item: dict[str, Any]) -> NormalizedMatch | None:
    league = item.get("league") or {}
    lid = int(league.get("id") or 0)
    code = API_FOOTBALL_LEAGUE_MAP.get(lid)
    if not code:
        return None
    meta = COMPETITION_META.get(code, {})
    fix = item.get("fixture") or {}
    fid = int(fix.get("id") or 0)
    date_iso = fix.get("date") or ""
    if not fid or not date_iso:
        return None
    status_obj = fix.get("status") or {}
    teams = item.get("teams") or {}
    home = teams.get("home") or {}
    away = teams.get("away") or {}
    home_name = home.get("name") or "TBD"
    away_name = away.get("name") or "TBD"
    goals = item.get("goals") or {}
    venue_obj = fix.get("venue") or {}
    venue_name = venue_obj.get("name") or ""
    round_str = league.get("round") or ""
    md = None
    for part in str(round_str).split():
        if part.isdigit():
            md = int(part)
            break

    return NormalizedMatch(
        id=f"af-{fid}",
        competitionCode=code,
        competitionName=league.get("name") or meta.get("name") or code,
        competitionZhName=meta.get("zhName") or league.get("name") or code,
        matchday=md,
        stage=round_str or "REGULAR_SEASON",
        utcDate=format_utc_iso(parse_utc_iso(date_iso)),
        status=_map_af_status(status_obj.get("short") or "NS"),
        homeTeamId=int(home.get("id") or 0),
        homeTeamName=home_name,
        homeTeamShortName=home_name,
        homeTeamZhName=localize_team(home_name),
        homeTeamCrest=resolve_team_logo(home_name, home.get("logo")),
        awayTeamId=int(away.get("id") or 0),
        awayTeamName=away_name,
        awayTeamShortName=away_name,
        awayTeamZhName=localize_team(away_name),
        awayTeamCrest=resolve_team_logo(away_name, away.get("logo")),
        homeScore=goals.get("home"),
        awayScore=goals.get("away"),
        venue=venue_name,
        lastUpdatedUtc=format_utc_iso(dt.datetime.now(dt.timezone.utc)),
        elapsedMinutes=status_obj.get("elapsed"),
        apiFootballFixtureId=fid,
    )


def fetch_json(url: str, headers: dict[str, str], timeout_seconds: int = 25) -> dict[str, Any]:
    req = urllib.request.Request(
        url,
        headers={
            "User-Agent": "FootballCalendarSync/3.0",
            **headers,
        },
    )
    with urllib.request.urlopen(req, timeout=timeout_seconds) as resp:
        return json.loads(resp.read().decode("utf-8"))


def fetch_fd_matches_batch(
    api_key: str,
    fd_codes: list[str],
    date_from: dt.date,
    date_to: dt.date,
    rate_limit_sleep: float,
) -> dict[str, NormalizedMatch]:
    collected: dict[str, NormalizedMatch] = {}
    comps_param = ",".join(fd_codes)
    cur = date_from
    first = True
    while cur < date_to:
        nxt = min(cur + dt.timedelta(days=9), date_to)
        if not first and rate_limit_sleep > 0:
            time.sleep(rate_limit_sleep)
        first = False
        params = urllib.parse.urlencode(
            {
                "competitions": comps_param,
                "dateFrom": cur.strftime("%Y-%m-%d"),
                "dateTo": nxt.strftime("%Y-%m-%d"),
            }
        )
        url = f"https://api.football-data.org/v4/matches?{params}"
        payload = fetch_json(url, {"X-Auth-Token": api_key})
        for m in payload.get("matches") or []:
            nm = normalize_api_match(m, "OTHER")
            collected[nm.id] = nm
        cur = nxt
    return collected


def fetch_competition_standings(api_key: str, competition_code: str) -> list[dict[str, Any]]:
    url = f"https://api.football-data.org/v4/competitions/{competition_code}/standings"
    payload = fetch_json(url, {"X-Auth-Token": api_key})
    standings_groups = payload.get("standings") or []
    total_table = next(
        (g.get("table") or [] for g in standings_groups if g.get("type") == "TOTAL"),
        (standings_groups[0].get("table") or []) if standings_groups else [],
    )
    total_rows = len(total_table)
    rows: list[dict[str, Any]] = []
    for entry in total_table:
        team = entry.get("team") or {}
        team_name = team.get("name") or "Unknown"
        team_short = team.get("shortName") or team.get("tla") or team_name
        rank = int(entry.get("position") or 0)
        z_type, z_label = determine_standing_zone(competition_code, rank, total_rows)
        rows.append(
            {
                "competitionCode": competition_code,
                "rank": rank,
                "teamName": team_name,
                "teamZhName": localize_team(team_name, team_short),
                "teamCrest": resolve_team_logo(team_name, team.get("crest")),
                "played": int(entry.get("playedGames") or 0),
                "won": int(entry.get("won") or 0),
                "draw": int(entry.get("draw") or 0),
                "lost": int(entry.get("lost") or 0),
                "goalDifference": int(entry.get("goalDifference") or 0),
                "points": int(entry.get("points") or 0),
                "form": entry.get("form") or "",
                "zoneType": z_type,
                "zoneLabel": z_label,
            }
        )
    return rows


def fetch_competition_teams_and_squads(api_key: str, competition_code: str) -> list[dict[str, Any]]:
    url = f"https://api.football-data.org/v4/competitions/{competition_code}/teams"
    payload = fetch_json(url, {"X-Auth-Token": api_key})
    result: list[dict[str, Any]] = []
    for t in payload.get("teams") or []:
        t_name = t.get("name") or "Unknown"
        t_short = t.get("shortName") or t.get("tla") or t_name
        coach = t.get("coach") or {}
        coach_name = coach.get("name") or ""
        coach_zh, _ = localize_player(coach_name)
        squad_rows: list[dict[str, Any]] = []
        for s in t.get("squad") or []:
            p_name = s.get("name") or ""
            if not p_name:
                continue
            p_zh, is_tr = localize_player(p_name)
            dob = s.get("dateOfBirth") or ""
            birth_year = int(dob[:4]) if len(dob) >= 4 and dob[:4].isdigit() else 2000
            squad_rows.append(
                {
                    "id": f"fdp-{s.get('id') or abs(hash(p_name))}",
                    "name": p_name,
                    "zhName": p_zh,
                    "isTransliterated": is_tr,
                    "position": s.get("position") or "",
                    "positionZh": localize_position(s.get("position")),
                    "nationality": s.get("nationality") or "",
                    "birthYear": birth_year,
                    "shirtNumber": int(s.get("shirtNumber") or 0),
                }
            )
        result.append(
            {
                "teamId": int(t.get("id") or 0),
                "competitionCode": competition_code,
                "name": t_name,
                "shortName": t_short,
                "zhName": localize_team(t_name, t_short),
                "crest": resolve_team_logo(t_name, t.get("crest")),
                "venue": t.get("venue") or "",
                "founded": int(t.get("founded") or 0),
                "clubColors": t.get("clubColors") or "",
                "coachName": coach_name,
                "coachZhName": coach_zh,
                "coachNationality": coach.get("nationality") or "",
                "squad": squad_rows,
            }
        )
    return result


def fetch_competition_scorers(api_key: str, competition_code: str, limit: int = 30) -> list[dict[str, Any]]:
    url = f"https://api.football-data.org/v4/competitions/{competition_code}/scorers?limit={limit}"
    payload = fetch_json(url, {"X-Auth-Token": api_key})
    players: list[dict[str, Any]] = []
    for idx, entry in enumerate(payload.get("scorers") or [], start=1):
        p = entry.get("player") or {}
        t = entry.get("team") or {}
        p_name = p.get("name") or "Unknown"
        p_zh, is_tr = localize_player(p_name)
        t_name = t.get("name") or ""
        t_short = t.get("shortName") or t.get("tla") or t_name
        dob = p.get("dateOfBirth") or ""
        birth_year = int(dob[:4]) if len(dob) >= 4 and dob[:4].isdigit() else 2000
        portrait = _PORTRAIT_BY_NAME.get(_normalize_team_key(p_name), "")
        players.append(
            {
                "id": f"fds-{competition_code}-{p.get('id') or idx}",
                "name": p_name,
                "zhName": p_zh,
                "isTransliterated": is_tr,
                "clubName": t_name,
                "clubZhName": localize_team(t_name, t_short),
                "clubCrestUrl": resolve_team_logo(t_name, t.get("crest")),
                "nationalName": p.get("nationality") or "",
                "nationalZhName": localize_team(p.get("nationality") or "") or (p.get("nationality") or ""),
                "nationalCrestUrl": resolve_team_logo(p.get("nationality") or ""),
                "positionZh": localize_position(p.get("section") or p.get("position")),
                "nationalityZh": p.get("nationality") or "",
                "shirtNumber": int(p.get("shirtNumber") or 0),
                "birthYear": birth_year,
                "portraitUrl": portrait,
                "competitionCode": competition_code,
                "rank": idx,
                "goals": int(entry.get("goals") or 0),
                "assists": int(entry.get("assists") or 0),
                "penalties": int(entry.get("penalties") or 0),
                "playedMatches": int(entry.get("playedMatches") or 0),
            }
        )
    return players


def generate_demo_matches(anchor_utc: dt.datetime | None = None) -> list[NormalizedMatch]:
    now = (anchor_utc or dt.datetime.now(dt.timezone.utc)).replace(
        minute=0, second=0, microsecond=0
    )
    base_day = dt.datetime(now.year, now.month, now.day, tzinfo=dt.timezone.utc)
    now_iso = format_utc_iso(now)

    templates: list[tuple[str, str, int, int, int, str, str, str, int | None, int | None, str]] = [
        ("demo-1001", "PL", -2, 16, 30, "Arsenal FC", "Chelsea FC", "FINISHED", 2, 1, "Emirates Stadium"),
        ("demo-1002", "PD", -1, 20, 0, "Real Madrid CF", "FC Barcelona", "FINISHED", 3, 2, "Santiago Bernabéu"),
        ("demo-1003", "PL", 0, 12, 30, "Liverpool FC", "Manchester City FC", "TIMED", None, None, "Anfield"),
        ("demo-1004", "ACL", 0, 11, 0, "Shanghai Port", "Kawasaki Frontale", "TIMED", None, None, "上汽浦东足球场"),
        ("demo-1005", "SA", 0, 19, 45, "FC Internazionale Milano", "AC Milan", "TIMED", None, None, "San Siro"),
        ("demo-1006", "J1", 0, 10, 0, "Vissel Kobe", "Yokohama F. Marinos", "TIMED", None, None, "Noevir Stadium Kobe"),
        ("demo-1007", "UEL", 1, 20, 0, "Manchester United FC", "AS Roma", "TIMED", None, None, "Old Trafford"),
        ("demo-1008", "UNL", 2, 19, 45, "France", "Spain", "TIMED", None, None, "Stade de France"),
        ("demo-1009", "WC", 3, 0, 0, "Argentina", "Brazil", "TIMED", None, None, "Estadio Monumental"),
        ("demo-1010", "U20AC", 3, 11, 35, "China PR U20", "Japan U20", "TIMED", None, None, "深圳青少年基地"),
        ("demo-1011", "U17WC", 4, 15, 0, "England U17", "Brazil U17", "TIMED", None, None, "Aspire Zone"),
        ("demo-1012", "EC", 4, 19, 45, "England", "Germany", "TIMED", None, None, "Wembley Stadium"),
    ]

    result: list[NormalizedMatch] = []
    for idx, (
        match_id, comp_code, day_offset, hour, minute,
        home_name, away_name, status, h_score, a_score, venue,
    ) in enumerate(templates, start=1):
        meta = COMPETITION_META[comp_code]
        kickoff = base_day + dt.timedelta(days=day_offset, hours=hour, minutes=minute)
        result.append(
            NormalizedMatch(
                id=match_id,
                competitionCode=comp_code,
                competitionName=meta["name"],
                competitionZhName=meta["zhName"],
                matchday=None if comp_code in INTERNATIONAL_CODES else 12 + (idx % 3),
                stage="INTERNATIONAL" if comp_code in INTERNATIONAL_CODES else "REGULAR_SEASON",
                utcDate=format_utc_iso(kickoff),
                status=status,
                homeTeamId=idx * 10 + 1,
                homeTeamName=home_name,
                homeTeamShortName=home_name.replace(" FC", "").replace(" CF", ""),
                homeTeamZhName=localize_team(home_name),
                homeTeamCrest=resolve_team_logo(home_name),
                awayTeamId=idx * 10 + 2,
                awayTeamName=away_name,
                awayTeamShortName=away_name.replace(" FC", "").replace(" CF", ""),
                awayTeamZhName=localize_team(away_name),
                awayTeamCrest=resolve_team_logo(away_name),
                homeScore=h_score,
                awayScore=a_score,
                venue=venue,
                lastUpdatedUtc=now_iso,
            )
        )
    return result


def generate_demo_standings() -> list[dict[str, Any]]:
    tables: dict[str, list[tuple[str, int, int, int, int, int, int, str]]] = {
        "PL": [
            ("Liverpool FC", 12, 10, 1, 1, 18, 31, "W,W,D,W,W"),
            ("Arsenal FC", 12, 8, 4, 0, 14, 28, "W,D,W,W,W"),
            ("Manchester City FC", 12, 8, 2, 2, 12, 26, "W,W,L,D,W"),
            ("Chelsea FC", 12, 7, 2, 3, 9, 23, "D,W,W,L,W"),
            ("Aston Villa FC", 12, 6, 2, 4, 5, 20, "W,L,D,W,D"),
            ("Newcastle United FC", 12, 5, 4, 3, 4, 19, "D,W,W,L,D"),
        ],
        "PD": [
            ("FC Barcelona", 13, 11, 0, 2, 22, 33, "W,W,W,L,W"),
            ("Real Madrid CF", 13, 9, 3, 1, 16, 30, "W,D,W,W,W"),
            ("Club Atlético de Madrid", 13, 7, 5, 1, 11, 26, "W,W,D,W,D"),
            ("Athletic Club", 13, 6, 5, 2, 7, 23, "D,W,D,W,W"),
        ],
        "BL1": [
            ("FC Bayern München", 11, 9, 2, 0, 24, 29, "W,W,W,D,W"),
            ("Bayer 04 Leverkusen", 11, 6, 5, 0, 12, 23, "D,W,D,W,W"),
            ("RB Leipzig", 11, 6, 3, 2, 8, 21, "W,L,W,D,W"),
            ("Borussia Dortmund", 11, 6, 1, 4, 5, 19, "W,L,W,W,L"),
        ],
        "SA": [
            ("SSC Napoli", 12, 9, 1, 2, 13, 28, "W,W,L,W,W"),
            ("FC Internazionale Milano", 12, 8, 3, 1, 15, 27, "D,W,W,W,D"),
            ("Atalanta BC", 12, 8, 1, 3, 14, 25, "W,W,W,W,L"),
            ("Juventus FC", 12, 6, 6, 0, 11, 24, "D,W,D,D,W"),
        ],
        "FL1": [
            ("Paris Saint-Germain FC", 12, 10, 2, 0, 23, 32, "W,W,W,D,W"),
            ("AS Monaco FC", 12, 8, 2, 2, 12, 26, "W,D,W,L,W"),
            ("Olympique de Marseille", 12, 7, 2, 3, 9, 23, "L,W,W,D,W"),
            ("Olympique Lyonnais", 12, 6, 3, 3, 6, 21, "W,D,W,W,L"),
        ],
        "CL": [
            ("Liverpool FC", 5, 5, 0, 0, 11, 15, "W,W,W,W,W"),
            ("FC Internazionale Milano", 5, 4, 1, 0, 7, 13, "D,W,W,W,W"),
            ("FC Barcelona", 5, 4, 0, 1, 10, 12, "L,W,W,W,W"),
            ("Arsenal FC", 5, 3, 1, 1, 6, 10, "W,D,W,L,W"),
        ],
    }

    rows: list[dict[str, Any]] = []
    for code, items in tables.items():
        total = len(items)
        for rank, (name, p, w, d, l, gd, pts, form) in enumerate(items, start=1):
            z_type, z_label = determine_standing_zone(code, rank, total)
            rows.append(
                {
                    "competitionCode": code,
                    "rank": rank,
                    "teamName": name,
                    "teamZhName": localize_team(name),
                    "teamCrest": resolve_team_logo(name),
                    "played": p,
                    "won": w,
                    "draw": d,
                    "lost": l,
                    "goalDifference": gd,
                    "points": pts,
                    "form": form,
                    "zoneType": z_type,
                    "zoneLabel": z_label,
                }
            )
    return rows


def enrich_players(extra_scorers: list[dict[str, Any]] | None = None) -> list[dict[str, Any]]:
    by_name: dict[str, dict[str, Any]] = {}
    ordered: list[dict[str, Any]] = []
    for s in (extra_scorers or []):
        key = _normalize_team_key(s["name"]) + "|" + s.get("competitionCode", "")
        if key not in by_name:
            by_name[key] = s
            ordered.append(s)

    seen_base_names = {_normalize_team_key(p["name"]) for p in ordered}
    for p in STAR_PLAYERS_CATALOG:
        base_key = _normalize_team_key(p["name"])
        item = dict(p)
        item["clubCrestUrl"] = resolve_team_logo(p["clubName"])
        item["nationalCrestUrl"] = resolve_team_logo(p["nationalName"])
        item.setdefault("goals", 0)
        item.setdefault("assists", 0)
        item.setdefault("penalties", 0)
        item.setdefault("playedMatches", 0)
        item.setdefault("competitionCode", "")
        item.setdefault("rank", 0)
        item.setdefault("isTransliterated", False)
        if base_key not in seen_base_names:
            ordered.append(item)
    return ordered


def ics_escape(value: str) -> str:
    return (
        value.replace("\\", "\\\\")
        .replace("\r\n", "\\n")
        .replace("\n", "\\n")
        .replace(",", "\\,")
        .replace(";", "\\;")
    )


def fold_ics_line(line: str, limit: int = 75) -> str:
    if len(line) <= limit:
        return line
    chunks = [line[:limit]]
    pos = limit
    while pos < len(line):
        chunks.append(" " + line[pos : pos + limit - 1])
        pos += limit - 1
    return "\r\n".join(chunks)


def build_ics(matches: list[NormalizedMatch], calendar_name: str, generated_at: dt.datetime) -> str:
    stamp = generated_at.astimezone(dt.timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    lines = [
        "BEGIN:VCALENDAR",
        "VERSION:2.0",
        "PRODID:-//Football Calendar//Auto Sync Feed v3//ZH",
        "CALSCALE:GREGORIAN",
        "METHOD:PUBLISH",
        f"X-WR-CALNAME:{ics_escape(calendar_name)}",
        "X-PUBLISHED-TTL:PT4H",
    ]
    for m in matches:
        start = parse_utc_iso(m.utcDate)
        end = start + dt.timedelta(minutes=110)
        summary_prefix = "[已延期] " if m.status in {"POSTPONED", "CANCELLED"} else ""
        score_txt = f" ({m.homeScore}:{m.awayScore})" if m.homeScore is not None and m.awayScore is not None else ""
        summary = f"{summary_prefix}[{m.competitionZhName}] {m.homeTeamZhName} ({m.homeTeamName}) vs {m.awayTeamZhName} ({m.awayTeamName}){score_txt}"
        lines.extend(
            [
                "BEGIN:VEVENT",
                f"UID:{ics_escape(m.id)}@football-calendar.app",
                f"DTSTAMP:{stamp}",
                f"DTSTART:{start.strftime('%Y%m%dT%H%M%SZ')}",
                f"DTEND:{end.strftime('%Y%m%dT%H%M%SZ')}",
                f"SUMMARY:{ics_escape(summary)}",
                f"DESCRIPTION:{ics_escape(f'赛事: {m.competitionZhName} ({m.competitionName})\\n状态: {STATUS_ZH_MAP.get(m.status, m.status)}')}",
                f"LOCATION:{ics_escape(m.venue or m.competitionName)}",
                f"STATUS:{'CANCELLED' if m.status in {'POSTPONED', 'CANCELLED'} else 'CONFIRMED'}",
                "END:VEVENT",
            ]
        )
    lines.append("END:VCALENDAR")
    return "\r\n".join(fold_ics_line(line) for line in lines) + "\r\n"


def _logo_img_html(url: str, label: str, size: int = 28) -> str:
    if not url:
        initial = html.escape(label[:1] or "⚽")
        return f'<span class="crest-fallback" style="width:{size}px;height:{size}px">{initial}</span>'
    return f'<img class="crest" src="{html.escape(url)}" alt="{html.escape(label)}" style="width:{size}px;height:{size}px" loading="lazy" />'


def build_index_html(
    matches: list[NormalizedMatch],
    standings: list[dict[str, Any]],
    players: list[dict[str, Any]],
    mode: str,
    generated_at: dt.datetime,
    competitions: list[str],
) -> str:
    bj_tz = dt.timezone(dt.timedelta(hours=8))
    gen_bj = generated_at.astimezone(bj_tz).strftime("%Y-%m-%d %H:%M UTC+8")
    mode_badge = (
        "Live API 实时同步中"
        if mode == "live_api"
        else "演示赛程（请在 GitHub Secrets 配置 API Key）"
    )

    cards_html: list[str] = []
    for m in matches[:80]:
        kickoff_bj = parse_utc_iso(m.utcDate).astimezone(bj_tz)
        date_str = kickoff_bj.strftime("%m-%d %H:%M")
        early_tag = '<span class="badge early">凌晨场</span>' if 0 <= kickoff_bj.hour < 6 else ""
        score_display = (
            f"{m.homeScore} : {m.awayScore}"
            if m.homeScore is not None and m.awayScore is not None
            else "VS"
        )
        status_zh = STATUS_ZH_MAP.get(m.status, m.status)
        status_cls = "postponed" if m.status in {"POSTPONED", "CANCELLED"} else "normal"
        is_nat = "true" if m.competitionCode in INTERNATIONAL_CODES else "false"
        cards_html.append(
            f"""
            <article class="card match-card" data-comp="{html.escape(m.competitionCode)}" data-nat="{is_nat}">
              <div class="card-top">
                <span class="league">{html.escape(m.competitionZhName)}</span>
                <span class="time tnum">{html.escape(date_str)} {early_tag}</span>
              </div>
              <div class="teams">
                <div class="team home">
                  {_logo_img_html(m.homeTeamCrest, m.homeTeamZhName, 28)}
                  <div>
                    <div>{html.escape(m.homeTeamZhName)}</div>
                    <div class="en-sub">{html.escape(m.homeTeamName)}</div>
                  </div>
                </div>
                <span class="score tnum">{html.escape(score_display)}</span>
                <div class="team away">
                  <div style="text-align:right">
                    <div>{html.escape(m.awayTeamZhName)}</div>
                    <div class="en-sub">{html.escape(m.awayTeamName)}</div>
                  </div>
                  {_logo_img_html(m.awayTeamCrest, m.awayTeamZhName, 28)}
                </div>
              </div>
              <div class="card-foot">
                <span class="status {status_cls}">{html.escape(status_zh)}</span>
                <span class="venue">{html.escape(m.venue or m.competitionName)}</span>
              </div>
            </article>
            """
        )

    standings_by_comp: dict[str, list[dict[str, Any]]] = {}
    for row in standings:
        standings_by_comp.setdefault(row["competitionCode"], []).append(row)

    standings_tables_html: list[str] = []
    available_st_codes = [c for c in STANDINGS_COMPETITIONS if standings_by_comp.get(c)]
    for idx, code in enumerate(available_st_codes):
        rows = standings_by_comp.get(code, [])
        tr_html = []
        for r in rows:
            tr_html.append(
                f"""
                <tr>
                  <td class="rank tnum">No.{r['rank']}</td>
                  <td class="tname">
                    {_logo_img_html(r.get('teamCrest', ''), r['teamZhName'], 20)}
                    <div>
                      <div>{html.escape(r['teamZhName'])}</div>
                      <div class="en-sub">{html.escape(r['teamName'])}</div>
                    </div>
                  </td>
                  <td class="tnum">{r['played']}</td>
                  <td class="tnum">{r['goalDifference']:+d}</td>
                  <td class="pts tnum">{r['points']}</td>
                </tr>
                """
            )
        hidden = "" if idx == 0 else 'style="display:none;"'
        standings_tables_html.append(
            f'<table class="st-table" data-st="{html.escape(code)}" {hidden}>'
            f'<thead><tr><th>No.</th><th>球队</th><th>赛</th><th>净胜</th><th>积分</th></tr></thead>'
            f'<tbody>{"".join(tr_html)}</tbody></table>'
        )

    players_html: list[str] = []
    for idx, p in enumerate(players[:40], start=1):
        rank_str = f"No.{p.get('rank') or idx}"
        stat_str = f" · ⚽{p.get('goals', 0)} 🎯{p.get('assists', 0)}" if p.get("goals") else ""
        portrait = p.get("portraitUrl") or p.get("clubCrestUrl") or ""
        players_html.append(
            f"""
            <article class="card player-card">
              <div class="p-top">
                {_logo_img_html(portrait, p['zhName'], 42)}
                <div>
                  <div class="p-name">{rank_str} {html.escape(p['zhName'])}</div>
                  <div class="en-sub">{html.escape(p['name'])}{html.escape(stat_str)}</div>
                </div>
              </div>
              <div class="p-teams">
                <span class="p-tag">{_logo_img_html(p.get('clubCrestUrl', ''), p.get('clubZhName', ''), 16)}{html.escape(p.get('clubZhName', ''))}</span>
              </div>
            </article>
            """
        )

    return f"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>足球赛事日历 · 自动更新中心</title>
  <style>
    :root {{ --red: #E11D48; --bg: #F6F7F9; --card: #FFFFFF; --ink: #111827; --sub: #6B7280; --border: #E5E7EB; }}
    * {{ box-sizing: border-box; }}
    body {{ margin: 0; font-family: -apple-system, BlinkMacSystemFont, "PingFang SC", "Helvetica Neue", sans-serif; background: var(--bg); color: var(--ink); }}
    .topbar {{ background: linear-gradient(135deg, #B91C1C, #E11D48); color: #fff; padding: 20px 24px; }}
    .topbar-inner {{ max-width: 1200px; margin: 0 auto; display: flex; flex-wrap: wrap; gap: 16px; align-items: center; justify-content: space-between; }}
    .brand {{ display: flex; gap: 12px; align-items: center; }}
    .brand h1 {{ margin: 0; font-size: 20px; font-weight: 800; }}
    .sub {{ font-size: 13px; opacity: 0.9; margin-top: 2px; }}
    .actions {{ display: flex; flex-wrap: wrap; gap: 8px; }}
    .btn {{ padding: 8px 14px; border-radius: 999px; border: 1px solid rgba(255,255,255,.35); background: rgba(255,255,255,.15); color: #fff; text-decoration: none; font-size: 13px; cursor: pointer; }}
    .btn.primary {{ background: #fff; color: #B91C1C; font-weight: 700; border-color: #fff; }}
    .wrap {{ max-width: 1200px; margin: 22px auto; padding: 0 16px; display: grid; grid-template-columns: 1.55fr 1fr; gap: 20px; }}
    @media (max-width: 900px) {{ .wrap {{ grid-template-columns: 1fr; }} }}
    .section-head {{ display: flex; flex-wrap: wrap; justify-content: space-between; align-items: center; gap: 10px; margin-bottom: 12px; }}
    .section-title {{ margin: 0; font-size: 18px; font-weight: 800; }}
    .filters {{ display: flex; flex-wrap: wrap; gap: 6px; }}
    .pill {{ border: 1px solid var(--border); background: #fff; color: var(--sub); padding: 5px 11px; border-radius: 999px; font-size: 12px; cursor: pointer; }}
    .pill.active {{ background: var(--red); border-color: var(--red); color: #fff; font-weight: 700; }}
    .grid {{ display: grid; grid-template-columns: 1fr; gap: 10px; }}
    .card {{ background: var(--card); border-radius: 16px; border: 1px solid var(--border); padding: 14px; }}
    .card-top, .card-foot {{ display: flex; justify-content: space-between; align-items: center; font-size: 12px; color: var(--sub); }}
    .league {{ font-weight: 700; color: var(--red); }}
    .teams {{ display: flex; align-items: center; justify-content: space-between; margin: 12px 0; gap: 8px; }}
    .team {{ display: flex; align-items: center; gap: 8px; flex: 1; font-weight: 700; font-size: 14px; }}
    .team.away {{ justify-content: flex-end; }}
    .en-sub {{ font-size: 11px; font-weight: 400; color: var(--sub); }}
    .score {{ font-weight: 800; font-size: 17px; padding: 0 8px; }}
    .crest {{ border-radius: 50%; object-fit: contain; background: #F9FAFB; }}
    .crest-fallback {{ display: inline-flex; align-items: center; justify-content: center; border-radius: 50%; background: #FEE2E2; color: #E11D48; font-weight: 700; font-size: 12px; }}
    .badge.early {{ background: #FEF3C7; color: #B45309; padding: 1px 6px; border-radius: 999px; font-size: 11px; }}
    .status.postponed {{ color: #DC2626; font-weight: 700; }}
    .st-table {{ width: 100%; border-collapse: collapse; font-size: 13px; }}
    .st-table th {{ text-align: left; color: var(--sub); font-size: 11px; padding: 6px 4px; border-bottom: 1px solid var(--border); }}
    .st-table td {{ padding: 7px 4px; border-bottom: 1px solid #F3F4F6; }}
    .tname {{ display: flex; align-items: center; gap: 6px; font-weight: 600; }}
    .pts {{ font-weight: 800; color: var(--red); }}
    .players-grid {{ display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 10px; }}
    .p-top {{ display: flex; gap: 10px; align-items: center; }}
    .p-name {{ font-weight: 700; font-size: 14px; }}
    .p-teams {{ margin-top: 8px; display: flex; flex-wrap: wrap; gap: 6px; font-size: 11px; color: var(--sub); }}
    .p-tag {{ display: inline-flex; align-items: center; gap: 4px; background: #F3F4F6; padding: 2px 8px; border-radius: 999px; }}
  </style>
</head>
<body>
  <header class="topbar">
    <div class="topbar-inner">
      <div class="brand">
        <div>
          <h1>足球赛事日历 · 自动更新中心</h1>
          <div class="sub">最近同步（北京时间）：{html.escape(gen_bj)} · {html.escape(mode_badge)}</div>
        </div>
      </div>
      <div class="actions">
        <a class="btn primary" href="./football.ics">📅 订阅全部赛程 (.ics)</a>
        <a class="btn" href="./fixtures.json">🔗 JSON 接口</a>
        <button class="btn" onclick="copyFeedUrl()">📋 复制 App 订阅地址</button>
      </div>
    </div>
  </header>

  <main class="wrap">
    <section>
      <div class="section-head">
        <h2 class="section-title">全赛事日历（俱乐部 · 国家队 · 青少年杯赛）</h2>
        <div class="filters" id="filters">
          <button class="pill active" data-filter="ALL">全部</button>
          <button class="pill" data-filter="NAT">国家队</button>
          {"".join(f'<button class="pill" data-filter="{html.escape(c)}">{html.escape(COMPETITION_META.get(c, {}).get("zhName", c))}</button>' for c in competitions if c not in INTERNATIONAL_CODES)}
        </div>
      </div>
      <div class="grid" id="cards">{"".join(cards_html)}</div>

      <div style="margin-top:28px;">
        <div class="section-head"><h2 class="section-title">球员榜（射手榜 · 球星 · 全队名单）</h2></div>
        <div class="players-grid">{"".join(players_html)}</div>
      </div>
    </section>

    <aside>
      <div class="section-head">
        <h2 class="section-title">联赛积分榜</h2>
        <div class="filters" id="st-filters">
          {"".join(f'<button class="pill {"active" if i == 0 else ""}" data-st="{html.escape(c)}">{html.escape(COMPETITION_META.get(c, {}).get("zhName", c))}</button>' for i, c in enumerate(available_st_codes))}
        </div>
      </div>
      <div class="card">{"".join(standings_tables_html)}</div>
    </aside>
  </main>

  <script>
    function copyFeedUrl() {{
      const url = new URL('./fixtures.json', window.location.href).href;
      navigator.clipboard.writeText(url).then(() => alert('已复制订阅地址：' + url));
    }}
    document.querySelectorAll('#filters button').forEach(btn => {{
      btn.addEventListener('click', () => {{
        document.querySelectorAll('#filters button').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        const target = btn.dataset.filter;
        document.querySelectorAll('#cards .match-card').forEach(card => {{
          const show = target === 'ALL'
            || (target === 'NAT' && card.dataset.nat === 'true')
            || card.dataset.comp === target;
          card.style.display = show ? 'block' : 'none';
        }});
      }});
    }});
    document.querySelectorAll('#st-filters button').forEach(btn => {{
      btn.addEventListener('click', () => {{
        document.querySelectorAll('#st-filters button').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        const target = btn.dataset.st;
        document.querySelectorAll('.st-table').forEach(tbl => {{
          tbl.style.display = tbl.dataset.st === target ? 'table' : 'none';
        }});
      }});
    }});
  </script>
</body>
</html>
"""


def write_feed_outputs(
    output_dir: pathlib.Path,
    matches: list[NormalizedMatch],
    standings: list[dict[str, Any]],
    players: list[dict[str, Any]],
    mode: str,
    competitions: list[str],
    generated_at: dt.datetime | None = None,
    teams: list[dict[str, Any]] | None = None,
    live_details: dict[str, Any] | None = None,
) -> dict[str, pathlib.Path]:
    now = (generated_at or dt.datetime.now(dt.timezone.utc)).astimezone(dt.timezone.utc)
    sorted_matches = sorted(matches, key=lambda m: (m.utcDate, m.competitionCode, m.id))

    output_dir.mkdir(parents=True, exist_ok=True)
    ics_dir = output_dir / "ics"
    ics_dir.mkdir(parents=True, exist_ok=True)

    payload = {
        "schemaVersion": 4,
        "generatedAtUtc": format_utc_iso(now),
        "mode": mode,
        "competitions": [
            {
                "code": c,
                "name": COMPETITION_META.get(c, {}).get("name", c),
                "zhName": COMPETITION_META.get(c, {}).get("zhName", c),
                "colorHex": COMPETITION_META.get(c, {}).get("color", "#E11D48"),
                "isInternational": c in INTERNATIONAL_CODES,
            }
            for c in competitions
        ],
        "matches": [m.to_dict() for m in sorted_matches],
        "standings": standings,
        "players": players,
        "teams": teams or [],
        "liveDetails": live_details or {},
    }

    fixtures_path = output_dir / "fixtures.json"
    fixtures_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")

    ics_path = output_dir / "football.ics"
    ics_path.write_text(build_ics(sorted_matches, "足球赛事日历 (全部)", now), encoding="utf-8")

    for code in competitions:
        comp_matches = [m for m in sorted_matches if m.competitionCode == code]
        comp_zh = COMPETITION_META.get(code, {}).get("zhName", code)
        (ics_dir / f"{code}.ics").write_text(
            build_ics(comp_matches, f"足球赛事 - {comp_zh}", now), encoding="utf-8"
        )

    index_path = output_dir / "index.html"
    index_path.write_text(
        build_index_html(sorted_matches, standings, players, mode, now, competitions),
        encoding="utf-8",
    )

    return {"fixtures_json": fixtures_path, "football_ics": ics_path, "index_html": index_path}


def _load_existing_feed(url: str | None) -> dict[str, Any]:
    if not url:
        return {}
    try:
        return fetch_json(url, {}, timeout_seconds=15)
    except Exception:  # noqa: BLE001
        return {}


def _fetch_af_team_detail(api_key: str, team_id: int, comp_code: str, fallback_name: str, fallback_crest: str) -> dict[str, Any]:
    venue_name = ""
    founded = 0
    colors = ""
    team_name = fallback_name
    crest = fallback_crest
    coach_name = ""

    try:
        t_resp = fetch_json(f"https://v3.football.api-sports.io/teams?id={team_id}", {"x-apisports-key": api_key})
        t_row = (t_resp.get("response") or [None])[0]
        if t_row:
            t_info = t_row.get("team") or {}
            v_info = t_row.get("venue") or {}
            team_name = t_info.get("name") or fallback_name
            crest = t_info.get("logo") or fallback_crest
            founded = int(t_info.get("founded") or 0)
            venue_name = v_info.get("name") or ""
    except Exception:  # noqa: BLE001
        pass

    try:
        c_resp = fetch_json(f"https://v3.football.api-sports.io/coachs?team={team_id}", {"x-apisports-key": api_key})
        c_rows = c_resp.get("response") or []
        if c_rows:
            coach_name = c_rows[0].get("name") or ""
    except Exception:  # noqa: BLE001
        pass

    squad_rows: list[dict[str, Any]] = []
    try:
        s_resp = fetch_json(f"https://v3.football.api-sports.io/players/squads?team={team_id}", {"x-apisports-key": api_key})
        s_players = ((s_resp.get("response") or [{}])[0]).get("players") or []
        for sp in s_players:
            p_name = sp.get("name") or ""
            if not p_name:
                continue
            p_zh, is_tr = localize_player(p_name)
            age = int(sp.get("age") or 0)
            birth_year = max(1975, dt.datetime.now().year - age) if age > 10 else 0
            squad_rows.append(
                {
                    "id": f"afp-{sp.get('id') or abs(hash(p_name))}",
                    "name": p_name,
                    "zhName": p_zh,
                    "isTransliterated": is_tr,
                    "position": sp.get("position") or "",
                    "positionZh": localize_position(sp.get("position")),
                    "nationality": "",
                    "birthYear": birth_year,
                    "shirtNumber": int(sp.get("number") or 0),
                    "portraitUrl": sp.get("photo") or "",
                }
            )
    except Exception:  # noqa: BLE001
        pass

    coach_zh, _ = localize_player(coach_name)
    return {
        "teamId": team_id,
        "competitionCode": comp_code,
        "name": team_name,
        "shortName": team_name,
        "zhName": localize_team(team_name),
        "crest": resolve_team_logo(team_name, crest),
        "venue": venue_name,
        "founded": founded,
        "clubColors": colors,
        "coachName": coach_name,
        "coachZhName": coach_zh,
        "coachNationality": "",
        "squad": squad_rows,
    }


def _map_espn_status(status_obj: dict[str, Any]) -> tuple[str, int | None]:
    stype = status_obj.get("type") or {}
    state = (stype.get("state") or "pre").lower()
    name = (stype.get("name") or "").upper()
    clock_raw = str(status_obj.get("displayClock") or "").strip()
    elapsed: int | None = None
    if clock_raw:
        digits = clock_raw.split("'")[0].replace("+", "").strip()
        if digits.isdigit():
            elapsed = int(digits)
    if "HALFTIME" in name:
        return ("PAUSED", elapsed)
    if "POSTPONED" in name:
        return ("POSTPONED", None)
    if "CANCELED" in name or "CANCELLED" in name:
        return ("CANCELLED", None)
    if "SUSPENDED" in name:
        return ("SUSPENDED", elapsed)
    if state == "in":
        return ("LIVE", elapsed)
    if state == "post":
        return ("FINISHED", elapsed)
    return ("TIMED", None)


def _merge_espn_match(
    nm: NormalizedMatch,
    collected: dict[str, NormalizedMatch],
    espn_index: dict[tuple[str, str], NormalizedMatch],
) -> None:
    pair = (_normalize_team_key(nm.homeTeamName), _normalize_team_key(nm.awayTeamName))
    espn_index[pair] = nm
    # Check if AF or FD already has this match
    matched_id = None
    for mid, cur in collected.items():
        if cur.competitionCode == nm.competitionCode and (
            _normalize_team_key(cur.homeTeamName),
            _normalize_team_key(cur.awayTeamName),
        ) == pair:
            matched_id = mid
            break
    if matched_id is None:
        collected[nm.id] = nm


def normalize_espn_event(ev: dict[str, Any], fallback_slug: str = "") -> NormalizedMatch | None:
    eid = str(ev.get("id") or "").strip()
    if not eid:
        return None
    slug = (fallback_slug or "").strip()
    if not slug:
        slug = ((ev.get("league") or {}).get("slug") or "").strip()
    if not slug:
        # Try deriving from league links if present inside event
        for lnk in ev.get("links") or []:
            href = lnk.get("href") or ""
            for s in ESPN_CODE_BY_SLUG:
                if f"/{s}/" in href:
                    slug = s
                    break
            if slug:
                break
    code = ESPN_CODE_BY_SLUG.get(slug)
    if not code:
        return None
    meta = COMPETITION_META.get(code, {})
    comps = ev.get("competitions") or []
    comp0 = comps[0] if comps else {}
    competitors = comp0.get("competitors") or []
    home_c = next((c for c in competitors if c.get("homeAway") == "home"), competitors[0] if len(competitors) > 0 else {})
    away_c = next((c for c in competitors if c.get("homeAway") == "away"), competitors[1] if len(competitors) > 1 else {})
    home_team = home_c.get("team") or {}
    away_team = away_c.get("team") or {}
    home_name = home_team.get("displayName") or home_team.get("name") or "TBD"
    away_name = away_team.get("displayName") or away_team.get("name") or "TBD"
    date_iso = ev.get("date") or ""
    if not date_iso:
        return None
    status_str, elapsed = _map_espn_status(comp0.get("status") or ev.get("status") or {})
    show_score = status_str in {"LIVE", "PAUSED", "FINISHED"}
    h_score = int(home_c["score"]) if show_score and str(home_c.get("score", "")).isdigit() else None
    a_score = int(away_c["score"]) if show_score and str(away_c.get("score", "")).isdigit() else None
    venue = ((comp0.get("venue") or {}).get("fullName")) or ""
    h_crest = home_team.get("logo") or ((home_team.get("logos") or [{}])[0].get("href") or "")
    a_crest = away_team.get("logo") or ((away_team.get("logos") or [{}])[0].get("href") or "")
    return NormalizedMatch(
        id=f"espn-{eid}",
        competitionCode=code,
        competitionName=meta.get("name") or code,
        competitionZhName=meta.get("zhName") or code,
        matchday=None,
        stage="INTERNATIONAL" if code in INTERNATIONAL_CODES else "REGULAR_SEASON",
        utcDate=format_utc_iso(parse_utc_iso(date_iso)),
        status=status_str,
        homeTeamId=int(home_team.get("id") or 0),
        homeTeamName=home_name,
        homeTeamShortName=home_team.get("shortDisplayName") or home_name,
        homeTeamZhName=localize_team(home_name, home_team.get("shortDisplayName")),
        homeTeamCrest=resolve_team_logo(home_name, h_crest),
        awayTeamId=int(away_team.get("id") or 0),
        awayTeamName=away_name,
        awayTeamShortName=away_team.get("shortDisplayName") or away_name,
        awayTeamZhName=localize_team(away_name, away_team.get("shortDisplayName")),
        awayTeamCrest=resolve_team_logo(away_name, a_crest),
        homeScore=h_score,
        awayScore=a_score,
        venue=venue,
        lastUpdatedUtc=format_utc_iso(dt.datetime.now(dt.timezone.utc)),
        elapsedMinutes=elapsed,
        espnSlug=slug,
        espnEventId=eid,
    )


def fetch_espn_standings(slug: str, comp_code: str) -> list[dict[str, Any]]:
    url = f"https://site.api.espn.com/apis/v2/sports/soccer/{slug}/standings"
    payload = fetch_json(url, {})
    children = payload.get("children") or [payload]
    rows: list[dict[str, Any]] = []
    for child in children:
        grp_name = localize_espn_group(child.get("name"))
        entries = (child.get("standings") or {}).get("entries") or []
        total_rows = len(entries)
        for idx, entry in enumerate(entries, start=1):
            team = entry.get("team") or {}
            t_name = team.get("displayName") or team.get("name") or "Unknown"
            t_short = team.get("shortDisplayName") or t_name
            logos = team.get("logos") or []
            crest = logos[0].get("href", "") if logos else ""
            stats = {s.get("name"): s for s in (entry.get("stats") or []) if s.get("name")}
            def _si(key: str, default: int = 0) -> int:
                s_obj = stats.get(key) or {}
                v = s_obj.get("value")
                if v is not None:
                    return int(round(float(v)))
                dv = str(s_obj.get("displayValue") or "").replace("+", "").strip()
                return int(dv) if dv.lstrip("-").isdigit() else default
            rank = _si("rank", idx)
            note_desc = (entry.get("note") or {}).get("description")
            z_type, z_label = localize_espn_zone(note_desc, comp_code, rank, total_rows)
            if z_type == "NONE":
                z_type, z_label = determine_standing_zone(comp_code, rank, total_rows)
            rows.append(
                {
                    "competitionCode": comp_code,
                    "groupName": grp_name,
                    "rank": rank,
                    "teamName": t_name,
                    "teamZhName": localize_team(t_name, t_short),
                    "teamCrest": resolve_team_logo(t_name, crest),
                    "played": _si("gamesPlayed"),
                    "won": _si("wins"),
                    "draw": _si("ties"),
                    "lost": _si("losses"),
                    "goalDifference": _si("pointDifferential"),
                    "points": _si("points"),
                    "form": "",
                    "zoneType": z_type,
                    "zoneLabel": z_label,
                }
            )
    return rows


def fetch_espn_teams_and_rosters(
    slug: str, comp_code: str, existing_by_key: dict[str, dict[str, Any]]
) -> list[dict[str, Any]]:
    payload = fetch_json(f"https://site.api.espn.com/apis/site/v2/sports/soccer/{slug}/teams", {})
    raw_teams = (((payload.get("sports") or [{}])[0].get("leagues") or [{}])[0].get("teams") or [])
    results: list[dict[str, Any]] = []
    targets: list[tuple[str, str, str, str]] = []
    for item in raw_teams:
        t = item.get("team") or {}
        tid = str(t.get("id") or "")
        t_name = t.get("displayName") or t.get("name") or ""
        t_short = t.get("shortDisplayName") or t_name
        logos = t.get("logos") or []
        crest = logos[0].get("href", "") if logos else ""
        if not tid or not t_name:
            continue
        tk = _normalize_team_key(t_name)
        cached = existing_by_key.get(tk)
        if cached and cached.get("squad"):
            continue
        targets.append((tid, t_name, t_short, crest))

    def _pull_roster(args: tuple[str, str, str, str]) -> dict[str, Any] | None:
        tid, t_name, t_short, crest = args
        squad_rows: list[dict[str, Any]] = []
        try:
            r_data = fetch_json(
                f"https://site.api.espn.com/apis/site/v2/sports/soccer/{slug}/teams/{tid}/roster",
                {},
                timeout_seconds=12,
            )
            for ath in r_data.get("athletes") or []:
                p_name = ath.get("displayName") or ath.get("fullName") or ""
                if not p_name:
                    continue
                p_zh, is_tr = localize_player(p_name)
                pos_obj = ath.get("position") or {}
                pos_en = pos_obj.get("displayName") or pos_obj.get("name") or ""
                age = int(ath.get("age") or 0)
                jersey_str = str(ath.get("jersey") or "")
                squad_rows.append(
                    {
                        "id": f"espnp-{ath.get('id') or abs(hash(p_name))}",
                        "name": p_name,
                        "zhName": p_zh,
                        "isTransliterated": is_tr,
                        "position": pos_en,
                        "positionZh": localize_position(pos_en),
                        "nationality": ((ath.get("citizenship") or "")),
                        "birthYear": max(1975, dt.datetime.now().year - age) if age > 10 else 0,
                        "shirtNumber": int(jersey_str) if jersey_str.isdigit() else 0,
                        "portraitUrl": ((ath.get("headshot") or {}).get("href") or ""),
                    }
                )
        except Exception:  # noqa: BLE001
            return None
        return {
            "teamId": int(tid) if tid.isdigit() else 0,
            "competitionCode": comp_code,
            "name": t_name,
            "shortName": t_short,
            "zhName": localize_team(t_name, t_short),
            "crest": resolve_team_logo(t_name, crest),
            "venue": "",
            "founded": 0,
            "clubColors": "",
            "coachName": "",
            "coachZhName": "",
            "coachNationality": "",
            "squad": squad_rows,
        }

    with ThreadPoolExecutor(max_workers=6) as pool:
        for res in pool.map(_pull_roster, targets[:36]):
            if res:
                results.append(res)
    return results


def fetch_espn_summary_detail(match_id: str, slug: str, event_id: str) -> dict[str, Any] | None:
    try:
        sm = fetch_json(
            f"https://site.api.espn.com/apis/site/v2/sports/soccer/{slug}/summary?event={event_id}",
            {},
            timeout_seconds=12,
        )
    except Exception:  # noqa: BLE001
        return None
    comp = ((sm.get("header") or {}).get("competitions") or [{}])[0]
    competitors = comp.get("competitors") or []
    home_c = next((c for c in competitors if c.get("homeAway") == "home"), competitors[0] if len(competitors) > 0 else {})
    away_c = next((c for c in competitors if c.get("homeAway") == "away"), competitors[1] if len(competitors) > 1 else {})
    home_id = str((home_c.get("team") or {}).get("id") or "")
    home_name = (home_c.get("team") or {}).get("displayName") or ""

    # Parse keyEvents for goals, cards & substitutions cleanly
    events: list[dict[str, Any]] = []
    for ke in sm.get("keyEvents") or []:
        t_text = ((ke.get("type") or {}).get("text") or "").strip()
        low_t = t_text.lower()
        if not any(k in low_t for k in ("goal", "card", "substitution", "penalty")):
            continue
        clock_str = ((ke.get("clock") or {}).get("displayValue") or "").strip()
        mins = 0
        extra: int | None = None
        if "'" in clock_str:
            parts_c = clock_str.replace("'", "").split("+")
            if parts_c[0].strip().isdigit():
                mins = int(parts_c[0].strip())
            if len(parts_c) > 1 and parts_c[1].strip().isdigit():
                extra = int(parts_c[1].strip())
        team_obj = ke.get("team") or {}
        t_id = str(team_obj.get("id") or "")
        t_name = team_obj.get("displayName") or ""
        parts = ke.get("participants") or []
        p_name = ((parts[0].get("athlete") or {}).get("displayName") or "") if len(parts) > 0 else ""
        a_name = ((parts[1].get("athlete") or {}).get("displayName") or "") if len(parts) > 1 else ""
        if "substitution" in low_t:
            ev_type = "subst"
            ev_detail = "换人调整"
        elif "red" in low_t:
            ev_type = "Card"
            ev_detail = "红牌"
        elif "yellow" in low_t:
            ev_type = "Card"
            ev_detail = "黄牌"
        else:
            ev_type = "Goal"
            ev_detail = "点球" if "penalty" in low_t else ("乌龙球" if "own" in low_t else "进球")
        if p_name:
            events.append(
                {
                    "elapsed": mins,
                    "extra": extra,
                    "teamName": t_name,
                    "teamZhName": localize_team(t_name),
                    "isHomeTeam": (home_id and t_id == home_id) or (t_name == home_name),
                    "playerName": p_name,
                    "playerZhName": localize_player(p_name)[0],
                    "assistName": a_name or None,
                    "assistZhName": localize_player(a_name)[0] if a_name else None,
                    "type": ev_type,
                    "detail": ev_detail,
                }
            )

    lineups: list[dict[str, Any]] = []
    for r in sm.get("rosters") or []:
        t_name = (r.get("team") or {}).get("displayName") or ""
        roster_list = r.get("roster") or []
        starters = []
        subs = []
        for item in roster_list:
            pn = ((item.get("athlete") or {}).get("displayName") or "").strip()
            if not pn:
                continue
            j = str(item.get("jersey") or "")
            pos = ((item.get("position") or {}).get("abbreviation") or "")
            slot = {
                "name": pn,
                "zhName": localize_player(pn)[0],
                "number": int(j) if j.isdigit() else 0,
                "pos": pos,
            }
            if item.get("starter"):
                starters.append(slot)
            else:
                subs.append(slot)
        lineups.append(
            {
                "teamName": t_name,
                "teamZhName": localize_team(t_name),
                "formation": r.get("formation") or "",
                "coachName": "",
                "coachZhName": "",
                "startXI": starters,
                "substitutes": subs,
            }
        )

    box_teams = (sm.get("boxscore") or {}).get("teams") or []
    stats_pairs: list[dict[str, str]] = []
    if len(box_teams) >= 2:
        hm = {s.get("name"): str(s.get("displayValue") or "") for s in (box_teams[0].get("statistics") or [])}
        am = {s.get("name"): str(s.get("displayValue") or "") for s in (box_teams[1].get("statistics") or [])}
        if box_teams[0].get("team", {}).get("displayName") != home_name and box_teams[1].get("team", {}).get("displayName") == home_name:
            hm, am = am, hm
        for raw_k, zh_label in (
            ("possessionPct", "控球率"),
            ("totalShots", "射门次数"),
            ("shotsOnTarget", "射正次数"),
            ("wonCorners", "角球"),
            ("passPct", "传球成功率"),
            ("foulsCommitted", "犯规"),
            ("yellowCards", "黄牌"),
            ("offsides", "越位"),
        ):
            hv, av = hm.get(raw_k), am.get(raw_k)
            if hv is not None and av is not None:
                if raw_k == "possessionPct":
                    hv, av = f"{hv}%", f"{av}%"
                elif raw_k == "passPct":
                    try:
                        if float(hv) <= 1.0:
                            hv = f"{round(float(hv) * 100)}%"
                        else:
                            hv = f"{hv}%"
                        if float(av) <= 1.0:
                            av = f"{round(float(av) * 100)}%"
                        else:
                            av = f"{av}%"
                    except ValueError:
                        pass
                stats_pairs.append({"labelZh": zh_label, "homeValue": hv, "awayValue": av})

    status_str, elapsed = _map_espn_status(comp.get("status") or {})
    h_sc = int(home_c["score"]) if str(home_c.get("score", "")).isdigit() else None
    a_sc = int(away_c["score"]) if str(away_c.get("score", "")).isdigit() else None
    return {
        "matchId": match_id,
        "fixtureId": 0,
        "espnEventId": event_id,
        "elapsedMinutes": elapsed,
        "statusShort": status_str,
        "homeScore": h_sc,
        "awayScore": a_sc,
        "events": events,
        "lineups": lineups,
        "stats": stats_pairs,
    }


def collect_data(
    api_key: str | None,
    competitions: list[str],
    days_back: int = 3,
    days_ahead: int = 21,
    rate_limit_sleep: float = 6.2,
    api_football_key: str | None = None,
    existing_feed_url: str | None = None,
    enable_espn: bool = True,
) -> tuple[list[NormalizedMatch], list[dict[str, Any]], list[dict[str, Any]], list[dict[str, Any]], dict[str, Any], str]:
    now = dt.datetime.now(dt.timezone.utc)
    if not api_key and not api_football_key and not enable_espn:
        return generate_demo_matches(now), generate_demo_standings(), [], enrich_players(), {}, "demo_fallback"
    if not api_key and not api_football_key and rate_limit_sleep == 0:
        return generate_demo_matches(now), generate_demo_standings(), [], enrich_players(), {}, "demo_fallback"

    existing = _load_existing_feed(existing_feed_url)
    collected: dict[str, NormalizedMatch] = {}
    standings: list[dict[str, Any]] = []
    teams_by_key: dict[str, dict[str, Any]] = {}
    scorers_list: list[dict[str, Any]] = []
    errors: list[str] = []

    # Restore cached team squads/details so we don't repeat expensive calls unnecessarily
    for td in existing.get("teams") or []:
        t_key = _normalize_team_key(td.get("name") or "")
        if t_key:
            teams_by_key[t_key] = td

    # Carry forward recent AF & ESPN fixtures within [-10d, +21d]
    cutoff_old = (now - dt.timedelta(days=10)).isoformat()
    for em in existing.get("matches") or []:
        eid = str(em.get("id") or "")
        if (eid.startswith("af-") or eid.startswith("espn-")) and str(em.get("utcDate") or "") >= cutoff_old:
            if em.get("competitionCode") in competitions:
                collected[eid] = NormalizedMatch(
                    id=eid,
                    competitionCode=em.get("competitionCode", "OTHER"),
                    competitionName=em.get("competitionName", ""),
                    competitionZhName=em.get("competitionZhName", ""),
                    matchday=em.get("matchday"),
                    stage=em.get("stage", "REGULAR_SEASON"),
                    utcDate=em.get("utcDate", ""),
                    status=em.get("status", "TIMED"),
                    homeTeamId=int(em.get("homeTeamId") or 0),
                    homeTeamName=em.get("homeTeamName", ""),
                    homeTeamShortName=em.get("homeTeamShortName", ""),
                    homeTeamZhName=em.get("homeTeamZhName", ""),
                    homeTeamCrest=em.get("homeTeamCrest", ""),
                    awayTeamId=int(em.get("awayTeamId") or 0),
                    awayTeamName=em.get("awayTeamName", ""),
                    awayTeamShortName=em.get("awayTeamShortName", ""),
                    awayTeamZhName=em.get("awayTeamZhName", ""),
                    awayTeamCrest=em.get("awayTeamCrest", ""),
                    homeScore=em.get("homeScore"),
                    awayScore=em.get("awayScore"),
                    venue=em.get("venue", ""),
                    lastUpdatedUtc=em.get("lastUpdatedUtc", ""),
                    elapsedMinutes=em.get("elapsedMinutes"),
                    apiFootballFixtureId=em.get("apiFootballFixtureId"),
                    espnSlug=em.get("espnSlug"),
                    espnEventId=em.get("espnEventId"),
                )

    # 1. Fetch Football-Data.org (matches batch, full standings, full teams+squads, scorers)
    fd_codes = [c for c in competitions if c in FD_COMPETITIONS]
    if api_key and fd_codes:
        try:
            date_from = (now - dt.timedelta(days=days_back)).date()
            date_to = (now + dt.timedelta(days=days_ahead)).date()
            collected.update(fetch_fd_matches_batch(api_key, fd_codes, date_from, date_to, rate_limit_sleep))
        except Exception as exc:  # noqa: BLE001
            errors.append(f"FD matches batch: {exc}")

        for code in [c for c in STANDINGS_COMPETITIONS if c in fd_codes]:
            if rate_limit_sleep > 0:
                time.sleep(rate_limit_sleep)
            try:
                standings.extend(fetch_competition_standings(api_key, code))
            except Exception as exc:  # noqa: BLE001
                errors.append(f"{code} standings: {exc}")

        for code in [c for c in ["PL", "PD", "BL1", "SA", "FL1", "CL", "ELC", "DED", "PPL", "BSA"] if c in fd_codes]:
            if rate_limit_sleep > 0:
                time.sleep(rate_limit_sleep)
            try:
                scorers_list.extend(fetch_competition_scorers(api_key, code, limit=25))
            except Exception as exc:  # noqa: BLE001
                errors.append(f"{code} scorers: {exc}")

        for code in [c for c in ["PL", "PD", "BL1", "SA", "FL1", "CL", "ELC", "DED", "PPL", "BSA"] if c in fd_codes]:
            if rate_limit_sleep > 0:
                time.sleep(rate_limit_sleep)
            try:
                for td in fetch_competition_teams_and_squads(api_key, code):
                    t_key = _normalize_team_key(td["name"])
                    if t_key:
                        teams_by_key[t_key] = td
            except Exception as exc:  # noqa: BLE001
                errors.append(f"{code} teams: {exc}")

    # 2. Fetch API-Football Free window (yesterday, today, tomorrow -> 3 API calls)
    #    Covers ACL Elite, J1, J2, K League 1, A-League, MLS, UEL, UECL, UNL, U17 WC, U20 Asian Cup, etc.
    if api_football_key:
        af_by_pair: dict[tuple[str, str], NormalizedMatch] = {}
        for day_delta in (-1, 0, 1):
            date_str = (now + dt.timedelta(days=day_delta)).strftime("%Y-%m-%d")
            try:
                payload = fetch_json(
                    f"https://v3.football.api-sports.io/fixtures?date={date_str}",
                    {"x-apisports-key": api_football_key},
                )
                for raw_fix in payload.get("response") or []:
                    nm = normalize_af_fixture(raw_fix)
                    if nm and nm.competitionCode in competitions:
                        pair = (
                            _normalize_team_key(nm.homeTeamName),
                            _normalize_team_key(nm.awayTeamName),
                        )
                        af_by_pair[pair] = nm
                        if nm.competitionCode not in fd_codes:
                            collected[nm.id] = nm
            except Exception as exc:  # noqa: BLE001
                errors.append(f"AF date {date_str}: {exc}")

        # Attach apiFootballFixtureId onto FD matches + update live score if fresher
        for mid, fm in list(collected.items()):
            if mid.startswith("fd-"):
                pair = (
                    _normalize_team_key(fm.homeTeamName),
                    _normalize_team_key(fm.awayTeamName),
                )
                af_match = af_by_pair.get(pair)
                if af_match:
                    collected[mid] = dataclasses.replace(
                        fm,
                        apiFootballFixtureId=af_match.apiFootballFixtureId,
                        elapsedMinutes=af_match.elapsedMinutes,
                        homeScore=af_match.homeScore if af_match.status in {"LIVE", "PAUSED", "FINISHED"} and af_match.homeScore is not None else fm.homeScore,
                        awayScore=af_match.awayScore if af_match.status in {"LIVE", "PAUSED", "FINISHED"} and af_match.awayScore is not None else fm.awayScore,
                        status=af_match.status if af_match.status in {"LIVE", "PAUSED", "FINISHED"} else fm.status,
                    )

        # Progressively fetch squads/coaches/venues for uncached AF teams (cap 3 teams = max 9 calls/run)
        team_candidates: list[tuple[int, str, str, str]] = []
        for m in collected.values():
            if m.id.startswith("af-"):
                if m.homeTeamId > 0 and _normalize_team_key(m.homeTeamName) not in teams_by_key:
                    team_candidates.append((m.homeTeamId, m.competitionCode, m.homeTeamName, m.homeTeamCrest))
                if m.awayTeamId > 0 and _normalize_team_key(m.awayTeamName) not in teams_by_key:
                    team_candidates.append((m.awayTeamId, m.competitionCode, m.awayTeamName, m.awayTeamCrest))
        seen_ids: set[int] = set()
        fetched_count = 0
        for tid, c_code, t_name, t_crest in team_candidates:
            if tid in seen_ids or fetched_count >= 3:
                continue
            seen_ids.add(tid)
            td = _fetch_af_team_detail(api_football_key, tid, c_code, t_name, t_crest)
            t_key = _normalize_team_key(td["name"])
            if t_key:
                teams_by_key[t_key] = td
                fetched_count += 1

    # 3. Fetch ESPN (schedules across UEL/UECL/ACL/J1/AL/MLS/WC/EC/UNL/U17WC + 2026 Standings + Rosters + Live Details)
    live_details: dict[str, Any] = {}
    if enable_espn:
        espn_events_index: dict[tuple[str, str], NormalizedMatch] = {}
        try:
            # Multi-day sweep via `all/scoreboard?dates=YYYYMMDD` + league scoreboards
            for delta in range(-days_back, min(days_ahead, 10) + 1):
                ds = (now + dt.timedelta(days=delta)).strftime("%Y%m%d")
                try:
                    sb = fetch_json(
                        f"https://site.api.espn.com/apis/site/v2/sports/soccer/all/scoreboard?dates={ds}",
                        {},
                        timeout_seconds=15,
                    )
                    for ev in sb.get("events") or []:
                        ev_slug = ((ev.get("league") or {}).get("slug") or "").strip()
                        nm = normalize_espn_event(ev, ev_slug)
                        if nm and nm.competitionCode in competitions:
                            _merge_espn_match(nm, collected, espn_events_index)
                except Exception:  # noqa: BLE001
                    pass

            # Current round per non-FD / key ESPN league slug
            for code in competitions:
                slug = ESPN_SLUG_BY_CODE.get(code)
                if not slug:
                    continue
                if code in fd_codes and code not in {"PL", "PD", "BL1", "SA", "FL1", "CL", "ELC", "DED", "PPL", "BSA"}:
                    continue
                try:
                    sb = fetch_json(
                        f"https://site.api.espn.com/apis/site/v2/sports/soccer/{slug}/scoreboard",
                        {},
                        timeout_seconds=15,
                    )
                    for ev in sb.get("events") or []:
                        nm = normalize_espn_event(ev, slug)
                        if nm and nm.competitionCode in competitions:
                            _merge_espn_match(nm, collected, espn_events_index)
                except Exception as exc:  # noqa: BLE001
                    errors.append(f"ESPN {slug} scoreboard: {exc}")

            # Attach espnSlug & espnEventId onto existing FD/AF matches + sync fresher score
            for mid, m in list(collected.items()):
                pair = (_normalize_team_key(m.homeTeamName), _normalize_team_key(m.awayTeamName))
                em = espn_events_index.get(pair)
                if em:
                    collected[mid] = dataclasses.replace(
                        m,
                        espnSlug=em.espnSlug or m.espnSlug,
                        espnEventId=em.espnEventId or m.espnEventId,
                        elapsedMinutes=em.elapsedMinutes if em.elapsedMinutes is not None else m.elapsedMinutes,
                        homeScore=em.homeScore if em.status in {"LIVE", "PAUSED", "FINISHED"} and em.homeScore is not None else m.homeScore,
                        awayScore=em.awayScore if em.status in {"LIVE", "PAUSED", "FINISHED"} and em.awayScore is not None else m.awayScore,
                        status=em.status if em.status in {"LIVE", "PAUSED", "FINISHED"} else m.status,
                    )

            # ESPN 2026 Standings for non-FD leagues (UEL, UECL, ACL, J1, AL, MLS)
            existing_st_codes = {row["competitionCode"] for row in standings}
            for code in ESPN_STANDINGS_CODES:
                slug = ESPN_SLUG_BY_CODE.get(code)
                if slug and code in competitions and code not in existing_st_codes:
                    try:
                        standings.extend(fetch_espn_standings(slug, code))
                    except Exception as exc:  # noqa: BLE001
                        errors.append(f"ESPN {slug} standings: {exc}")

            # ESPN Teams & Rosters for non-FD standings leagues
            for code in ESPN_STANDINGS_CODES:
                slug = ESPN_SLUG_BY_CODE.get(code)
                if slug and code in competitions:
                    try:
                        for td in fetch_espn_teams_and_rosters(slug, code, teams_by_key):
                            tk = _normalize_team_key(td["name"])
                            if tk:
                                teams_by_key[tk] = td
                    except Exception as exc:  # noqa: BLE001
                        errors.append(f"ESPN {slug} teams: {exc}")

            # Pre-cache recent finished & live match details from ESPN summary for 0ms open
            recent_targets = [
                m for m in collected.values()
                if m.espnSlug and m.espnEventId and m.status in {"LIVE", "PAUSED", "FINISHED"}
            ][:40]
            if recent_targets:
                with ThreadPoolExecutor(max_workers=6) as pool:
                    futures = [
                        pool.submit(fetch_espn_summary_detail, m.id, m.espnSlug, m.espnEventId)
                        for m in recent_targets
                        if m.espnSlug and m.espnEventId
                    ]
                    for fut in futures:
                        det = fut.result()
                        if det:
                            live_details[det["matchId"]] = det
        except Exception as exc:  # noqa: BLE001
            errors.append(f"ESPN integration: {exc}")

    if not collected:
        print(f"[warn] No live matches fetched ({errors}); falling back to demo feed.", file=sys.stderr)
        return generate_demo_matches(now), generate_demo_standings(), [], enrich_players(), {}, "demo_fallback"

    if not standings:
        standings = generate_demo_standings()
    if errors:
        print(f"[warn] Partial API fetch warnings: {errors}", file=sys.stderr)
    return (
        list(collected.values()),
        standings,
        list(teams_by_key.values()),
        enrich_players(scorers_list),
        live_details,
        "live_api",
    )


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Generate Football Calendar JSON/ICS/Pages outputs.")
    parser.add_argument("--output-dir", default="_site", help="Output directory for generated assets.")
    parser.add_argument(
        "--competitions",
        default=",".join(DEFAULT_COMPETITIONS),
        help="Comma-separated competition codes.",
    )
    parser.add_argument("--days-back", type=int, default=3)
    parser.add_argument("--days-ahead", type=int, default=21)
    parser.add_argument("--sleep", type=float, default=6.2)
    parser.add_argument("--existing-feed-url", default="", help="Previous fixtures.json URL to carry over AF history & team caches.")
    parser.add_argument("--no-espn", action="store_true", help="Disable live ESPN HTTP fetches (e.g. offline test mode).")
    args = parser.parse_args(argv)

    competitions = [c.strip().upper() for c in args.competitions.split(",") if c.strip()]
    api_key = os.environ.get("FOOTBALL_DATA_API_KEY", "").strip() or None
    af_key = os.environ.get("API_FOOTBALL_KEY", "").strip() or None

    matches, standings, teams, players, live_details, mode = collect_data(
        api_key=api_key,
        competitions=competitions,
        days_back=args.days_back,
        days_ahead=args.days_ahead,
        rate_limit_sleep=args.sleep,
        api_football_key=af_key,
        existing_feed_url=args.existing_feed_url.strip() or None,
        enable_espn=not args.no_espn,
    )
    outputs = write_feed_outputs(
        output_dir=pathlib.Path(args.output_dir),
        matches=matches,
        standings=standings,
        players=players,
        mode=mode,
        competitions=competitions,
        teams=teams,
        live_details=live_details,
    )
    print(
        f"Generated {len(matches)} matches, {len(standings)} standing rows, "
        f"{len(teams)} teams, {len(players)} ranked players, {len(live_details)} pre-cached match details in mode={mode}: {outputs['fixtures_json']}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
