#!/usr/bin/env python3
"""Comprehensive Chinese team, player, position & phonetic transliteration helpers."""

from __future__ import annotations

import re
import unicodedata

TEAM_ZH_MAP: dict[str, str] = {
    # England - Premier League & Championship
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
    "Sheffield Utd": "谢菲尔德联",
    "Sunderland AFC": "桑德兰",
    "Sunderland": "桑德兰",
    "Hull City AFC": "赫尔城",
    "Hull City": "赫尔城",
    "Coventry City FC": "考文垂",
    "Coventry City": "考文垂",
    "Luton Town FC": "卢顿",
    "Middlesbrough FC": "米德尔斯堡",
    "Middlesbrough": "米德尔斯堡",
    "Norwich City FC": "诺维奇",
    "West Bromwich Albion FC": "西布朗",
    "Watford FC": "沃特福德",
    "Blackburn Rovers FC": "布莱克本",
    "Stoke City FC": "斯托克城",
    "Bristol City FC": "布里斯托尔城",
    "Queens Park Rangers FC": "女王公园巡游者",
    "QPR": "女王公园巡游者",
    "Swansea City AFC": "斯旺西",
    "Cardiff City FC": "卡迪夫城",
    "Millwall FC": "米尔沃尔",
    "Preston North End FC": "普雷斯顿",
    "Sheffield Wednesday FC": "谢周三",
    "Derby County FC": "德比郡",
    "Portsmouth FC": "朴茨茅斯",
    "Oxford United FC": "牛津联",
    "Plymouth Argyle FC": "普利茅斯",
    "Birmingham City FC": "伯明翰",
    "Wrexham AFC": "雷克瑟姆",
    "Charlton Athletic FC": "查尔顿",

    # Spain - La Liga & Segunda
    "Real Madrid CF": "皇家马德里",
    "Real Madrid": "皇家马德里",
    "FC Barcelona": "巴塞罗那",
    "Barcelona": "巴塞罗那",
    "Club Atlético de Madrid": "马德里竞技",
    "Atlético Madrid": "马德里竞技",
    "Atletico Madrid": "马德里竞技",
    "Athletic Club": "毕尔巴鄂竞技",
    "Athletic Bilbao": "毕尔巴鄂竞技",
    "Real Sociedad de Fútbol": "皇家社会",
    "Real Sociedad": "皇家社会",
    "Real Betis Balompié": "皇家贝蒂斯",
    "Real Betis": "皇家贝蒂斯",
    "Villarreal CF": "比利亚雷亚尔",
    "Girona FC": "赫罗纳",
    "Sevilla FC": "塞维利亚",
    "Valencia CF": "巴伦西亚",
    "RC Celta de Vigo": "塞尔塔",
    "Celta Vigo": "塞尔塔",
    "CA Osasuna": "奥萨苏纳",
    "Rayo Vallecano de Madrid": "巴列卡诺",
    "Rayo Vallecano": "巴列卡诺",
    "RCD Mallorca": "马略卡",
    "Getafe CF": "赫塔费",
    "Deportivo Alavés": "阿拉维斯",
    "Alavés": "阿拉维斯",
    "RCD Espanyol de Barcelona": "西班牙人",
    "Espanyol": "西班牙人",
    "UD Las Palmas": "拉斯帕尔马斯",
    "Real Valladolid CF": "巴拉多利德",
    "CD Leganés": "莱加内斯",
    "Levante UD": "莱万特",
    "Elche CF": "埃尔切",
    "Real Oviedo": "奥维耶多",
    "Racing Santander": "桑坦德竞技",
    "Real Racing Club de Santander": "桑坦德竞技",
    "Santander": "桑坦德竞技",
    "RC Deportivo de La Coruña": "拉科鲁尼亚",
    "Deportivo": "拉科鲁尼亚",
    "Málaga CF": "马拉加",
    "Malaga": "马拉加",
    "Granada CF": "格拉纳达",
    "UD Almería": "阿尔梅里亚",
    "Cádiz CF": "加的斯",

    # Germany - Bundesliga & 2. Bundesliga
    "FC Bayern München": "拜仁慕尼黑",
    "Bayern Munich": "拜仁慕尼黑",
    "Borussia Dortmund": "多特蒙德",
    "Bayer 04 Leverkusen": "勒沃库森",
    "RB Leipzig": "RB莱比锡",
    "VfB Stuttgart": "斯图加特",
    "Eintracht Frankfurt": "法兰克福",
    "VfL Wolfsburg": "沃尔夫斯堡",
    "SC Freiburg": "弗赖堡",
    "TSG 1899 Hoffenheim": "霍芬海姆",
    "SV Werder Bremen": "云达不莱梅",
    "1. FC Union Berlin": "柏林联合",
    "FC Augsburg": "奥格斯堡",
    "Borussia Mönchengladbach": "门兴格拉德巴赫",
    "1. FSV Mainz 05": "美因茨",
    "1. FC Heidenheim 1846": "海登海姆",
    "VfL Bochum 1848": "波鸿",
    "FC St. Pauli 1910": "圣保利",
    "Holstein Kiel": "荷尔斯泰因基尔",
    "Hamburger SV": "汉堡",
    "1. FC Köln": "科隆",
    "FC Schalke 04": "沙尔克04",
    "SV 07 Elversberg": "埃尔弗斯贝格",
    "Elversberg": "埃尔弗斯贝格",
    "SC Paderborn 07": "帕德博恩",
    "Paderborn": "帕德博恩",
    "Hertha BSC": "柏林赫塔",
    "Hannover 96": "汉诺威96",
    "Fortuna Düsseldorf": "杜塞尔多夫",
    "1. FC Kaiserslautern": "凯泽斯劳滕",
    "Karlsruher SC": "卡尔斯鲁厄",
    "1. FC Nürnberg": "纽伦堡",

    # Italy - Serie A & B
    "FC Internazionale Milano": "国际米兰",
    "Inter": "国际米兰",
    "Inter Milan": "国际米兰",
    "AC Milan": "AC米兰",
    "Juventus FC": "尤文图斯",
    "SSC Napoli": "那不勒斯",
    "AS Roma": "罗马",
    "SS Lazio": "拉齐奥",
    "Atalanta BC": "亚特兰大",
    "ACF Fiorentina": "佛罗伦萨",
    "Bologna FC 1909": "博洛尼亚",
    "Torino FC": "都灵",
    "Udinese Calcio": "乌迪内斯",
    "Genoa CFC": "热那亚",
    "Cagliari Calcio": "卡利亚里",
    "Hellas Verona FC": "维罗纳",
    "US Lecce": "莱切",
    "Empoli FC": "恩波利",
    "Como 1907": "科莫",
    "Parma Calcio 1913": "帕尔马",
    "Venezia FC": "威尼斯",
    "AC Monza": "蒙扎",
    "US Sassuolo Calcio": "萨索洛",
    "Pisa SC": "比萨",
    "US Cremonese": "克雷莫内塞",

    # France - Ligue 1
    "Paris Saint-Germain FC": "巴黎圣日耳曼",
    "Paris Saint Germain": "巴黎圣日耳曼",
    "Olympique de Marseille": "马赛",
    "AS Monaco FC": "摩纳哥",
    "Olympique Lyonnais": "里昂",
    "LOSC Lille": "里尔",
    "OGC Nice": "尼斯",
    "RC Lens": "朗斯",
    "Stade Rennais FC 1901": "雷恩",
    "Stade Brestois 29": "布雷斯特",
    "RC Strasbourg Alsace": "斯特拉斯堡",
    "Toulouse FC": "图卢兹",
    "FC Nantes": "南特",
    "AJ Auxerre": "欧塞尔",
    "Angers SCO": "昂热",
    "Le Havre AC": "勒阿弗尔",
    "Paris FC": "巴黎FC",
    "FC Metz": "梅斯",
    "FC Lorient": "洛里昂",

    # European Cups / ELC / DED / PPL
    "Club Brugge KV": "布鲁日",
    "Club Brugge": "布鲁日",
    "Feyenoord": "费耶诺德",
    "Feyenoord Rotterdam": "费耶诺德",
    "PSV": "埃因霍温",
    "PSV Eindhoven": "埃因霍温",
    "AFC Ajax": "阿贾克斯",
    "Ajax": "阿贾克斯",
    "AZ": "阿尔克马尔",
    "AZ Alkmaar": "阿尔克马尔",
    "FC Twente '65": "特温特",
    "FC Utrecht": "乌得勒支",
    "Sporting Clube de Portugal": "葡萄牙体育",
    "Sporting CP": "葡萄牙体育",
    "Sport Lisboa e Benfica": "本菲卡",
    "SL Benfica": "本菲卡",
    "FC Porto": "波尔图",
    "Sporting Clube de Braga": "布拉加",
    "SC Braga": "布拉加",
    "Vitória SC": "吉马良斯",
    "Galatasaray": "加拉塔萨雷",
    "Fenerbahçe SK": "费内巴切",
    "Fenerbahce": "费内巴切",
    "Beşiktaş JK": "贝西克塔斯",
    "Trabzonspor": "特拉布宗体育",
    "İstiklal": "独立",
    "Olympiacos": "奥林匹亚科斯",
    "PAOK": "塞萨洛尼基",
    "Panathinaikos": "帕纳辛奈科斯",
    "AEK Athens FC": "雅典AEK",
    "Rangers FC": "流浪者",
    "Celtic FC": "凯尔特人",
    "Heart of Midlothian": "哈茨",
    "Royale Union Saint-Gilloise": "圣吉罗斯联合",
    "Union St. Gilloise": "圣吉罗斯联合",
    "RSC Anderlecht": "安德莱赫特",
    "KRC Genk": "亨克",
    "KAA Gent": "根特",
    "FK Bodø/Glimt": "博德闪耀",
    "Bodo/Glimt": "博德闪耀",
    "SK Brann": "布兰",
    "FC København": "哥本哈根",
    "FC Midtjylland": "中日德兰",
    "Malmö FF": "马尔默",
    "SK Slavia Praha": "布拉格斯拉维亚",
    "AC Sparta Praha": "布拉格斯巴达",
    "FC Viktoria Plzeň": "比尔森胜利",
    "GNK Dinamo Zagreb": "萨格勒布迪纳摩",
    "FK Crvena Zvezda": "贝尔格莱德红星",
    "FK Shakhtar Donetsk": "顿涅茨克矿工",
    "Dynamo Kyiv": "基辅迪纳摩",
    "Qarabağ Ağdam FK": "卡拉巴赫",
    "Qarabag": "卡拉巴赫",
    "Sabah FK": "萨巴赫",
    "Ferencvárosi TC": "费伦茨瓦罗斯",
    "Legia Warszawa": "华沙莱吉亚",
    "Jagiellonia Białystok": "雅盖隆",
    "Lech Poznań": "波兹南莱赫",
    "Raków Częstochowa": "琴斯托霍瓦",
    "BSC Young Boys": "年轻人",
    "FC Basel 1893": "巴塞尔",
    "Servette FC": "塞尔维特",
    "SK Sturm Graz": "格拉茨风暴",
    "FC Red Bull Salzburg": "萨尔茨堡红牛",
    "LASK": "林茨",
    "SK Rapid Wien": "维也纳快速",
    "NK Celje": "采列",
    "Omonoia Nicosia": "奥莫尼亚",
    "Pafos FC": "帕福斯",
    "Shamrock Rovers": "沙姆洛克流浪",
    "Ludogorets 1945 Razgrad": "卢多戈雷茨",
    "FCSB": "布加勒斯特星",
    "Maccabi Tel Aviv": "特拉维夫马卡比",

    # Brazil & CONMEBOL
    "Feyenoord": "费耶诺德",
    "Kashima": "鹿岛鹿角",
    "Port FC": "泰港",
    "Port MTI FC": "泰港",
    "Besiktas": "贝西克塔斯",
    "Hapoel Be'er": "贝尔谢巴夏普尔",
    "Vitoria": "维多利亚",
    "Internazionale": "国际米兰",
    "CR Flamengo": "弗拉门戈",
    "Flamengo": "弗拉门戈",
    "SE Palmeiras": "帕尔梅拉斯",
    "Palmeiras": "帕尔梅拉斯",
    "Botafogo FR": "博塔弗戈",
    "São Paulo FC": "圣保罗",
    "SC Internacional": "巴西国际",
    "Grêmio FBPA": "格雷米奥",
    "Clube Atlético Mineiro": "米内罗竞技",
    "Cruzeiro EC": "克鲁塞罗",
    "Fluminense FC": "弗鲁米嫩塞",
    "SC Corinthians Paulista": "科林蒂安",
    "Club Atlético River Plate": "河床",
    "River Plate": "河床",
    "CA Boca Juniors": "博卡青年",
    "Boca Juniors": "博卡青年",
    "Racing Club": "竞技俱乐部",
    "CA Peñarol": "佩纳罗尔",
    "Club Nacional de Football": "乌拉圭民族",

    # AFC Champions League Elite / Asian Clubs / CSL
    "Al-Hilal Saudi FC": "利雅得新月",
    "Al-Hilal": "利雅得新月",
    "Al Hilal": "利雅得新月",
    "Al-Nassr FC": "利雅得胜利",
    "Al-Nassr": "利雅得胜利",
    "Al Nassr": "利雅得胜利",
    "Al-Ahli Saudi FC": "吉达国民",
    "Al-Ahli": "吉达国民",
    "Al-Ittihad Club": "吉达联合",
    "Al-Ittihad": "吉达联合",
    "Al-Sadd SC": "萨德",
    "Al-Sadd": "萨德",
    "Al-Gharafa": "加拉法",
    "Al-Rayyan": "赖扬",
    "Al-Ain FC": "艾因",
    "Al Ain": "艾因",
    "Al-Wasl": "迪拜祈祷",
    "Shabab Al-Ahli Dubai": "迪拜国民青年",
    "Persepolis FC": "波斯波利斯",
    "Esteghlal FC": "德黑兰独立",
    "Tractor Sazi": "大不里士拖拉机",
    "Pakhtakor": "塔什干棉农",
    "Shanghai Port FC": "上海海港",
    "Shanghai Port": "上海海港",
    "Shanghai Shenhua FC": "上海申花",
    "Shanghai Shenhua": "上海申花",
    "Shandong Taishan": "山东泰山",
    "Chengdu Rongcheng": "成都蓉城",
    "Beijing Guoan": "北京国安",
    "Zhejiang FC": "浙江队",
    "Johor Darul Ta'zim FC": "柔佛新山",
    "Johor Darul Ta'zim": "柔佛新山",
    "Buriram United": "武里南联",
    "Bangkok United": "曼谷联",

    # Japan - J1 & J2 League
    "Vissel Kobe": "神户胜利船",
    "Sanfrecce Hiroshima": "广岛三箭",
    "FC Machida Zelvia": "町田泽维亚",
    "Machida Zelvia": "町田泽维亚",
    "Gamba Osaka": "大阪钢巴",
    "Kashima Antlers": "鹿岛鹿角",
    "Tokyo Verdy": "东京绿茵",
    "FC Tokyo": "东京FC",
    "Kawasaki Frontale": "川崎前锋",
    "Yokohama F. Marinos": "横滨水手",
    "Yokohama FC": "横滨FC",
    "Cerezo Osaka": "大阪樱花",
    "Nagoya Grampus": "名古屋鲸鱼",
    "Avispa Fukuoka": "福冈黄蜂",
    "Urawa": "浦和红钻",
    "Urawa Red Diamonds": "浦和红钻",
    "Kyoto Sanga": "京都不死鸟",
    "Shonan Bellmare": "湘南丽海",
    "Kashiwa Reysol": "柏太阳神",
    "Albirex Niigata": "新潟天鹅",
    "Jubilo Iwata": "磐田喜悦",
    "Sagan Tosu": "鸟栖砂岩",
    "Consadole Sapporo": "札幌冈萨多",
    "Shimizu S-Pulse": "清水心跳",
    "Fagiano Okayama": "冈山绿雉",
    "JEF United Chiba": "千叶市原",
    "V-Varen Nagasaki": "长崎成功丸",
    "Montedio Yamagata": "山形山神",
    "Vegalta Sendai": "仙台七夕",
    "Tokushima Vortis": "德岛漩涡",
    "Oita Trinita": "大分三神",
    "Ventforet Kofu": "甲府风林",
    "Mito Hollyhock": "水户蜀葵",
    "Roasso Kumamoto": "熊本深红",
    "Blaublitz Akita": "秋田蓝闪电",
    "Iwaki FC": "磐城FC",
    "Renofa Yamaguchi": "山口雷法",
    "Fujieda MYFC": "藤枝MYFC",
    "Ehime FC": "爱媛FC",
    "Omiya Ardija": "大宫松鼠",
    "Kataller Toyama": "富山胜利",
    "FC Imabari": "今治FC",
    "Tochigi City": "枥木城市",

    # South Korea - K League 1
    "Ulsan HD FC": "蔚山HD",
    "Ulsan Hyundai FC": "蔚山HD",
    "Gangwon FC": "江原FC",
    "Gimcheon Sangmu FC": "金泉尚武",
    "FC Seoul": "首尔FC",
    "Suwon FC": "水原FC",
    "Pohang Steelers": "浦项铁人",
    "Jeju United FC": "济州联",
    "Jeju SK FC": "济州联",
    "Daejeon Citizen": "大田市民",
    "Daejeon Hana Citizen": "大田市民",
    "Gwangju FC": "光州FC",
    "Jeonbuk Motors": "全北现代",
    "Jeonbuk Hyundai Motors": "全北现代",
    "Daegu FC": "大邱FC",
    "Incheon United FC": "仁川联",
    "FC Anyang": "安养FC",

    # Australia - A-League
    "Auckland": "奥克兰FC",
    "Auckland FC": "奥克兰FC",
    "Melbourne Victory": "墨尔本胜利",
    "Melbourne City": "墨尔本城",
    "Adelaide United": "阿德莱德联",
    "Sydney FC": "悉尼FC",
    "Macarthur": "麦克阿瑟FC",
    "Macarthur FC": "麦克阿瑟FC",
    "Western Sydney Wanderers": "西悉尼流浪者",
    "Western United": "西部联",
    "Central Coast Mariners": "中央海岸水手",
    "Wellington Phoenix": "惠灵顿凤凰",
    "Newcastle Jets": "纽卡斯尔喷气机",
    "Brisbane Roar": "布里斯班狮吼",
    "Perth Glory": "珀斯光荣",

    # USA/Canada - MLS
    "Inter Miami CF": "迈阿密国际",
    "Inter Miami": "迈阿密国际",
    "Los Angeles FC": "洛杉矶FC",
    "LA Galaxy": "洛杉矶银河",
    "Los Angeles Galaxy": "洛杉矶银河",
    "Seattle Sounders": "西雅图海湾人",
    "Columbus Crew": "哥伦布机员",
    "FC Cincinnati": "辛辛那提FC",
    "Orlando City SC": "奥兰多城",
    "New York Red Bulls": "纽约红牛",
    "New York City FC": "纽约城",
    "Atlanta United FC": "亚特兰大联",
    "Philadelphia Union": "费城联合",
    "Minnesota United FC": "明尼苏达联",
    "Vancouver Whitecaps": "温哥华白浪",
    "Real Salt Lake": "皇家盐湖城",
    "Colorado Rapids": "科罗拉多急流",
    "Houston Dynamo": "休斯顿迪纳摩",
    "San Jose Earthquakes": "圣何塞地震",
    "Portland Timbers": "波特兰伐木者",
    "Sporting Kansas City": "堪萨斯城竞技",
    "Austin FC": "奥斯汀FC",
    "Charlotte FC": "夏洛特FC",
    "Nashville SC": "纳什维尔SC",
    "FC Dallas": "达拉斯FC",
    "San Diego FC": "圣地亚哥FC",
    "St. Louis City SC": "圣路易斯城",
    "Toronto FC": "多伦多FC",
    "CF Montreal": "蒙特利尔CF",
    "DC United": "华盛顿联",
    "Chicago Fire": "芝加哥火焰",
    "New England Revolution": "新英格兰革命",

    # National Teams
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
    "Iraq": "伊拉克国家队",
    "Jordan": "约旦国家队",
    "Uzbekistan": "乌兹别克斯坦国家队",
    "Thailand": "泰国国家队",
    "Vietnam": "越南国家队",
    "Indonesia": "印度尼西亚国家队",
    "Egypt": "埃及国家队",
    "Nigeria": "尼日利亚国家队",
    "Algeria": "阿尔及利亚国家队",
    "Tunisia": "突尼斯国家队",
    "Cameroon": "喀麦隆国家队",
    "Ghana": "加纳国家队",
    "Ivory Coast": "科特迪瓦国家队",
    "Cote D'Ivoire": "科特迪瓦国家队",
    "Chile": "智利国家队",
    "Ecuador": "厄瓜多尔国家队",
    "Paraguay": "巴拉圭国家队",
    "Peru": "秘鲁国家队",
    "Venezuela": "委内瑞拉国家队",
    "Bolivia": "玻利维亚国家队",
    "Greece": "希腊国家队",
    "Czechia": "捷克国家队",
    "Czech Republic": "捷克国家队",
    "Hungary": "匈牙利国家队",
    "Romania": "罗马尼亚国家队",
    "Slovakia": "斯洛伐克国家队",
    "Slovenia": "斯洛文尼亚国家队",
    "New Zealand": "新西兰国家队",
    "Mali": "马里国家队",
    "Burkina Faso": "布基纳法索国家队",
    "Panama": "巴拿马国家队",
    "Costa Rica": "哥斯达黎加国家队",
    "Jamaica": "牙买加国家队",
    "Honduras": "洪都拉斯国家队",
    "South Africa": "南非国家队",
}

PLAYER_ZH_MAP: dict[str, str] = {
    "Kylian Mbappé": "姆巴佩",
    "Erling Haaland": "哈兰德",
    "Lamine Yamal": "亚马尔",
    "Jude Bellingham": "贝林厄姆",
    "Vinícius Júnior": "维尼修斯",
    "Vinicius Junior": "维尼修斯",
    "Mohamed Salah": "萨拉赫",
    "Bukayo Saka": "萨卡",
    "Harry Kane": "凯恩",
    "Jamal Musiala": "穆西亚拉",
    "Lautaro Martínez": "劳塔罗·马丁内斯",
    "Cole Palmer": "帕尔默",
    "Pedri": "佩德里",
    "Ousmane Dembélé": "登贝莱",
    "Lionel Messi": "梅西",
    "Cristiano Ronaldo": "C罗",
    "Son Heung-min": "孙兴慜",
    "Rodri": "罗德里",
    "Florian Wirtz": "维尔茨",
    "Martin Ødegaard": "厄德高",
    "Declan Rice": "赖斯",
    "William Saliba": "萨利巴",
    "Gabriel Magalhães": "加布里埃尔",
    "Virgil van Dijk": "范戴克",
    "Trent Alexander-Arnold": "阿诺德",
    "Alexis Mac Allister": "麦卡利斯特",
    "Luis Díaz": "路易斯·迪亚斯",
    "Cody Gakpo": "加克波",
    "Darwin Núñez": "努涅斯",
    "Phil Foden": "福登",
    "Kevin De Bruyne": "德布劳内",
    "Bernardo Silva": "贝尔纳多·席尔瓦",
    "Ruben Dias": "鲁本·迪亚斯",
    "Rúben Dias": "鲁本·迪亚斯",
    "Joško Gvardiol": "格瓦迪奥尔",
    "Bruno Fernandes": "布鲁诺·费尔南德斯",
    "Marcus Rashford": "拉什福德",
    "Alexander Isak": "伊萨克",
    "Bruno Guimarães": "吉马良斯",
    "Ollie Watkins": "沃特金斯",
    "Viktor Gyökeres": "哲凯赖什",
    "Robert Lewandowski": "莱万多夫斯基",
    "Raphinha": "拉菲尼亚",
    "Dani Olmo": "奥尔莫",
    "Gavi": "加维",
    "Federico Valverde": "巴尔韦德",
    "Thibaut Courtois": "库尔图瓦",
    "Antonio Rüdiger": "吕迪格",
    "Antoine Griezmann": "格列兹曼",
    "Julián Álvarez": "阿尔瓦雷斯",
    "Julian Alvarez": "阿尔瓦雷斯",
    "Nico Williams": "尼科·威廉姆斯",
    "Michael Olise": "奥利塞",
    "Joshua Kimmich": "基米希",
    "Manuel Neuer": "诺伊尔",
    "Serhou Guirassy": "吉拉西",
    "Omar Marmoush": "马尔穆什",
    "Victor Osimhen": "奥斯梅恩",
    "Khvicha Kvaratskhelia": "克瓦拉茨赫利亚",
    "Rafael Leão": "莱奥",
    "Christian Pulisic": "普利希奇",
    "Dušan Vlahović": "弗拉霍维奇",
    "Marcus Thuram": "马库斯·图拉姆",
    "Nicolò Barella": "巴雷拉",
    "Alessandro Bastoni": "巴斯托尼",
    "Achraf Hakimi": "阿什拉夫",
    "Bradley Barcola": "巴尔科拉",
    "Vitinha": "维蒂尼亚",
    "Gianluigi Donnarumma": "多纳鲁马",
    "Alisson Becker": "阿利松",
    "David Raya": "拉亚",
    "Emiliano Martínez": "大马丁内斯",
    "Kaoru Mitoma": "三笘薰",
    "Takefusa Kubo": "久保建英",
    "Wataru Endo": "远藤航",
    "Min-jae Kim": "金玟哉",
    "Lee Kang-in": "李刚仁",
    "Wu Lei": "武磊",
    "Neymar": "内马尔",
    "Luis Suárez": "苏亚雷斯",
    "Karim Benzema": "本泽马",
    "N'Golo Kanté": "坎特",
    "Sadio Mané": "马内",
    "Serge Gnabry": "格纳布里",
    "Leroy Sané": "萨内",
    "Kai Havertz": "哈弗茨",
    "Viktor Boniface": "博尼法斯",
    "Granit Xhaka": "扎卡",
    "Heung-Min Son": "孙兴慜",
    "Enzo Fernández": "恩佐·费尔南德斯",
    "Moisés Caicedo": "凯塞多",
    "Nicolas Jackson": "尼古拉斯·杰克逊",
    "Rasmus Højlund": "霍伊伦",
    "Alejandro Garnacho": "加纳乔",
    "Kobbie Mainoo": "梅努",
    "Diogo Jota": "若塔",
    "Dominik Szoboszlai": "索博斯洛伊",
    "Ryan Gravenberch": "赫拉芬贝赫",
    "Ibrahima Konaté": "科纳特",
    "Jérémy Doku": "多库",
    "Savinho": "萨维尼奥",
    "Pau Cubarsí": "库巴西",
    "Fermín López": "费尔明",
    "Eduardo Camavinga": "卡马文加",
    "Aurélien Tchouaméni": "琼阿梅尼",
    "Rodrygo": "罗德里戈",
    "Endrick": "恩德里克",
    "Arda Güler": "居莱尔",
    "Marc-André ter Stegen": "特尔施特根",
    "Jan Oblak": "奥布拉克",
    "Romelu Lukaku": "卢卡库",
    "Paulo Dybala": "迪巴拉",
    "Kenan Yıldız": "伊尔迪兹",
    "Teun Koopmeiners": "库普梅纳斯",
    "Mateo Retegui": "雷特吉",
    "Ademola Lookman": "卢克曼",
    "Scott McTominay": "麦克托米奈",
    "Mason Greenwood": "格林伍德",
    "Jonathan David": "乔纳森·戴维",
    "Timo Werner": "维尔纳",
    "Ronaldo Vieira": "罗纳尔多·维埃拉",
}

NAME_TOKEN_ZH: dict[str, str] = {
    # Common European / South American / African / Asian given & family names
    "silva": "席尔瓦", "santos": "桑托斯", "oliveira": "奥利维拉", "souza": "索萨",
    "pereira": "佩雷拉", "costa": "科斯塔", "rodrigues": "罗德里格斯", "rodriguez": "罗德里格斯",
    "martinez": "马丁内斯", "garcia": "加西亚", "fernandez": "费尔南德斯", "fernandes": "费尔南德斯",
    "lopez": "洛佩斯", "gonzalez": "冈萨雷斯", "hernandez": "埃尔南德斯", "perez": "佩雷斯",
    "sanchez": "桑切斯", "ramirez": "拉米雷斯", "torres": "托雷斯", "flores": "弗洛雷斯",
    "gomez": "戈麦斯", "gomes": "戈麦斯", "diaz": "迪亚斯", "dias": "迪亚斯",
    "morales": "莫拉莱斯", "alvarez": "阿尔瓦雷斯", "alves": "阿尔维斯", "ferreira": "费雷拉",
    "castro": "卡斯特罗", "romero": "罗梅罗", "suarez": "苏亚雷斯", "ruiz": "鲁伊斯",
    "muller": "穆勒", "mueller": "穆勒", "schmidt": "施密特", "schneider": "施耐德",
    "fischer": "菲舍尔", "weber": "韦伯", "meyer": "迈尔", "wagner": "瓦格纳",
    "becker": "贝克尔", "hoffmann": "霍夫曼", "koch": "科赫", "bauer": "鲍尔",
    "klein": "克莱因", "wolf": "沃尔夫", "brand": "布兰特", "brandt": "布兰特",
    "martin": "马丁", "bernard": "贝尔纳", "thomas": "托马斯", "petit": "佩蒂特",
    "robert": "罗伯特", "richard": "里夏尔", "durand": "杜兰德", "dubois": "杜布瓦",
    "moreau": "莫罗", "laurent": "洛朗", "simon": "西蒙", "michel": "米歇尔",
    "lefevre": "勒费夫尔", "garcia": "加西亚", "roux": "鲁", "vincent": "文森特",
    "rossi": "罗西", "russo": "鲁索", "ferrari": "法拉利", "esposito": "埃斯波西托",
    "bianchi": "比安奇", "romano": "罗马诺", "colombo": "科隆博", "ricci": "里奇",
    "marino": "马里诺", "greco": "格雷科", "bruno": "布鲁诺", "conti": "孔蒂",
    "smith": "史密斯", "jones": "琼斯", "williams": "威廉姆斯", "brown": "布朗",
    "taylor": "泰勒", "davies": "戴维斯", "evans": "埃文斯", "wilson": "威尔逊",
    "johnson": "约翰逊", "roberts": "罗伯茨", "robinson": "罗宾逊", "wright": "赖特",
    "walker": "沃克", "white": "怀特", "edwards": "爱德华兹", "green": "格林",
    "hall": "霍尔", "lewis": "刘易斯", "harris": "哈里斯", "clarke": "克拉克",
    "jackson": "杰克逊", "james": "詹姆斯", "phillips": "菲利普斯", "adams": "亚当斯",
    "kelly": "凯利", "murphy": "墨菲", "brennan": "布伦南", "byrne": "伯恩",
    "david": "戴维", "lucas": "卢卡斯", "mateo": "马特奥", "gabriel": "加布里埃尔",
    "rafael": "拉斐尔", "daniel": "丹尼尔", "samuel": "塞缪尔", "carlos": "卡洛斯",
    "luis": "路易斯", "miguel": "米格尔", "jose": "何塞", "juan": "胡安",
    "pedro": "佩德罗", "pablo": "巴勃罗", "diego": "迭戈", "javier": "哈维尔",
    "sergio": "塞尔吉奥", "marco": "马尔科", "matteo": "马特奥", "lorenzo": "洛伦佐",
    "andrea": "安德烈亚", "luca": "卢卡", "nicolo": "尼科洛", "federico": "费德里科",
    "alex": "亚历克斯", "alexander": "亚历山大", "benjamin": "本杰明", "oliver": "奥利弗",
    "jack": "杰克", "harry": "哈里", "charlie": "查理", "george": "乔治",
    "jacob": "雅各布", "adam": "亚当", "max": "马克斯", "felix": "菲利克斯",
    "leon": "莱昂", "julian": "尤利安", "jonas": "约纳斯", "paul": "保罗",
    "kevin": "凯文", "dennis": "丹尼斯", "florian": "弗洛里安", "tobias": "托比亚斯",
    "ivan": "伊万", "luka": "卢卡", "marko": "马尔科", "nikola": "尼古拉",
    "stefan": "斯特凡", "tomas": "托马斯", "jan": "扬", "jakub": "雅库布",
    "erik": "埃里克", "emil": "埃米尔", "oskar": "奥斯卡", "viktor": "维克托",
    "sebastian": "塞巴斯蒂安", "andreas": "安德烈亚斯", "christian": "克里斯蒂安",
    "kim": "金", "park": "朴", "lee": "李", "son": "孙", "jung": "郑", "kang": "姜",
    "cho": "曹", "choi": "崔", "yoon": "尹", "lim": "林", "han": "韩", "seo": "徐",
    "tanaka": "田中", "sato": "佐藤", "suzuki": "铃木", "takahashi": "高桥",
    "watanabe": "渡边", "ito": "伊藤", "yamamoto": "山本", "nakamura": "中村",
    "kobayashi": "小林", "kato": "加藤", "yoshida": "吉田", "yamada": "山田",
    "sasaki": "佐佐木", "yamaguchi": "山口", "matsumoto": "松 本".replace(" ", ""),
    "inoue": "井上", "kimura": "木村", "hayashi": "林", "shimizu": "清水", "yamazaki": "山崎",
    "mori": "森", "abe": "阿部", "ikeda": "池田", "hashimoto": "桥本", "ogawa": "小川",
    "sakamoto": "坂本", "aoki": "青木", "fujita": "藤田", "okada": "冈田", "goto": "后藤",
    "mohamed": "穆罕默德", "mohammed": "穆罕默德", "ahmed": "艾哈迈德", "ali": "阿里",
    "hassan": "哈桑", "hussein": "侯赛因", "ibrahim": "易卜拉欣", "omar": "奥马尔",
    "youssef": "优素福", "khaled": "哈立德", "tarek": "塔里克", "karim": "卡里姆",
    "abdullah": "阿卜杜拉", "salem": "萨利姆", "saeed": "赛义德", "al": "阿尔",
    "diallo": "迪亚洛", "traoe": "特拉奥雷", "traore": "特拉奥雷", "kone": "科内",
    "camara": "卡马拉", "keita": "凯塔", "kouyate": "库亚特", "mend": "门迪", "mendy": "门迪",
    "sarr": "萨尔", "ndiaye": "恩迪亚耶", "cisse": "西塞", "toure": "图雷",
    "da": "达", "de": "德", "del": "德尔", "dos": "多斯", "van": "范", "von": "冯",
    "junior": "儒尼奥尔", "jr": "小",
}

_SYLLABLE_ZH = {
    "ba": "巴", "be": "贝", "bi": "比", "bo": "博", "bu": "布", "by": "比",
    "ca": "卡", "ce": "塞", "ci": "奇", "co": "科", "cu": "库", "cy": "西",
    "cha": "查", "che": "切", "chi": "奇", "cho": "乔", "chu": "丘",
    "da": "达", "de": "德", "di": "迪", "do": "多", "du": "杜", "dy": "迪",
    "fa": "法", "fe": "费", "fi": "菲", "fo": "福", "fu": "富", "fy": "菲",
    "ga": "加", "ge": "格", "gi": "吉", "go": "戈", "gu": "古", "gy": "吉",
    "ha": "哈", "he": "赫", "hi": "希", "ho": "霍", "hu": "胡", "hy": "希",
    "ja": "亚", "je": "耶", "ji": "吉", "jo": "约", "ju": "尤", "jy": "吉",
    "ka": "卡", "ke": "克", "ki": "基", "ko": "科", "ku": "库", "ky": "基",
    "la": "拉", "le": "莱", "li": "利", "lo": "洛", "lu": "卢", "ly": "利",
    "ma": "马", "me": "梅", "mi": "米", "mo": "莫", "mu": "穆", "my": "米",
    "na": "纳", "ne": "内", "ni": "尼", "no": "诺", "nu": "努", "ny": "尼",
    "pa": "帕", "pe": "佩", "pi": "皮", "po": "波", "pu": "普", "py": "皮",
    "qa": "卡", "qe": "克", "qi": "齐", "qo": "科", "qu": "库",
    "ra": "拉", "re": "雷", "ri": "里", "ro": "罗", "ru": "鲁", "ry": "里",
    "sa": "萨", "se": "塞", "si": "西", "so": "索", "su": "苏", "sy": "西",
    "sha": "沙", "she": "谢", "shi": "希", "sho": "肖", "shu": "舒",
    "ta": "塔", "te": "特", "ti": "蒂", "to": "托", "tu": "图", "ty": "蒂",
    "tha": "萨", "the": "瑟", "thi": "蒂", "tho": "托", "thu": "图",
    "va": "瓦", "ve": "维", "vi": "维", "vo": "沃", "vu": "武", "vy": "维",
    "wa": "瓦", "we": "韦", "wi": "威", "wo": "沃", "wu": "伍", "wy": "威",
    "xa": "沙", "xe": "谢", "xi": "西", "xo": "索", "xu": "舒",
    "ya": "亚", "ye": "耶", "yi": "伊", "yo": "约", "yu": "尤",
    "za": "扎", "ze": "泽", "zi": "齐", "zo": "佐", "zu": "祖", "zy": "齐",
}

_CODA_ZH = {
    "b": "布", "c": "克", "d": "德", "f": "夫", "g": "格", "h": "", "j": "奇",
    "k": "克", "l": "尔", "m": "姆", "n": "恩", "ng": "昂", "p": "普", "q": "克",
    "r": "尔", "s": "斯", "t": "特", "v": "夫", "w": "夫", "x": "克斯", "z": "茨",
    "ch": "奇", "sh": "什", "th": "斯", "ck": "克", "nd": "德", "nt": "特", "st": "斯特",
}

_VOWEL_ZH = {"a": "阿", "e": "埃", "i": "伊", "o": "奥", "u": "乌", "y": "伊"}

POSITION_ZH_MAP: dict[str, str] = {
    "Goalkeeper": "门将",
    "Defence": "后卫",
    "Defender": "后卫",
    "Centre-Back": "中后卫",
    "Left-Back": "左后卫",
    "Right-Back": "右后卫",
    "Midfield": "中场",
    "Midfielder": "中场",
    "Defensive Midfield": "后腰",
    "Central Midfield": "中前卫",
    "Attacking Midfield": "前腰",
    "Left Midfield": "左前卫",
    "Right Midfield": "右前卫",
    "Offence": "前锋",
    "Forward": "前锋",
    "Attacker": "前锋",
    "Centre-Forward": "中锋",
    "Left Winger": "左边锋",
    "Right Winger": "右边锋",
}


def contains_cjk(text: str) -> bool:
    return any("\u4e00" <= ch <= "\u9fff" for ch in text)


def strip_accents(raw: str) -> str:
    decomposed = unicodedata.normalize("NFD", raw)
    return "".join(ch for ch in decomposed if unicodedata.category(ch) != "Mn")


def _normalize_team_key(raw: str) -> str:
    stripped = strip_accents(raw)
    cleaned = (
        stripped.lower()
        .replace("ø", "o")
        .replace("ł", "l")
        .replace("đ", "d")
        .replace("&", " ")
        .replace(".", " ")
        .replace("-", " ")
    )
    cleaned = re.sub(
        r"\b(fc|cf|afc|kv|bc|sc|sv|sk|fk|ssc|ac|as|us|rc|rcd|cd|ud|ca|ogc|vfl|vfb|tsg|fsv|bsc|gnk|sl|jk|ff|sd|ad|spvgg|04|05|07|96|98|1846|1848|1893|1899|1901|1907|1909|1910|1913|1945)\b",
        " ",
        cleaned,
    )
    return re.sub(r"\s+", " ", cleaned).strip()


_NORMALIZED_TEAM_ZH_MAP: dict[str, str] = {_normalize_team_key(k): v for k, v in TEAM_ZH_MAP.items()}
_NORMALIZED_PLAYER_ZH_MAP: dict[str, str] = {
    strip_accents(k).lower().strip(): v for k, v in PLAYER_ZH_MAP.items()
}


def _phonetic_word(word: str) -> str:
    w = re.sub(r"[^a-z]", "", strip_accents(word).lower())
    if not w:
        return word
    if len(w) == 1:
        return word.upper()
    out: list[str] = []
    i = 0
    vowels = set("aeiouy")
    while i < len(w):
        matched = False
        # Try onset+vowel (3 chars then 2 chars)
        for c_len in (2, 1):
            if i + c_len < len(w) and w[i + c_len] in vowels:
                key = w[i : i + c_len + 1]
                if key in _SYLLABLE_ZH:
                    out.append(_SYLLABLE_ZH[key])
                    i += c_len + 1
                    # Consume trailing double vowel like oo/ea/ou
                    while i < len(w) and w[i] in vowels and i > 0 and w[i] == w[i - 1]:
                        i += 1
                    matched = True
                    break
        if matched:
            continue
        if w[i] in vowels:
            out.append(_VOWEL_ZH[w[i]])
            i += 1
            continue
        if i + 2 <= len(w) and w[i : i + 2] in _CODA_ZH:
            c = _CODA_ZH[w[i : i + 2]]
            if c:
                out.append(c)
            i += 2
            continue
        c = _CODA_ZH.get(w[i], "")
        if c:
            out.append(c)
        i += 1
    result = "".join(out)
    return result if result else word


def transliterate_name_to_zh(name: str, is_club: bool = False) -> str:
    cleaned = name.strip()
    if not cleaned:
        return ""
    if contains_cjk(cleaned):
        return cleaned

    tokens = [t for t in re.split(r"[\s]+", cleaned) if t]
    if is_club:
        ignored = {"fc", "sc", "cf", "afc", "united", "city"}
        filtered = [t for t in tokens if t.lower().strip(".") not in ignored]
        if filtered:
            tokens = filtered

    parts: list[str] = []
    for idx, tok in enumerate(tokens):
        clean_tok = strip_accents(tok).strip(".,")
        if not clean_tok:
            continue
        if len(clean_tok) == 1 and "." in tok:
            parts.append(clean_tok.upper())
            continue
        low = clean_tok.lower()
        if "-" in low:
            sub = [_phonetic_word_or_dict(s) for s in low.split("-") if s]
            parts.append("·".join(sub))
        else:
            parts.append(_phonetic_word_or_dict(low))
    if not parts:
        return cleaned
    return ("" if is_club else "·").join(parts) if not is_club else "".join(parts)


def _phonetic_word_or_dict(token_lower: str) -> str:
    return NAME_TOKEN_ZH.get(token_lower) or _phonetic_word(token_lower)


def localize_team(name: str, short_name: str | None = None) -> str:
    if contains_cjk(name):
        return name
    # Detect youth suffix like "U17", "U20", "U21", "U23"
    m_youth = re.match(r"^(.*?)\s+(U17|U19|U20|U21|U23)(?:\s+.*)?$", name.strip(), re.IGNORECASE)
    if m_youth:
        base_team = m_youth.group(1).strip()
        age_tag = m_youth.group(2).upper()
        base_zh = localize_team(base_team).removesuffix("国家队")
        return f"{base_zh}{age_tag}"

    for cand in (name, short_name or ""):
        if cand in TEAM_ZH_MAP:
            return TEAM_ZH_MAP[cand]
        norm = _normalize_team_key(cand)
        if norm in _NORMALIZED_TEAM_ZH_MAP:
            return _NORMALIZED_TEAM_ZH_MAP[norm]

    return transliterate_name_to_zh(short_name or name, is_club=True)


def localize_player(name: str) -> tuple[str, bool]:
    """Returns (zh_name, is_transliterated)."""
    cleaned = name.strip()
    if not cleaned:
        return ("", False)
    if contains_cjk(cleaned):
        return (cleaned, False)
    if cleaned in PLAYER_ZH_MAP:
        return (PLAYER_ZH_MAP[cleaned], False)
    norm = strip_accents(cleaned).lower().strip()
    if norm in _NORMALIZED_PLAYER_ZH_MAP:
        return (_NORMALIZED_PLAYER_ZH_MAP[norm], False)
    return (transliterate_name_to_zh(cleaned, is_club=False), True)


def localize_position(pos: str | None) -> str:
    if not pos:
        return "球员"
    p = pos.strip()
    return POSITION_ZH_MAP.get(p, p)


def localize_espn_group(name: str | None) -> str:
    if not name:
        return ""
    low = name.strip().lower()
    if low in {"regular season", "league phase", "overall"}:
        return ""
    mapping = {
        "eastern conference": "东部联盟",
        "western conference": "西部联盟",
        "east region": "东亚区",
        "west region": "西亚区",
        "east": "东区",
        "west": "西区",
    }
    if low in mapping:
        return mapping[low]
    if low.startswith("group "):
        return "小组 " + name.strip()[6:]
    return name.strip()


def localize_espn_zone(desc: str | None, code: str, rank: int, total_rows: int) -> tuple[str, str]:
    d = (desc or "").lower()
    if "relegation playoff" in d or "relegation play-off" in d:
        return ("RELEGATION_PLAYOFF", "升降级附加赛区")
    if "relegation" in d or "relegated" in d:
        return ("RELEGATION", "降级区")
    if "champions league" in d and ("qualif" in d or "playoff" in d):
        return ("UCL_QUAL", "欧冠资格赛区")
    if "champions league" in d:
        return ("UCL", "欧冠正赛区")
    if "europa league" in d:
        return ("UEL", "欧联杯区")
    if "conference league" in d:
        return ("UECL", "欧协联区")
    if "round of 16" in d or "next round" in d or "knockout stage" in d:
        return ("UCL", "16强直通区")
    if "knockout" in d or "playoff" in d or "play-off" in d or "finals series" in d or "final series" in d:
        return ("PLAYOFF", "季后/淘汰附加赛区")
    if "promotion playoff" in d:
        return ("PLAYOFF", "升级附加赛区")
    if "promotion" in d or "promoted" in d:
        return ("PROMOTION", "升级区")
    return ("NONE", "")
