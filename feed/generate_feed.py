#!/usr/bin/env python3
"""Football match schedule, standings, and star players feed generator (v2).

Outputs into --output-dir:
  - fixtures.json : Normalized feed (matches with team crests, standings, star players)
  - football.ics  : Combined RFC 5545 iCalendar subscription file
  - ics/<CODE>.ics: Per-competition iCalendar subscription files
  - index.html    : Apple Red & White web dashboard with schedule, standings & stars
"""

from __future__ import annotations

import argparse
import dataclasses
import datetime as dt
import html
import json
import os
import pathlib
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from typing import Any

DEFAULT_COMPETITIONS = ["PL", "PD", "BL1", "SA", "FL1", "CL", "WC", "EC", "INT"]
STANDINGS_COMPETITIONS = ["PL", "PD", "BL1", "SA", "FL1", "CL"]

COMPETITION_META: dict[str, dict[str, str]] = {
    "PL": {"name": "Premier League", "zhName": "英超", "country": "英格兰", "color": "#E11D48"},
    "PD": {"name": "Primera Division", "zhName": "西甲", "country": "西班牙", "color": "#EA580C"},
    "BL1": {"name": "Bundesliga", "zhName": "德甲", "country": "德国", "color": "#DC2626"},
    "SA": {"name": "Serie A", "zhName": "意甲", "country": "意大利", "color": "#2563EB"},
    "FL1": {"name": "Ligue 1", "zhName": "法甲", "country": "法国", "color": "#0284C7"},
    "CL": {"name": "UEFA Champions League", "zhName": "欧冠", "country": "欧洲", "color": "#1D4ED8"},
    "WC": {"name": "FIFA World Cup", "zhName": "世界杯", "country": "国家队", "color": "#D97706"},
    "EC": {"name": "UEFA European Championship", "zhName": "欧洲杯", "country": "国家队", "color": "#BE123C"},
    "INT": {"name": "International Match", "zhName": "国家队", "country": "国际赛事", "color": "#E11D48"},
}

TEAM_ZH_MAP: dict[str, str] = {
    "Arsenal FC": "阿森纳",
    "Arsenal": "阿森纳",
    "Manchester City FC": "曼城",
    "Man City": "曼城",
    "Liverpool FC": "利物浦",
    "Liverpool": "利物浦",
    "Chelsea FC": "切尔西",
    "Chelsea": "切尔西",
    "Manchester United FC": "曼联",
    "Man United": "曼联",
    "Tottenham Hotspur FC": "热刺",
    "Tottenham": "热刺",
    "Newcastle United FC": "纽卡斯尔联",
    "Newcastle": "纽卡斯尔联",
    "Aston Villa FC": "阿斯顿维拉",
    "Aston Villa": "阿斯顿维拉",
    "Brighton & Hove Albion FC": "布莱顿",
    "Brighton Hove": "布莱顿",
    "Brighton": "布莱顿",
    "AFC Bournemouth": "伯恩茅斯",
    "Bournemouth": "伯恩茅斯",
    "Brentford FC": "布伦特福德",
    "Brentford": "布伦特福德",
    "Fulham FC": "富勒姆",
    "Fulham": "富勒姆",
    "Everton FC": "埃弗顿",
    "Everton": "埃弗顿",
    "West Ham United FC": "西汉姆联",
    "West Ham": "西汉姆联",
    "Crystal Palace FC": "水晶宫",
    "Crystal Palace": "水晶宫",
    "Wolverhampton Wanderers FC": "狼队",
    "Wolverhampton": "狼队",
    "Wolves": "狼队",
    "Nottingham Forest FC": "诺丁汉森林",
    "Nottingham": "诺丁汉森林",
    "Leicester City FC": "莱斯特城",
    "Leicester": "莱斯特城",
    "Ipswich Town FC": "伊普斯威奇",
    "Ipswich Town": "伊普斯威奇",
    "Southampton FC": "南安普顿",
    "Southampton": "南安普顿",
    "Leeds United FC": "利兹联",
    "Leeds United": "利兹联",
    "Burnley FC": "伯恩利",
    "Burnley": "伯恩利",
    "Sheffield United FC": "谢菲尔德联",
    "Sunderland AFC": "桑德兰",
    "Sunderland": "桑德兰",
    "Hull City AFC": "赫尔城",
    "Hull City": "赫尔城",
    "Coventry City FC": "考文垂",
    "Coventry City": "考文垂",
    "Luton Town FC": "卢顿",
    "Middlesbrough FC": "米德尔斯堡",
    "Norwich City FC": "诺维奇",
    "West Bromwich Albion FC": "西布朗",
    "Watford FC": "沃特福德",
    "Blackburn Rovers FC": "布莱克本",
    "Stoke City FC": "斯托克城",
    "Bristol City FC": "布里斯托尔城",
    "Queens Park Rangers FC": "女王公园巡游者",
    "QPR": "女王公园巡游者",
    "Real Madrid CF": "皇家马德里",
    "Real Madrid": "皇家马德里",
    "FC Barcelona": "巴塞罗那",
    "Barcelona": "巴塞罗那",
    "Club Atlético de Madrid": "马德里竞技",
    "Atlético Madrid": "马德里竞技",
    "Atletico Madrid": "马德里竞技",
    "Atleti": "马德里竞技",
    "Athletic Club": "毕尔巴鄂竞技",
    "Athletic Bilbao": "毕尔巴鄂竞技",
    "Real Sociedad de Fútbol": "皇家社会",
    "Real Sociedad": "皇家社会",
    "Real Betis Balompié": "皇家贝蒂斯",
    "Real Betis": "皇家贝蒂斯",
    "Villarreal CF": "比利亚雷亚尔",
    "Villarreal": "比利亚雷亚尔",
    "Valencia CF": "巴伦西亚",
    "Valencia": "巴伦西亚",
    "Sevilla FC": "塞维利亚",
    "Sevilla": "塞维利亚",
    "Girona FC": "赫罗纳",
    "Girona": "赫罗纳",
    "CA Osasuna": "奥萨苏纳",
    "Osasuna": "奥萨苏纳",
    "RC Celta de Vigo": "塞尔塔",
    "Celta Vigo": "塞尔塔",
    "RCD Mallorca": "马略卡",
    "Mallorca": "马略卡",
    "Getafe CF": "赫塔费",
    "Getafe": "赫塔费",
    "Rayo Vallecano de Madrid": "巴列卡诺",
    "Rayo Vallecano": "巴列卡诺",
    "Deportivo Alavés": "阿拉维斯",
    "RCD Espanyol de Barcelona": "西班牙人",
    "Espanyol": "西班牙人",
    "UD Las Palmas": "拉斯帕尔马斯",
    "CD Leganés": "莱加内斯",
    "Real Valladolid CF": "巴拉多利德",
    "FC Bayern München": "拜仁慕尼黑",
    "Bayern Munich": "拜仁慕尼黑",
    "Bayern": "拜仁慕尼黑",
    "Borussia Dortmund": "多特蒙德",
    "Dortmund": "多特蒙德",
    "Bayer 04 Leverkusen": "勒沃库森",
    "Leverkusen": "勒沃库森",
    "RB Leipzig": "RB莱比锡",
    "VfB Stuttgart": "斯图加特",
    "Stuttgart": "斯图加特",
    "Eintracht Frankfurt": "法兰克福",
    "Frankfurt": "法兰克福",
    "SC Freiburg": "弗赖堡",
    "Freiburg": "弗赖堡",
    "VfL Wolfsburg": "沃尔夫斯堡",
    "Wolfsburg": "沃尔夫斯堡",
    "TSG 1899 Hoffenheim": "霍芬海姆",
    "Hoffenheim": "霍芬海姆",
    "SV Werder Bremen": "云达不莱梅",
    "Werder Bremen": "云达不莱梅",
    "FC Augsburg": "奥格斯堡",
    "Augsburg": "奥格斯堡",
    "1. FSV Mainz 05": "美因茨",
    "Mainz 05": "美因茨",
    "Borussia Mönchengladbach": "门兴格拉德巴赫",
    "M'gladbach": "门兴格拉德巴赫",
    "1. FC Union Berlin": "柏林联合",
    "Union Berlin": "柏林联合",
    "VfL Bochum 1848": "波鸿",
    "Bochum": "波鸿",
    "1. FC Heidenheim 1846": "海登海姆",
    "Heidenheim": "海登海姆",
    "FC St. Pauli 1910": "圣保利",
    "St. Pauli": "圣保利",
    "Holstein Kiel": "基尔",
    "Hamburger SV": "汉堡",
    "1. FC Köln": "科隆",
    "FC Internazionale Milano": "国际米兰",
    "Inter Milan": "国际米兰",
    "Inter": "国际米兰",
    "AC Milan": "AC米兰",
    "Milan": "AC米兰",
    "Juventus FC": "尤文图斯",
    "Juventus": "尤文图斯",
    "SSC Napoli": "那不勒斯",
    "Napoli": "那不勒斯",
    "AS Roma": "罗马",
    "Roma": "罗马",
    "SS Lazio": "拉齐奥",
    "Lazio": "拉齐奥",
    "Atalanta BC": "亚特兰大",
    "Atalanta": "亚特兰大",
    "ACF Fiorentina": "佛罗伦萨",
    "Fiorentina": "佛罗伦萨",
    "Bologna FC 1909": "博洛尼亚",
    "Bologna": "博洛尼亚",
    "Torino FC": "都灵",
    "Udinese Calcio": "乌迪内斯",
    "Genoa CFC": "热那亚",
    "AC Monza": "蒙扎",
    "US Lecce": "莱切",
    "Cagliari Calcio": "卡利亚里",
    "Hellas Verona FC": "维罗纳",
    "Parma Calcio 1913": "帕尔马",
    "Empoli FC": "恩波利",
    "Como 1907": "科莫",
    "Venezia FC": "威尼斯",
    "US Sassuolo Calcio": "萨索洛",
    "Paris Saint-Germain FC": "巴黎圣日耳曼",
    "Paris SG": "巴黎圣日耳曼",
    "PSG": "巴黎圣日耳曼",
    "Olympique de Marseille": "马赛",
    "Marseille": "马赛",
    "AS Monaco FC": "摩纳哥",
    "Monaco": "摩纳哥",
    "Olympique Lyonnais": "里昂",
    "Lyon": "里昂",
    "Lille OSC": "里尔",
    "LOSC": "里尔",
    "OGC Nice": "尼斯",
    "Racing Club de Lens": "朗斯",
    "Stade Rennais FC 1901": "雷恩",
    "Stade de Reims": "兰斯",
    "RC Strasbourg Alsace": "斯特拉斯堡",
    "Toulouse FC": "图卢兹",
    "Stade Brestois 29": "布雷斯特",
    "FC Nantes": "南特",
    "Montpellier HSC": "蒙彼利埃",
    "AJ Auxerre": "欧塞尔",
    "Angers SCO": "昂热",
    "Le Havre AC": "勒阿弗尔",
    "AS Saint-Étienne": "圣埃蒂安",
    "Club Brugge KV": "布鲁日",
    "Club Brugge": "布鲁日",
    "PSV": "埃因霍温",
    "PSV Eindhoven": "埃因霍温",
    "Feyenoord Rotterdam": "费耶诺德",
    "Feyenoord": "费耶诺德",
    "AFC Ajax": "阿贾克斯",
    "Ajax": "阿贾克斯",
    "Sporting Clube de Portugal": "葡萄牙体育",
    "Sporting CP": "葡萄牙体育",
    "Sport Lisboa e Benfica": "本菲卡",
    "Benfica": "本菲卡",
    "FC Porto": "波尔图",
    "Porto": "波尔图",
    "Celtic FC": "凯尔特人",
    "Rangers FC": "流浪者",
    "FK Shakhtar Donetsk": "顿涅茨克矿工",
    "FC Red Bull Salzburg": "萨尔茨堡红牛",
    "Galatasaray SK": "加拉塔萨雷",
    "Fenerbahçe SK": "费内巴切",
    "Olympiacos FC": "奥林匹亚科斯",
    "Inter Miami CF": "迈阿密国际",
    "Al-Nassr FC": "利雅得胜利",
    # National teams
    "France": "法国国家队",
    "Argentina": "阿根廷国家队",
    "Spain": "西班牙国家队",
    "England": "英格兰国家队",
    "Germany": "德国国家队",
    "Brazil": "巴西国家队",
    "Portugal": "葡萄牙国家队",
    "Netherlands": "荷兰国家队",
    "Italy": "意大利国家队",
    "Belgium": "比利时国家队",
    "Croatia": "克罗地亚国家队",
    "Uruguay": "乌拉圭国家队",
    "Colombia": "哥伦比亚国家队",
    "Morocco": "摩洛哥国家队",
    "Senegal": "塞内加尔国家队",
    "Switzerland": "瑞士国家队",
    "Austria": "奥地利国家队",
    "Denmark": "丹麦国家队",
    "Norway": "挪威国家队",
    "Sweden": "瑞典国家队",
    "Poland": "波兰国家队",
    "Turkey": "土耳其国家队",
    "Türkiye": "土耳其国家队",
    "Ukraine": "乌克兰国家队",
    "Serbia": "塞尔维亚国家队",
    "Scotland": "苏格兰国家队",
    "Wales": "威尔士国家队",
    "United States": "美国国家队",
    "USA": "美国国家队",
    "Mexico": "墨西哥国家队",
    "Canada": "加拿大国家队",
    "China PR": "中国国家队",
    "China": "中国国家队",
    "Japan": "日本国家队",
    "South Korea": "韩国国家队",
    "Korea Republic": "韩国国家队",
    "Australia": "澳大利亚国家队",
    "Iran": "伊朗国家队",
    "Saudi Arabia": "沙特阿拉伯国家队",
    "Qatar": "卡塔尔国家队",
    "Egypt": "埃及国家队",
    "Nigeria": "尼日利亚国家队",
}


def _normalize_team_key(raw: str) -> str:
    cleaned = raw.lower().replace("&", " ").replace(".", " ").replace("-", " ")
    cleaned = re.sub(
        r"\b(fc|cf|afc|kv|bc|sc|sv|sk|fk|ssc|ac|as|us|rc|rcd|cd|ud|ca|ogc|vfl|vfb|tsg|fsv|bsc|gnk|sl)\b",
        " ",
        cleaned,
    )
    return re.sub(r"\s+", " ", cleaned).strip()


_NORMALIZED_TEAM_ZH_MAP: dict[str, str] = {
    _normalize_team_key(k): v for k, v in TEAM_ZH_MAP.items()
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
        "portraitUrl": "https://thumb.wikimedia.org/wikipedia/commons/thumb/1/10/Vin%C3%ADcius_J%C3%BAnior_Brazil_V_Morocco_13_June_2026-207_%28cropped%29.jpg/330px-Vin%C3%ADcius_J%C3%BAnior_Brazil_V_Morocco_13_June_2026-207_%28cropped%29.jpg",
    },
    {
        "id": "p-salah", "name": "Mohamed Salah", "zhName": "萨拉赫",
        "clubName": "Liverpool FC", "clubZhName": "利物浦",
        "nationalName": "Egypt", "nationalZhName": "埃及国家队",
        "positionZh": "右边锋", "nationalityZh": "埃及", "shirtNumber": 11, "birthYear": 1992,
        "portraitUrl": "https://thumb.wikimedia.org/wikipedia/commons/thumb/a/a6/Mohamed_Salah_Argentina_v_Egypt_7_July_2026-163_%28cropped%29.jpg/330px-Mohamed_Salah_Argentina_v_Egypt_7_July_2026-163_%28cropped%29.jpg",
    },
    {
        "id": "p-saka", "name": "Bukayo Saka", "zhName": "萨卡",
        "clubName": "Arsenal FC", "clubZhName": "阿森纳",
        "nationalName": "England", "nationalZhName": "英格兰国家队",
        "positionZh": "右边锋", "nationalityZh": "英格兰", "shirtNumber": 7, "birthYear": 2001,
        "portraitUrl": "https://thumb.wikimedia.org/wikipedia/commons/thumb/2/2f/Bukayo_Saka_England_v_Ghana_23_June_2026-057_%28cropped%29.jpg/330px-Bukayo_Saka_England_v_Ghana_23_June_2026-057_%28cropped%29.jpg",
    },
    {
        "id": "p-kane", "name": "Harry Kane", "zhName": "哈里·凯恩",
        "clubName": "FC Bayern München", "clubZhName": "拜仁慕尼黑",
        "nationalName": "England", "nationalZhName": "英格兰国家队",
        "positionZh": "中锋", "nationalityZh": "英格兰", "shirtNumber": 9, "birthYear": 1993,
        "portraitUrl": "https://thumb.wikimedia.org/wikipedia/commons/thumb/2/2f/Harry_Kane_England_v_Ghana_23_June_2026-024_%28cropped%29.jpg/330px-Harry_Kane_England_v_Ghana_23_June_2026-024_%28cropped%29.jpg",
    },
    {
        "id": "p-messi", "name": "Lionel Messi", "zhName": "梅西",
        "clubName": "Inter Miami CF", "clubZhName": "迈阿密国际",
        "nationalName": "Argentina", "nationalZhName": "阿根廷国家队",
        "positionZh": "前锋 / 队长", "nationalityZh": "阿根廷", "shirtNumber": 10, "birthYear": 1987,
        "portraitUrl": "https://thumb.wikimedia.org/wikipedia/commons/thumb/c/c8/Leo_Messi_Argentina_v_Egypt_7_July_2026-1.jpg/330px-Leo_Messi_Argentina_v_Egypt_7_July_2026-1.jpg",
    },
    {
        "id": "p-ronaldo", "name": "Cristiano Ronaldo", "zhName": "C罗",
        "clubName": "Al-Nassr FC", "clubZhName": "利雅得胜利",
        "nationalName": "Portugal", "nationalZhName": "葡萄牙国家队",
        "positionZh": "前锋 / 队长", "nationalityZh": "葡萄牙", "shirtNumber": 7, "birthYear": 1985,
        "portraitUrl": "https://thumb.wikimedia.org/wikipedia/commons/thumb/2/26/Cristiano_Ronaldo_Croatia_v_Portugal_2_July_2026-075_%28cropped%29.jpg/330px-Cristiano_Ronaldo_Croatia_v_Portugal_2_July_2026-075_%28cropped%29.jpg",
    },
    {
        "id": "p-lautaro", "name": "Lautaro Martínez", "zhName": "劳塔罗",
        "clubName": "FC Internazionale Milano", "clubZhName": "国际米兰",
        "nationalName": "Argentina", "nationalZhName": "阿根廷国家队",
        "positionZh": "中锋", "nationalityZh": "阿根廷", "shirtNumber": 10, "birthYear": 1997,
        "portraitUrl": "https://thumb.wikimedia.org/wikipedia/commons/thumb/e/e9/Lautaro_Martinez_Argentina_v_Spain_19_July_2026-049_%28cropped%29.jpg/330px-Lautaro_Martinez_Argentina_v_Spain_19_July_2026-049_%28cropped%29.jpg",
    },
    {
        "id": "p-musiala", "name": "Jamal Musiala", "zhName": "穆西亚拉",
        "clubName": "FC Bayern München", "clubZhName": "拜仁慕尼黑",
        "nationalName": "Germany", "nationalZhName": "德国国家队",
        "positionZh": "前腰", "nationalityZh": "德国", "shirtNumber": 42, "birthYear": 2003,
        "portraitUrl": "https://thumb.wikimedia.org/wikipedia/commons/thumb/4/44/Jamal_Musiala_Ecuador_v_Germany_25_June_2026-174_%28cropped%29.jpg/330px-Jamal_Musiala_Ecuador_v_Germany_25_June_2026-174_%28cropped%29.jpg",
    },
    {
        "id": "p-rodri", "name": "Rodri", "zhName": "罗德里",
        "clubName": "Manchester City FC", "clubZhName": "曼城",
        "nationalName": "Spain", "nationalZhName": "西班牙国家队",
        "positionZh": "后腰", "nationalityZh": "西班牙", "shirtNumber": 16, "birthYear": 1996,
        "portraitUrl": "https://thumb.wikimedia.org/wikipedia/commons/thumb/7/7a/Rodri_Argentina_v_Spain_19_July_2026-187_%28cropped%29.jpg/330px-Rodri_Argentina_v_Spain_19_July_2026-187_%28cropped%29.jpg",
    },
    {
        "id": "p-vandijk", "name": "Virgil van Dijk", "zhName": "范戴克",
        "clubName": "Liverpool FC", "clubZhName": "利物浦",
        "nationalName": "Netherlands", "nationalZhName": "荷兰国家队",
        "positionZh": "中后卫", "nationalityZh": "荷兰", "shirtNumber": 4, "birthYear": 1991,
        "portraitUrl": "https://thumb.wikimedia.org/wikipedia/commons/thumb/5/5d/20160604_AUT_NED_8876_%28cropped%29.jpg/330px-20160604_AUT_NED_8876_%28cropped%29.jpg",
    },
    {
        "id": "p-wirtz", "name": "Florian Wirtz", "zhName": "维尔茨",
        "clubName": "Bayer 04 Leverkusen", "clubZhName": "勒沃库森",
        "nationalName": "Germany", "nationalZhName": "德国国家队",
        "positionZh": "前腰", "nationalityZh": "德国", "shirtNumber": 10, "birthYear": 2003,
        "portraitUrl": "https://thumb.wikimedia.org/wikipedia/commons/thumb/7/70/Florian_Wirtz_Ecuador_v_Germany_25_June_2026-181_%28cropped%29.jpg/330px-Florian_Wirtz_Ecuador_v_Germany_25_June_2026-181_%28cropped%29.jpg",
    },
    {
        "id": "p-palmer", "name": "Cole Palmer", "zhName": "帕尔默",
        "clubName": "Chelsea FC", "clubZhName": "切尔西",
        "nationalName": "England", "nationalZhName": "英格兰国家队",
        "positionZh": "前腰 / 边锋", "nationalityZh": "英格兰", "shirtNumber": 20, "birthYear": 2002,
        "portraitUrl": "https://upload.wikimedia.org/wikipedia/commons/f/fb/Cole_Palmer_2025_FIFA_Club_World_Cup_Final.jpg",
    },
]

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

    def to_dict(self) -> dict[str, Any]:
        return dataclasses.asdict(self)


def localize_team(name: str, short_name: str | None = None) -> str:
    if name in TEAM_ZH_MAP:
        return TEAM_ZH_MAP[name]
    if short_name and short_name in TEAM_ZH_MAP:
        return TEAM_ZH_MAP[short_name]
    norm_full = _normalize_team_key(name)
    if norm_full in _NORMALIZED_TEAM_ZH_MAP:
        return _NORMALIZED_TEAM_ZH_MAP[norm_full]
    if short_name:
        norm_short = _normalize_team_key(short_name)
        if norm_short in _NORMALIZED_TEAM_ZH_MAP:
            return _NORMALIZED_TEAM_ZH_MAP[norm_short]
    return short_name or name


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


def fetch_json(url: str, api_key: str, timeout_seconds: int = 20) -> dict[str, Any]:
    req = urllib.request.Request(
        url,
        headers={
            "X-Auth-Token": api_key,
            "User-Agent": "FootballCalendarSync/2.0",
        },
    )
    with urllib.request.urlopen(req, timeout=timeout_seconds) as resp:
        return json.loads(resp.read().decode("utf-8"))


def fetch_competition_matches(
    api_key: str,
    competition_code: str,
    date_from: str,
    date_to: str,
) -> list[NormalizedMatch]:
    params = urllib.parse.urlencode({"dateFrom": date_from, "dateTo": date_to})
    url = f"https://api.football-data.org/v4/competitions/{competition_code}/matches?{params}"
    payload = fetch_json(url, api_key)
    return [normalize_api_match(m, competition_code) for m in (payload.get("matches") or [])]


def fetch_competition_standings(api_key: str, competition_code: str) -> list[dict[str, Any]]:
    url = f"https://api.football-data.org/v4/competitions/{competition_code}/standings"
    payload = fetch_json(url, api_key)
    standings_groups = payload.get("standings") or []
    total_table = next(
        (g.get("table") or [] for g in standings_groups if g.get("type") == "TOTAL"),
        (standings_groups[0].get("table") or []) if standings_groups else [],
    )
    rows: list[dict[str, Any]] = []
    for entry in total_table[:20]:
        team = entry.get("team") or {}
        team_name = team.get("name") or "Unknown"
        team_short = team.get("shortName") or team.get("tla") or team_name
        rows.append(
            {
                "competitionCode": competition_code,
                "rank": int(entry.get("position") or 0),
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
            }
        )
    return rows


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
        ("demo-1004", "BL1", 0, 17, 30, "FC Bayern München", "Borussia Dortmund", "TIMED", None, None, "Allianz Arena"),
        ("demo-1005", "SA", 0, 19, 45, "FC Internazionale Milano", "AC Milan", "TIMED", None, None, "San Siro"),
        ("demo-1006", "CL", 1, 20, 0, "Paris Saint-Germain FC", "Real Madrid CF", "TIMED", None, None, "Parc des Princes"),
        ("demo-1007", "CL", 1, 20, 0, "Arsenal FC", "FC Bayern München", "TIMED", None, None, "Emirates Stadium"),
        ("demo-1008", "INT", 2, 19, 45, "France", "Spain", "TIMED", None, None, "Stade de France"),
        ("demo-1009", "WC", 3, 0, 0, "Argentina", "Brazil", "TIMED", None, None, "Estadio Monumental"),
        ("demo-1010", "INT", 3, 11, 35, "China PR", "Japan", "TIMED", None, None, "工人体育场"),
        ("demo-1011", "EC", 4, 19, 45, "England", "Germany", "TIMED", None, None, "Wembley Stadium"),
        ("demo-1012", "PL", 5, 15, 0, "Manchester United FC", "Tottenham Hotspur FC", "TIMED", None, None, "Old Trafford"),
        ("demo-1013", "PD", 5, 19, 0, "Club Atlético de Madrid", "FC Barcelona", "TIMED", None, None, "Cívitas Metropolitano"),
        ("demo-1014", "SA", 6, 19, 45, "Juventus FC", "SSC Napoli", "TIMED", None, None, "Allianz Stadium"),
        ("demo-1015", "FL1", 7, 19, 0, "Olympique de Marseille", "Paris Saint-Germain FC", "TIMED", None, None, "Stade Vélodrome"),
        ("demo-1016", "BL1", 8, 17, 30, "Bayer 04 Leverkusen", "RB Leipzig", "POSTPONED", None, None, "BayArena"),
        ("demo-1017", "INT", 9, 19, 45, "Portugal", "Netherlands", "TIMED", None, None, "Estádio da Luz"),
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
                matchday=None if comp_code in {"INT", "WC", "EC"} else 12 + (idx % 3),
                stage="INTERNATIONAL" if comp_code in {"INT", "WC", "EC"} else "REGULAR_SEASON",
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
            ("Tottenham Hotspur FC", 12, 5, 3, 4, 3, 18, "L,W,D,W,L"),
            ("Manchester United FC", 12, 4, 4, 4, 1, 16, "D,L,W,D,W"),
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
            ("AC Milan", 12, 6, 3, 3, 6, 21, "L,W,D,W,D"),
            ("AS Roma", 12, 4, 4, 4, 2, 16, "D,L,W,D,W"),
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
            ("FC Bayern München", 5, 3, 0, 2, 5, 9, "W,L,L,W,W"),
            ("Real Madrid CF", 5, 3, 0, 2, 3, 9, "W,L,W,L,W"),
        ],
    }

    rows: list[dict[str, Any]] = []
    for code, items in tables.items():
        for rank, (name, p, w, d, l, gd, pts, form) in enumerate(items, start=1):
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
                }
            )
    return rows


def enrich_players() -> list[dict[str, Any]]:
    enriched = []
    for p in STAR_PLAYERS_CATALOG:
        item = dict(p)
        item["clubCrestUrl"] = resolve_team_logo(p["clubName"])
        item["nationalCrestUrl"] = resolve_team_logo(p["nationalName"])
        enriched.append(item)
    return enriched


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
        "PRODID:-//Football Calendar//Auto Sync Feed//ZH",
        "CALSCALE:GREGORIAN",
        "METHOD:PUBLISH",
        f"X-WR-CALNAME:{ics_escape(calendar_name)}",
        "X-WR-TIMEZONE:Asia/Shanghai",
        "REFRESH-INTERVAL;VALUE=DURATION:PT6H",
        "X-PUBLISHED-TTL:PT6H",
    ]
    for m in matches:
        start_dt = parse_utc_iso(m.utcDate)
        end_dt = start_dt + dt.timedelta(minutes=110)
        score_text = (
            f" ({m.homeScore}-{m.awayScore})"
            if m.homeScore is not None and m.awayScore is not None
            else ""
        )
        status_zh = STATUS_ZH_MAP.get(m.status, m.status)
        summary = f"[{m.competitionZhName}] {m.homeTeamZhName} vs {m.awayTeamZhName}{score_text}"
        desc = (
            f"赛事: {m.competitionZhName} ({m.competitionName})\\n"
            f"状态: {status_zh}\\n"
            f"对阵: {m.homeTeamName} vs {m.awayTeamName}"
        )
        ics_status = "CANCELLED" if m.status in {"CANCELLED", "POSTPONED"} else "CONFIRMED"
        event_lines = [
            "BEGIN:VEVENT",
            f"UID:{m.id}@football-calendar",
            f"DTSTAMP:{stamp}",
            f"DTSTART:{start_dt.strftime('%Y%m%dT%H%M%SZ')}",
            f"DTEND:{end_dt.strftime('%Y%m%dT%H%M%SZ')}",
            f"SUMMARY:{ics_escape(summary)}",
            f"DESCRIPTION:{desc}",
            f"STATUS:{ics_status}",
            f"CATEGORIES:{ics_escape(m.competitionZhName)}",
        ]
        if m.venue:
            event_lines.append(f"LOCATION:{ics_escape(m.venue)}")
        event_lines.append("END:VEVENT")
        lines.extend(fold_ics_line(line) for line in event_lines)
    lines.append("END:VCALENDAR")
    return "\r\n".join(lines) + "\r\n"


def _logo_img_html(url: str, label: str, size: int = 30) -> str:
    initial = html.escape((label[:1] or "?"))
    if url:
        return (
            f'<img class="crest" style="width:{size}px;height:{size}px;" '
            f'src="{html.escape(url)}" alt="{html.escape(label)}" loading="lazy" '
            f'onerror="this.style.display=\'none\';this.nextElementSibling.style.display=\'inline-flex\'" />'
            f'<span class="crest-fallback" style="display:none;width:{size}px;height:{size}px;">{initial}</span>'
        )
    return f'<span class="crest-fallback" style="width:{size}px;height:{size}px;">{initial}</span>'


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
        else "演示赛程（配置 FOOTBALL_DATA_API_KEY 后切换实时赛程）"
    )

    cards_html: list[str] = []
    for m in matches[:60]:
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
        is_nat = "true" if m.competitionCode in {"WC", "EC", "INT"} else "false"
        cards_html.append(
            f"""
            <article class="card match-card" data-comp="{html.escape(m.competitionCode)}" data-nat="{is_nat}">
              <div class="card-top">
                <span class="league">{html.escape(m.competitionZhName)}</span>
                <span class="time tnum">{html.escape(date_str)} {early_tag}</span>
              </div>
              <div class="teams">
                <div class="team home">
                  {_logo_img_html(m.homeTeamCrest, m.homeTeamZhName, 30)}
                  <span>{html.escape(m.homeTeamZhName)}</span>
                </div>
                <span class="score tnum">{html.escape(score_display)}</span>
                <div class="team away">
                  <span>{html.escape(m.awayTeamZhName)}</span>
                  {_logo_img_html(m.awayTeamCrest, m.awayTeamZhName, 30)}
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
    for idx, code in enumerate(STANDINGS_COMPETITIONS):
        rows = standings_by_comp.get(code, [])
        if not rows:
            continue
        tr_html = []
        for r in rows[:10]:
            tr_html.append(
                f"""
                <tr>
                  <td class="rank tnum">{r['rank']}</td>
                  <td class="tname">{_logo_img_html(r.get('teamCrest', ''), r['teamZhName'], 22)}<span>{html.escape(r['teamZhName'])}</span></td>
                  <td class="tnum">{r['played']}</td>
                  <td class="tnum">{r['goalDifference']:+d}</td>
                  <td class="pts tnum">{r['points']}</td>
                </tr>
                """
            )
        hidden = "" if idx == 0 else 'style="display:none;"'
        standings_tables_html.append(
            f'<table class="st-table" data-st="{html.escape(code)}" {hidden}>'
            f'<thead><tr><th>#</th><th>球队</th><th>赛</th><th>净胜</th><th>积分</th></tr></thead>'
            f'<tbody>{"".join(tr_html)}</tbody></table>'
        )

    players_html: list[str] = []
    for p in players:
        players_html.append(
            f"""
            <article class="card player-card">
              <div class="p-top">
                <img class="avatar" src="{html.escape(p['portraitUrl'])}" alt="{html.escape(p['zhName'])}" loading="lazy" />
                <div>
                  <div class="p-name">{html.escape(p['zhName'])} <span class="p-num">#{p['shirtNumber']}</span></div>
                  <div class="p-sub">{html.escape(p['nationalityZh'])} · {html.escape(p['positionZh'])}</div>
                </div>
              </div>
              <div class="p-teams">
                <span class="p-tag">{_logo_img_html(p.get('clubCrestUrl', ''), p['clubZhName'], 18)}{html.escape(p['clubZhName'])}</span>
                <span class="p-tag">{_logo_img_html(p.get('nationalCrestUrl', ''), p['nationalZhName'], 18)}{html.escape(p['nationalZhName'])}</span>
              </div>
            </article>
            """
        )

    return f"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>足球赛事日历 · 红白苹果风订阅中心</title>
  <style>
    :root {{
      --bg: #F2F2F7;
      --surface: #FFFFFF;
      --border: #E5E5EA;
      --text: #1C1C1E;
      --muted: #6E6E73;
      --red: #E11D48;
      --red-tint: #FFF1F2;
      --warn: #D97706;
    }}
    * {{ box-sizing: border-box; }}
    body {{
      margin: 0; font-family: -apple-system, BlinkMacSystemFont, "SF Pro Text", "Segoe UI", Roboto, sans-serif;
      background: var(--bg); color: var(--text); line-height: 1.45;
    }}
    .tnum {{ font-variant-numeric: tabular-nums; }}
    .topbar {{
      position: sticky; top: 0; z-index: 20;
      background: rgba(255,255,255,0.92); backdrop-filter: blur(12px);
      border-bottom: 1px solid var(--border); padding: 14px 20px;
    }}
    .topbar-inner, .wrap {{ max-width: 1120px; margin: 0 auto; }}
    .topbar-inner {{ display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 12px; }}
    .brand {{ display: flex; align-items: center; gap: 12px; }}
    .logo-box {{
      width: 40px; height: 40px; border-radius: 12px; background: var(--red); color: #fff;
      display: flex; align-items: center; justify-content: center; font-size: 20px; font-weight: 800;
    }}
    h1 {{ margin: 0; font-size: 1.2rem; font-weight: 800; letter-spacing: -0.02em; }}
    .sub {{ font-size: 0.82rem; color: var(--muted); }}
    .actions {{ display: flex; flex-wrap: wrap; gap: 8px; }}
    .btn {{
      text-decoration: none; border: 1px solid var(--border); background: var(--surface); color: var(--text);
      padding: 8px 14px; border-radius: 12px; font-size: 0.84rem; font-weight: 600; cursor: pointer;
    }}
    .btn.primary {{ background: var(--red); color: #fff; border-color: var(--red); }}
    .wrap {{ padding: 20px 16px 56px; display: grid; grid-template-columns: 1fr; gap: 22px; }}
    @media (min-width: 960px) {{ .wrap {{ grid-template-columns: 2fr 1fr; align-items: start; }} }}
    .section-head {{ display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 10px; margin-bottom: 12px; }}
    .section-title {{ font-size: 1.15rem; font-weight: 800; margin: 0; }}
    .filters {{ display: flex; flex-wrap: wrap; gap: 6px; }}
    .pill {{
      background: var(--surface); color: var(--text); border: 1px solid var(--border);
      padding: 6px 12px; border-radius: 999px; font-size: 0.8rem; font-weight: 600; cursor: pointer;
    }}
    .pill.active {{ background: var(--red); color: #fff; border-color: var(--red); }}
    .grid {{ display: grid; grid-template-columns: repeat(auto-fill, minmax(290px, 1fr)); gap: 12px; }}
    .card {{
      background: var(--surface); border: 1px solid var(--border); border-radius: 18px;
      padding: 14px; box-shadow: 0 2px 8px rgba(0,0,0,0.03);
    }}
    .card-top, .card-foot {{ display: flex; justify-content: space-between; align-items: center; font-size: 0.78rem; color: var(--muted); }}
    .league {{ font-weight: 700; color: var(--red); }}
    .teams {{ display: flex; align-items: center; justify-content: space-between; gap: 8px; margin: 12px 0; }}
    .team {{ flex: 1; display: flex; align-items: center; gap: 8px; font-weight: 700; font-size: 0.94rem; min-width: 0; }}
    .team.away {{ justify-content: flex-end; text-align: right; }}
    .team span {{ overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }}
    .crest {{ object-fit: contain; border-radius: 999px; background: #fff; flex-shrink: 0; }}
    .crest-fallback {{
      display: inline-flex; align-items: center; justify-content: center; border-radius: 999px;
      background: var(--red-tint); color: var(--red); font-weight: 800; font-size: 0.75rem; flex-shrink: 0;
    }}
    .score {{
      padding: 4px 10px; border-radius: 10px; background: var(--bg); font-weight: 800; font-size: 0.88rem;
    }}
    .badge.early {{ background: #FEF3C7; color: var(--warn); padding: 1px 6px; border-radius: 6px; font-size: 0.72rem; margin-left: 4px; }}
    .status.postponed {{ color: var(--red); font-weight: 700; }}
    .st-table {{ width: 100%; border-collapse: collapse; font-size: 0.82rem; }}
    .st-table th {{ text-align: left; color: var(--muted); font-weight: 700; padding: 8px 6px; border-bottom: 1px solid var(--border); }}
    .st-table td {{ padding: 8px 6px; border-bottom: 1px solid #F2F2F7; }}
    .st-table .tname {{ display: flex; align-items: center; gap: 6px; font-weight: 600; }}
    .st-table .pts {{ font-weight: 800; color: var(--red); }}
    .players-grid {{ display: grid; grid-template-columns: repeat(auto-fill, minmax(230px, 1fr)); gap: 12px; }}
    .p-top {{ display: flex; align-items: center; gap: 12px; }}
    .avatar {{ width: 54px; height: 54px; border-radius: 999px; object-fit: cover; object-position: top; background: var(--bg); border: 2px solid var(--red-tint); }}
    .p-name {{ font-weight: 800; font-size: 0.96rem; }}
    .p-num {{ color: var(--red); font-size: 0.84rem; }}
    .p-sub {{ color: var(--muted); font-size: 0.78rem; margin-top: 2px; }}
    .p-teams {{ display: flex; flex-wrap: wrap; gap: 6px; margin-top: 10px; padding-top: 10px; border-top: 1px solid #F2F2F7; }}
    .p-tag {{ display: inline-flex; align-items: center; gap: 5px; font-size: 0.76rem; background: var(--bg); padding: 4px 8px; border-radius: 999px; font-weight: 600; }}
  </style>
</head>
<body>
  <header class="topbar">
    <div class="topbar-inner">
      <div class="brand">
        <div class="logo-box">⚽</div>
        <div>
          <h1>足球赛事日历 · 自动更新中心</h1>
          <div class="sub">最近同步：{html.escape(gen_bj)} · {html.escape(mode_badge)}</div>
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
        <h2 class="section-title">近期与未来赛程（含俱乐部 &amp; 国家队）</h2>
        <div class="filters" id="filters">
          <button class="pill active" data-filter="ALL">全部</button>
          <button class="pill" data-filter="NAT">国家队</button>
          {"".join(f'<button class="pill" data-filter="{html.escape(c)}">{html.escape(COMPETITION_META.get(c, {}).get("zhName", c))}</button>' for c in competitions if c not in {"WC", "EC", "INT"})}
        </div>
      </div>
      <div class="grid" id="cards">{"".join(cards_html)}</div>

      <div style="margin-top:28px;">
        <div class="section-head"><h2 class="section-title">热门球星库（真人头像 &amp; 俱乐部/国家队关联）</h2></div>
        <div class="players-grid">{"".join(players_html)}</div>
      </div>
    </section>

    <aside>
      <div class="section-head">
        <h2 class="section-title">联赛积分榜</h2>
        <div class="filters" id="st-filters">
          {"".join(f'<button class="pill {"active" if i == 0 else ""}" data-st="{html.escape(c)}">{html.escape(COMPETITION_META.get(c, {}).get("zhName", c))}</button>' for i, c in enumerate(STANDINGS_COMPETITIONS))}
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
) -> dict[str, pathlib.Path]:
    now = (generated_at or dt.datetime.now(dt.timezone.utc)).astimezone(dt.timezone.utc)
    sorted_matches = sorted(matches, key=lambda m: (m.utcDate, m.competitionCode, m.id))

    output_dir.mkdir(parents=True, exist_ok=True)
    ics_dir = output_dir / "ics"
    ics_dir.mkdir(parents=True, exist_ok=True)

    payload = {
        "schemaVersion": 2,
        "generatedAtUtc": format_utc_iso(now),
        "mode": mode,
        "competitions": [
            {
                "code": c,
                "name": COMPETITION_META.get(c, {}).get("name", c),
                "zhName": COMPETITION_META.get(c, {}).get("zhName", c),
                "colorHex": COMPETITION_META.get(c, {}).get("color", "#E11D48"),
            }
            for c in competitions
        ],
        "matches": [m.to_dict() for m in sorted_matches],
        "standings": standings,
        "players": players,
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


def collect_data(
    api_key: str | None,
    competitions: list[str],
    days_back: int = 3,
    days_ahead: int = 21,
    rate_limit_sleep: float = 6.2,
) -> tuple[list[NormalizedMatch], list[dict[str, Any]], str]:
    now = dt.datetime.now(dt.timezone.utc)
    if not api_key:
        return generate_demo_matches(now), generate_demo_standings(), "demo_fallback"

    date_from = (now - dt.timedelta(days=days_back)).strftime("%Y-%m-%d")
    date_to = (now + dt.timedelta(days=days_ahead)).strftime("%Y-%m-%d")

    collected: dict[str, NormalizedMatch] = {}
    standings: list[dict[str, Any]] = []
    errors: list[str] = []

    api_comps = [c for c in competitions if c != "INT"]
    for idx, code in enumerate(api_comps):
        if idx > 0 and rate_limit_sleep > 0:
            time.sleep(rate_limit_sleep)
        try:
            for match in fetch_competition_matches(api_key, code, date_from, date_to):
                collected[match.id] = match
        except urllib.error.HTTPError as exc:
            errors.append(f"{code} matches: HTTP {exc.code}")
            if exc.code == 429:
                time.sleep(max(rate_limit_sleep, 10.0))
        except Exception as exc:  # noqa: BLE001
            errors.append(f"{code} matches: {exc}")

    for code in STANDINGS_COMPETITIONS:
        if rate_limit_sleep > 0:
            time.sleep(rate_limit_sleep)
        try:
            standings.extend(fetch_competition_standings(api_key, code))
        except Exception as exc:  # noqa: BLE001
            errors.append(f"{code} standings: {exc}")

    if not collected:
        print(f"[warn] No live matches fetched ({errors}); falling back to demo feed.", file=sys.stderr)
        return generate_demo_matches(now), generate_demo_standings(), "demo_fallback"

    if not standings:
        standings = generate_demo_standings()
    if errors:
        print(f"[warn] Partial API fetch warnings: {errors}", file=sys.stderr)
    return list(collected.values()), standings, "live_api"


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
    args = parser.parse_args(argv)

    competitions = [c.strip().upper() for c in args.competitions.split(",") if c.strip()]
    api_key = os.environ.get("FOOTBALL_DATA_API_KEY", "").strip() or None

    matches, standings, mode = collect_data(
        api_key=api_key,
        competitions=competitions,
        days_back=args.days_back,
        days_ahead=args.days_ahead,
        rate_limit_sleep=args.sleep,
    )
    players = enrich_players()
    outputs = write_feed_outputs(
        output_dir=pathlib.Path(args.output_dir),
        matches=matches,
        standings=standings,
        players=players,
        mode=mode,
        competitions=competitions,
    )
    print(f"Generated {len(matches)} matches, {len(standings)} standing rows, {len(players)} stars in mode={mode}: {outputs['fixtures_json']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
