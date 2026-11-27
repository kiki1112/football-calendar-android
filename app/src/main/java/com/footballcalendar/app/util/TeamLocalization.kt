package com.footballcalendar.app.util

import com.footballcalendar.app.model.CompetitionCatalog
import java.text.Normalizer

object TeamLocalization {
    private val exactTeamZhMap = mapOf(
        // England - Premier League & Championship
        "Arsenal FC" to "阿森纳", "Arsenal" to "阿森纳",
        "Manchester City FC" to "曼城", "Man City" to "曼城",
        "Liverpool FC" to "利物浦", "Liverpool" to "利物浦",
        "Chelsea FC" to "切尔西", "Chelsea" to "切尔西",
        "Manchester United FC" to "曼联", "Man United" to "曼联",
        "Tottenham Hotspur FC" to "热刺", "Tottenham" to "热刺",
        "Newcastle United FC" to "纽卡斯尔联", "Newcastle" to "纽卡斯尔联",
        "Aston Villa FC" to "阿斯顿维拉", "Aston Villa" to "阿斯顿维拉",
        "Brighton & Hove Albion FC" to "布莱顿", "Brighton Hove" to "布莱顿", "Brighton" to "布莱顿",
        "AFC Bournemouth" to "伯恩茅斯", "Bournemouth" to "伯恩茅斯",
        "Brentford FC" to "布伦特福德", "Brentford" to "布伦特福德",
        "Fulham FC" to "富勒姆", "Fulham" to "富勒姆",
        "Everton FC" to "埃弗顿", "Everton" to "埃弗顿",
        "West Ham United FC" to "西汉姆联", "West Ham" to "西汉姆联",
        "Crystal Palace FC" to "水晶宫", "Crystal Palace" to "水晶宫",
        "Wolverhampton Wanderers FC" to "狼队", "Wolverhampton" to "狼队", "Wolves" to "狼队",
        "Nottingham Forest FC" to "诺丁汉森林", "Nottingham" to "诺丁汉森林",
        "Leicester City FC" to "莱斯特城", "Leicester" to "莱斯特城",
        "Ipswich Town FC" to "伊普斯威奇", "Ipswich Town" to "伊普斯威奇",
        "Southampton FC" to "南安普顿", "Southampton" to "南安普顿",
        "Leeds United FC" to "利兹联", "Leeds United" to "利兹联",
        "Burnley FC" to "伯恩利", "Burnley" to "伯恩利",
        "Sheffield United FC" to "谢菲尔德联", "Sheffield Utd" to "谢菲尔德联",
        "Sunderland AFC" to "桑德兰", "Sunderland" to "桑德兰",
        "Hull City AFC" to "赫尔城", "Hull City" to "赫尔城",
        "Coventry City FC" to "考文垂", "Coventry City" to "考文垂",
        "Luton Town FC" to "卢顿",
        "Middlesbrough FC" to "米德尔斯堡", "Middlesbrough" to "米德尔斯堡",
        "Norwich City FC" to "诺维奇", "Norwich" to "诺维奇",
        "West Bromwich Albion FC" to "西布朗", "West Brom" to "西布朗",
        "Watford FC" to "沃特福德",
        "Blackburn Rovers FC" to "布莱克本",
        "Stoke City FC" to "斯托克城",
        "Bristol City FC" to "布里斯托尔城",
        "Queens Park Rangers FC" to "女王公园巡游者", "QPR" to "女王公园巡游者",
        "Swansea City AFC" to "斯旺西",
        "Cardiff City FC" to "卡迪夫城",
        "Millwall FC" to "米尔沃尔",
        "Preston North End FC" to "普雷斯顿",
        "Sheffield Wednesday FC" to "谢周三",
        "Derby County FC" to "德比郡",
        "Portsmouth FC" to "朴茨茅斯",
        "Oxford United FC" to "牛津联",
        "Plymouth Argyle FC" to "普利茅斯",
        "Birmingham City FC" to "伯明翰",
        "Wrexham AFC" to "雷克瑟姆",
        "Charlton Athletic FC" to "查尔顿",

        // Spain - La Liga & Segunda
        "Real Madrid CF" to "皇家马德里", "Real Madrid" to "皇家马德里",
        "FC Barcelona" to "巴塞罗那", "Barcelona" to "巴塞罗那",
        "Club Atlético de Madrid" to "马德里竞技", "Atlético Madrid" to "马德里竞技", "Atletico Madrid" to "马德里竞技",
        "Athletic Club" to "毕尔巴鄂竞技", "Athletic Bilbao" to "毕尔巴鄂竞技",
        "Real Sociedad de Fútbol" to "皇家社会", "Real Sociedad" to "皇家社会",
        "Real Betis Balompié" to "皇家贝蒂斯", "Real Betis" to "皇家贝蒂斯",
        "Villarreal CF" to "比利亚雷亚尔",
        "Girona FC" to "赫罗纳",
        "Sevilla FC" to "塞维利亚",
        "Valencia CF" to "巴伦西亚",
        "RC Celta de Vigo" to "塞尔塔", "Celta Vigo" to "塞尔塔",
        "CA Osasuna" to "奥萨苏纳",
        "Rayo Vallecano de Madrid" to "巴列卡诺", "Rayo Vallecano" to "巴列卡诺",
        "RCD Mallorca" to "马略卡",
        "Getafe CF" to "赫塔费",
        "Deportivo Alavés" to "阿拉维斯", "Alavés" to "阿拉维斯",
        "RCD Espanyol de Barcelona" to "西班牙人", "Espanyol" to "西班牙人",
        "UD Las Palmas" to "拉斯帕尔马斯",
        "Real Valladolid CF" to "巴拉多利德",
        "CD Leganés" to "莱加内斯",
        "Levante UD" to "莱万特",
        "Elche CF" to "埃尔切",
        "Real Oviedo" to "奥维耶多",
        "Racing Santander" to "桑坦德竞技", "Real Racing Club de Santander" to "桑坦德竞技", "Santander" to "桑坦德竞技",
        "RC Deportivo de La Coruña" to "拉科鲁尼亚", "Deportivo" to "拉科鲁尼亚",
        "Málaga CF" to "马拉加", "Malaga" to "马拉加",
        "Granada CF" to "格拉纳达", "UD Almería" to "阿尔梅里亚", "Cádiz CF" to "加的斯",

        // Germany - Bundesliga & 2. Bundesliga
        "FC Bayern München" to "拜仁慕尼黑", "Bayern Munich" to "拜仁慕尼黑",
        "Borussia Dortmund" to "多特蒙德",
        "Bayer 04 Leverkusen" to "勒沃库森",
        "RB Leipzig" to "RB莱比锡",
        "VfB Stuttgart" to "斯图加特",
        "Eintracht Frankfurt" to "法兰克福",
        "VfL Wolfsburg" to "沃尔夫斯堡",
        "SC Freiburg" to "弗赖堡",
        "TSG 1899 Hoffenheim" to "霍芬海姆",
        "SV Werder Bremen" to "云达不莱梅",
        "1. FC Union Berlin" to "柏林联合",
        "FC Augsburg" to "奥格斯堡",
        "Borussia Mönchengladbach" to "门兴格拉德巴赫",
        "1. FSV Mainz 05" to "美因茨",
        "1. FC Heidenheim 1846" to "海登海姆",
        "VfL Bochum 1848" to "波鸿",
        "FC St. Pauli 1910" to "圣保利",
        "Holstein Kiel" to "荷尔斯泰因基尔",
        "Hamburger SV" to "汉堡",
        "1. FC Köln" to "科隆",
        "FC Schalke 04" to "沙尔克04",
        "SV 07 Elversberg" to "埃尔弗斯贝格", "Elversberg" to "埃尔弗斯贝格",
        "SC Paderborn 07" to "帕德博恩", "Paderborn" to "帕德博恩",
        "Hertha BSC" to "柏林赫塔",
        "Hannover 96" to "汉诺威96",
        "Fortuna Düsseldorf" to "杜塞尔多夫",

        // Italy
        "FC Internazionale Milano" to "国际米兰", "Inter" to "国际米兰", "Inter Milan" to "国际米兰",
        "AC Milan" to "AC米兰",
        "Juventus FC" to "尤文图斯",
        "SSC Napoli" to "那不勒斯",
        "AS Roma" to "罗马",
        "SS Lazio" to "拉齐奥",
        "Atalanta BC" to "亚特兰大",
        "ACF Fiorentina" to "佛罗伦萨",
        "Bologna FC 1909" to "博洛尼亚",
        "Torino FC" to "都灵",
        "Udinese Calcio" to "乌迪内斯",
        "Genoa CFC" to "热那亚",
        "Cagliari Calcio" to "卡利亚里",
        "Hellas Verona FC" to "维罗纳",
        "US Lecce" to "莱切",
        "Empoli FC" to "恩波利",
        "Como 1907" to "科莫",
        "Parma Calcio 1913" to "帕尔马",
        "Venezia FC" to "威尼斯",
        "AC Monza" to "蒙扎",
        "US Sassuolo Calcio" to "萨索洛",
        "Pisa SC" to "比萨",
        "US Cremonese" to "克雷莫内塞",

        // France
        "Paris Saint-Germain FC" to "巴黎圣日耳曼", "Paris Saint Germain" to "巴黎圣日耳曼",
        "Olympique de Marseille" to "马赛",
        "AS Monaco FC" to "摩纳哥",
        "Olympique Lyonnais" to "里昂",
        "LOSC Lille" to "里尔",
        "OGC Nice" to "尼斯",
        "RC Lens" to "朗斯",
        "Stade Rennais FC 1901" to "雷恩",
        "Stade Brestois 29" to "布雷斯特",
        "RC Strasbourg Alsace" to "斯特拉斯堡",
        "Toulouse FC" to "图卢兹",
        "FC Nantes" to "南特",
        "AJ Auxerre" to "欧塞尔",
        "Angers SCO" to "昂热",
        "Le Havre AC" to "勒阿弗尔",
        "Paris FC" to "巴黎FC",
        "FC Metz" to "梅斯",
        "FC Lorient" to "洛里昂",

        // European Cups, DED, PPL, BSA
        "Club Brugge KV" to "布鲁日", "Club Brugge" to "布鲁日",
        "Feyenoord" to "费耶诺德", "Feyenoord Rotterdam" to "费耶诺德",
        "PSV" to "埃因霍温", "PSV Eindhoven" to "埃因霍温",
        "AFC Ajax" to "阿贾克斯", "Ajax" to "阿贾克斯",
        "AZ" to "阿尔克马尔", "AZ Alkmaar" to "阿尔克马尔",
        "FC Twente '65" to "特温特", "FC Utrecht" to "乌得勒支",
        "Sporting Clube de Portugal" to "葡萄牙体育", "Sporting CP" to "葡萄牙体育",
        "Sport Lisboa e Benfica" to "本菲卡", "SL Benfica" to "本菲卡",
        "FC Porto" to "波尔图", "Sporting Clube de Braga" to "布拉加", "SC Braga" to "布拉加",
        "Vitória SC" to "吉马良斯",
        "Galatasaray" to "加拉塔萨雷",
        "Fenerbahçe SK" to "费内巴切", "Fenerbahce" to "费内巴切",
        "Beşiktaş JK" to "贝西克塔斯", "Besiktas" to "贝西克塔斯",
        "Hapoel Be'er" to "贝尔谢巴夏普尔",
        "Vitoria" to "维多利亚",
        "Internazionale" to "国际米兰",
        "Kashima" to "鹿岛鹿角",
        "Port FC" to "泰港",
        "Olympiacos" to "奥林匹亚科斯", "PAOK" to "塞萨洛尼基", "Panathinaikos" to "帕纳辛奈科斯",
        "Rangers FC" to "流浪者", "Celtic FC" to "凯尔特人",
        "Royale Union Saint-Gilloise" to "圣吉罗斯联合", "Union St. Gilloise" to "圣吉罗斯联合",
        "RSC Anderlecht" to "安德莱赫特", "KRC Genk" to "亨克", "KAA Gent" to "根特",
        "FK Bodø/Glimt" to "博德闪耀", "Bodo/Glimt" to "博德闪耀",
        "FC København" to "哥本哈根", "FC Midtjylland" to "中日德兰", "Malmö FF" to "马尔默",
        "SK Slavia Praha" to "布拉格斯拉维亚", "AC Sparta Praha" to "布拉格斯巴达",
        "FC Viktoria Plzeň" to "比尔森胜利", "FC Viktoria Plzen" to "比尔森胜利",
        "Viktoria Plzen" to "比尔森胜利", "Plzen" to "比尔森胜利",
        "GNK Dinamo Zagreb" to "萨格勒布迪纳摩",
        "FK Crvena Zvezda" to "贝尔格莱德红星", "FK Shakhtar Donetsk" to "顿涅茨克矿工",
        "Dynamo Kyiv" to "基辅迪纳摩", "Qarabağ Ağdam FK" to "卡拉巴赫", "Sabah FK" to "萨巴赫",
        "Ferencvárosi TC" to "费伦茨瓦罗斯", "Legia Warszawa" to "华沙莱吉亚",
        "BSC Young Boys" to "年轻人", "FC Basel 1893" to "巴塞尔",
        "SK Sturm Graz" to "格拉茨风暴", "FC Red Bull Salzburg" to "萨尔茨堡红牛",
        "SK Rapid Wien" to "维也纳快速", "NK Celje" to "采列", "FCSB" to "布加勒斯特星",
        "CR Flamengo" to "弗拉门戈", "Flamengo" to "弗拉门戈",
        "SE Palmeiras" to "帕尔梅拉斯", "Palmeiras" to "帕尔梅拉斯",
        "Botafogo FR" to "博塔弗戈", "Botafogo" to "博塔弗戈",
        "Clube Atlético Mineiro" to "米内罗竞技", "CA Mineiro" to "米内罗竞技",
        "Atlético-MG" to "米内罗竞技", "Atletico-MG" to "米内罗竞技", "Atlético Mineiro" to "米内罗竞技",
        "SC Corinthians Paulista" to "科林蒂安", "Corinthians" to "科林蒂安",
        "Cruzeiro EC" to "克鲁塞罗", "Cruzeiro" to "克鲁塞罗",
        "Fluminense FC" to "弗鲁米嫩塞", "Fluminense" to "弗鲁米嫩塞",
        "EC Bahia" to "巴伊亚", "Bahia" to "巴伊亚",
        "EC Vitória" to "维多利亚", "Vitória" to "维多利亚",
        "RB Bragantino" to "布拉干蒂诺RB", "Bragantino" to "布拉干蒂诺RB",
        "Mirassol FC" to "米拉索尔", "Mirassol" to "米拉索尔",
        "Chapecoense AF" to "沙佩科恩斯", "Chapecoense" to "沙佩科恩斯",
        "Coritiba FBC" to "科里蒂巴", "Coritiba" to "科里蒂巴",
        "Clube do Remo" to "雷莫", "Remo" to "雷莫",
        "CA Paranaense" to "巴拉纳竞技", "Athletico-PR" to "巴拉纳竞技",
        "São Paulo FC" to "圣保罗", "SC Internacional" to "巴西国际", "Grêmio FBPA" to "格雷米奥",
        "Club Atlético River Plate" to "河床", "CA Boca Juniors" to "博卡青年",

        // AFC Champions League Elite & Asian clubs
        "Al-Hilal Saudi FC" to "利雅得新月", "Al-Hilal" to "利雅得新月", "Al Hilal" to "利雅得新月",
        "Al-Nassr FC" to "利雅得胜利", "Al-Nassr" to "利雅得胜利", "Al Nassr" to "利雅得胜利",
        "Al-Ahli Saudi FC" to "吉达国民", "Al-Ahli" to "吉达国民",
        "Al-Ittihad Club" to "吉达联合", "Al-Ittihad" to "吉达联合",
        "Al-Sadd SC" to "萨德", "Al-Sadd" to "萨德",
        "Al-Gharafa" to "加拉法", "Al-Rayyan" to "赖扬", "Al-Ain FC" to "艾因",
        "Al-Wasl" to "迪拜祈祷", "Persepolis FC" to "波斯波利斯", "Esteghlal FC" to "德黑兰独立",
        "Tractor" to "大不里士拖拉机", "Tractor Sazi" to "大不里士拖拉机",
        "Shabab Al Ahli" to "迪拜国民青年", "Shabab Al-Ahli Dubai" to "迪拜国民青年",
        "Al-Quwa Al-Jawiya" to "巴格达空军", "Al-Shamal" to "沙马尔",
        "Cong An Ha Noi" to "河内公安", "Neftchi Fergana" to "费尔干纳油工",
        "Pakhtakor" to "塔什干棉农",
        "Shanghai Port" to "上海海港", "Shanghai Port FC" to "上海海港",
        "Shanghai Shenhua" to "上海申花", "Shanghai Shenhua FC" to "上海申花",
        "Shandong Taishan" to "山东泰山", "Chengdu Rongcheng" to "成都蓉城",
        "Beijing Guoan" to "北京国安", "Zhejiang FC" to "浙江队",
        "Johor Darul Ta'zim" to "柔佛新山", "Buriram United" to "武里南联",

        // Japan J1 & J2
        "Vissel Kobe" to "神户胜利船",
        "Sanfrecce Hiroshima" to "广岛三箭",
        "FC Machida Zelvia" to "町田泽维亚", "Machida Zelvia" to "町田泽维亚",
        "Gamba Osaka" to "大阪钢巴",
        "Kashima Antlers" to "鹿岛鹿角",
        "Tokyo Verdy" to "东京绿茵",
        "FC Tokyo" to "东京FC",
        "Kawasaki Frontale" to "川崎前锋",
        "Yokohama F. Marinos" to "横滨水手", "Yokohama FC" to "横滨FC",
        "Cerezo Osaka" to "大阪樱花",
        "Nagoya Grampus" to "名古屋鲸鱼",
        "Avispa Fukuoka" to "福冈黄蜂",
        "Urawa" to "浦和红钻", "Urawa Red Diamonds" to "浦和红钻",
        "Kyoto Sanga" to "京都不死鸟",
        "Shonan Bellmare" to "湘南丽海",
        "Kashiwa Reysol" to "柏太阳神",
        "Albirex Niigata" to "新潟天鹅",
        "Jubilo Iwata" to "磐田喜悦",
        "Sagan Tosu" to "鸟栖砂岩",
        "Consadole Sapporo" to "札幌冈萨多",
        "Shimizu S-Pulse" to "清水心跳",
        "Fagiano Okayama" to "冈山绿雉",
        "JEF United Chiba" to "千叶市原",
        "V-Varen Nagasaki" to "长崎成功丸",
        "Montedio Yamagata" to "山形山神",
        "Vegalta Sendai" to "仙台七夕",
        "Tokushima Vortis" to "德岛漩涡",
        "Oita Trinita" to "大分三神",
        "Ventforet Kofu" to "甲府风林",
        "Mito Hollyhock" to "水户蜀葵",
        "Roasso Kumamoto" to "熊本深红",
        "Blaublitz Akita" to "秋田蓝闪电",
        "Iwaki FC" to "磐城FC",
        "Renofa Yamaguchi" to "山口雷法",
        "Fujieda MYFC" to "藤枝MYFC",
        "Ehime FC" to "爱媛FC",
        "Omiya Ardija" to "大宫松鼠",
        "Kataller Toyama" to "富山胜利",
        "FC Imabari" to "今治FC",
        "Tochigi City" to "枥木城市",

        // South Korea K League 1
        "Ulsan HD FC" to "蔚山HD", "Ulsan Hyundai FC" to "蔚山HD",
        "Gangwon FC" to "江原FC", "Gimcheon Sangmu FC" to "金泉尚武",
        "FC Seoul" to "首尔FC", "Suwon FC" to "水原FC",
        "Pohang Steelers" to "浦项铁人",
        "Jeju United FC" to "济州联", "Jeju SK FC" to "济州联",
        "Daejeon Citizen" to "大田市民", "Daejeon Hana Citizen" to "大田市民",
        "Gwangju FC" to "光州FC",
        "Jeonbuk Motors" to "全北现代", "Jeonbuk Hyundai Motors" to "全北现代",
        "Daegu FC" to "大邱FC", "Incheon United FC" to "仁川联", "FC Anyang" to "安养FC",

        // Australia A-League
        "Auckland" to "奥克兰FC", "Auckland FC" to "奥克兰FC",
        "Melbourne Victory" to "墨尔本胜利", "Melbourne City" to "墨尔本城",
        "Adelaide United" to "阿德莱德联", "Sydney FC" to "悉尼FC",
        "Macarthur" to "麦克阿瑟FC", "Macarthur FC" to "麦克阿瑟FC",
        "Western Sydney Wanderers" to "西悉尼流浪者", "Western United" to "西部联",
        "Central Coast Mariners" to "中央海岸水手", "Wellington Phoenix" to "惠灵顿凤凰",
        "Newcastle Jets" to "纽卡斯尔喷气机", "Brisbane Roar" to "布里斯班狮吼", "Perth Glory" to "珀斯光荣",

        // MLS
        "Inter Miami CF" to "迈阿密国际", "Inter Miami" to "迈阿密国际",
        "Los Angeles FC" to "洛杉矶FC",
        "LA Galaxy" to "洛杉矶银河", "Los Angeles Galaxy" to "洛杉矶银河",
        "Seattle Sounders" to "西雅图海湾人",
        "Columbus Crew" to "哥伦布机员",
        "FC Cincinnati" to "辛辛那提FC",
        "Orlando City SC" to "奥兰多城",
        "New York Red Bulls" to "纽约红牛",
        "New York City FC" to "纽约城",
        "Atlanta United FC" to "亚特兰大联",
        "Philadelphia Union" to "费城联合",
        "Minnesota United FC" to "明尼苏达联",
        "Vancouver Whitecaps" to "温哥华白浪",
        "Real Salt Lake" to "皇家盐湖城",
        "Colorado Rapids" to "科罗拉多急流",
        "Houston Dynamo" to "休斯顿迪纳摩",
        "San Jose Earthquakes" to "圣何塞地震",
        "Portland Timbers" to "波特兰伐木者",
        "Sporting Kansas City" to "堪萨斯城竞技",
        "Austin FC" to "奥斯汀FC",
        "Charlotte FC" to "夏洛特FC",
        "Nashville SC" to "纳什维尔SC",
        "FC Dallas" to "达拉斯FC",
        "San Diego FC" to "圣地亚哥FC",
        "St. Louis City SC" to "圣路易斯城",
        "Toronto FC" to "多伦多FC",
        "CF Montreal" to "蒙特利尔CF",
        "DC United" to "华盛顿联",

        // National teams
        "France" to "法国国家队", "Argentina" to "阿根廷国家队", "Spain" to "西班牙国家队",
        "England" to "英格兰国家队", "Germany" to "德国国家队", "Brazil" to "巴西国家队",
        "Portugal" to "葡萄牙国家队", "Netherlands" to "荷兰国家队", "Italy" to "意大利国家队",
        "Belgium" to "比利时国家队", "Croatia" to "克罗地亚国家队", "Uruguay" to "乌拉圭国家队",
        "Colombia" to "哥伦比亚国家队", "Morocco" to "摩洛哥国家队", "Senegal" to "塞内加尔国家队",
        "Switzerland" to "瑞士国家队", "Austria" to "奥地利国家队", "Denmark" to "丹麦国家队",
        "Norway" to "挪威国家队", "Sweden" to "瑞典国家队", "Poland" to "波兰国家队",
        "Turkey" to "土耳其国家队", "Türkiye" to "土耳其国家队", "Ukraine" to "乌克兰国家队",
        "Serbia" to "塞尔维亚国家队", "Scotland" to "苏格兰国家队", "Wales" to "威尔士国家队",
        "United States" to "美国国家队", "USA" to "美国国家队", "Mexico" to "墨西哥国家队",
        "Canada" to "加拿大国家队", "China PR" to "中国国家队", "China" to "中国国家队",
        "Japan" to "日本国家队", "South Korea" to "韩国国家队", "Korea Republic" to "韩国国家队",
        "Australia" to "澳大利亚国家队", "Iran" to "伊朗国家队", "Saudi Arabia" to "沙特阿拉伯国家队",
        "Qatar" to "卡塔尔国家队", "Iraq" to "伊拉克国家队", "Jordan" to "约旦国家队",
        "Uzbekistan" to "乌兹别克斯坦国家队", "Thailand" to "泰国国家队", "Vietnam" to "越南国家队",
        "Indonesia" to "印度尼西亚国家队", "Egypt" to "埃及国家队", "Nigeria" to "尼日利亚国家队",
        "Algeria" to "阿尔及利亚国家队", "Tunisia" to "突尼斯国家队", "Cameroon" to "喀麦隆国家队",
        "Ghana" to "加纳国家队", "Ivory Coast" to "科特迪瓦国家队", "Cote D'Ivoire" to "科特迪瓦国家队",
        "Chile" to "智利国家队", "Ecuador" to "厄瓜多尔国家队", "Paraguay" to "巴拉圭国家队",
        "Greece" to "希腊国家队", "Czechia" to "捷克国家队", "Czech Republic" to "捷克国家队",
        "Hungary" to "匈牙利国家队", "Romania" to "罗马尼亚国家队", "Slovakia" to "斯洛伐克国家队",
        "Slovenia" to "斯洛文尼亚国家队", "New Zealand" to "新西兰国家队", "Panama" to "巴拿马国家队",
    )

    private val playerZhMap = mapOf(
        "Kylian Mbappé" to "姆巴佩", "Erling Haaland" to "哈兰德", "Lamine Yamal" to "亚马尔",
        "Jude Bellingham" to "贝林厄姆", "Vinícius Júnior" to "维尼修斯", "Vinicius Junior" to "维尼修斯",
        "Mohamed Salah" to "萨拉赫", "Bukayo Saka" to "萨卡", "Harry Kane" to "哈里·凯恩",
        "Jamal Musiala" to "穆西亚拉", "Lautaro Martínez" to "劳塔罗·马丁内斯", "Cole Palmer" to "帕尔默",
        "Pedri" to "佩德里", "Ousmane Dembélé" to "登贝莱", "Lionel Messi" to "梅西",
        "Cristiano Ronaldo" to "C罗", "Son Heung-min" to "孙兴慜", "Heung-Min Son" to "孙兴慜",
        "Rodri" to "罗德里", "Florian Wirtz" to "维尔茨", "Martin Ødegaard" to "厄德高",
        "Declan Rice" to "赖斯", "William Saliba" to "萨利巴", "Virgil van Dijk" to "范戴克",
        "Trent Alexander-Arnold" to "阿诺德", "Alexis Mac Allister" to "麦卡利斯特",
        "Phil Foden" to "福登", "Kevin De Bruyne" to "德布劳内", "Bernardo Silva" to "B席",
        "Bruno Fernandes" to "B费", "Alexander Isak" to "伊萨克", "Viktor Gyökeres" to "哲凯赖什",
        "Robert Lewandowski" to "莱万多夫斯基", "Raphinha" to "拉菲尼亚", "Federico Valverde" to "巴尔韦德",
        "Thibaut Courtois" to "库尔图瓦", "Antoine Griezmann" to "格列兹曼",
        "Julián Álvarez" to "阿尔瓦雷斯", "Julian Alvarez" to "阿尔瓦雷斯",
        "Serhou Guirassy" to "吉拉西", "Omar Marmoush" to "马尔穆什", "Victor Osimhen" to "奥斯梅恩",
        "Rafael Leão" to "莱奥", "Christian Pulisic" to "普利希奇", "Dušan Vlahović" to "弗拉霍维奇",
        "Kaoru Mitoma" to "三笘薰", "Takefusa Kubo" to "久保建英", "Wataru Endo" to "远藤航",
        "Min-jae Kim" to "金玟哉", "Lee Kang-in" to "李刚仁", "Wu Lei" to "武磊",
        "Timo Werner" to "维尔纳", "Ronaldo Vieira" to "罗纳尔多·维埃拉",
    )

    private val tokenZhMap = mapOf(
        "silva" to "席尔瓦", "santos" to "桑托斯", "oliveira" to "奥利维拉", "souza" to "索萨",
        "pereira" to "佩雷拉", "costa" to "科斯塔", "rodriguez" to "罗德里格斯", "rodrigues" to "罗德里格斯",
        "martinez" to "马丁内斯", "garcia" to "加西亚", "fernandez" to "费尔南德斯", "fernandes" to "费尔南德斯",
        "lopez" to "洛佩斯", "gonzalez" to "冈萨雷斯", "hernandez" to "埃尔南德斯", "perez" to "佩雷斯",
        "sanchez" to "桑切斯", "torres" to "托雷斯", "gomez" to "戈麦斯", "gomes" to "戈麦斯",
        "diaz" to "迪亚斯", "dias" to "迪亚斯", "alvarez" to "阿尔瓦雷斯", "alves" to "阿尔维斯",
        "romero" to "罗梅罗", "suarez" to "苏亚雷斯", "ruiz" to "鲁伊斯",
        "muller" to "穆勒", "schmidt" to "施密特", "schneider" to "施耐德", "fischer" to "菲舍尔",
        "weber" to "韦伯", "meyer" to "迈尔", "wagner" to "瓦格纳", "becker" to "贝克尔",
        "martin" to "马丁", "thomas" to "托马斯", "rossi" to "罗西", "bruno" to "布鲁诺",
        "smith" to "史密斯", "jones" to "琼斯", "williams" to "威廉姆斯", "brown" to "布朗",
        "taylor" to "泰勒", "davies" to "戴维斯", "evans" to "埃文斯", "wilson" to "威尔逊",
        "johnson" to "约翰逊", "walker" to "沃克", "white" to "怀特", "green" to "格林",
        "lewis" to "刘易斯", "clarke" to "克拉克", "jackson" to "杰克逊", "james" to "詹姆斯",
        "david" to "戴维", "lucas" to "卢卡斯", "mateo" to "马特奥", "gabriel" to "加布里埃尔",
        "rafael" to "拉斐尔", "daniel" to "丹尼尔", "carlos" to "卡洛斯", "luis" to "路易斯",
        "miguel" to "米格尔", "jose" to "何塞", "juan" to "胡安", "pedro" to "佩德罗",
        "pablo" to "巴勃罗", "diego" to "迭戈", "sergio" to "塞尔吉奥", "marco" to "马尔科",
        "matteo" to "马特奥", "luca" to "卢卡", "luka" to "卢卡", "ivan" to "伊万",
        "alex" to "亚历克斯", "alexander" to "亚历山大", "oliver" to "奥利弗", "jack" to "杰克",
        "harry" to "哈里", "george" to "乔治", "max" to "马克斯", "felix" to "菲利克斯",
        "leon" to "莱昂", "julian" to "尤利安", "paul" to "保罗", "kevin" to "凯文",
        "florian" to "弗洛里安", "sebastian" to "塞巴斯蒂安", "christian" to "克里斯蒂安",
        "kim" to "金", "park" to "朴", "lee" to "李", "son" to "孙", "jung" to "郑",
        "cho" to "曹", "choi" to "崔", "yoon" to "尹", "han" to "韩",
        "tanaka" to "田中", "sato" to "佐藤", "suzuki" to "铃木", "takahashi" to "高桥",
        "watanabe" to "渡边", "ito" to "伊藤", "yamamoto" to "山本", "nakamura" to "中村",
        "kobayashi" to "小林", "kato" to "加藤", "yoshida" to "吉田", "yamada" to "山田",
        "sasaki" to "佐佐木", "yamaguchi" to "山口", "inoue" to "井上", "kimura" to "木村",
        "shimizu" to "清水", "mori" to "森", "abe" to "阿部", "ikeda" to "池田",
        "mohamed" to "穆罕默德", "mohammed" to "穆罕默德", "ahmed" to "艾哈迈德", "ali" to "阿里",
        "hassan" to "哈桑", "ibrahim" to "易卜拉欣", "omar" to "奥马尔", "youssef" to "优素福",
        "al" to "阿尔", "diallo" to "迪亚洛", "traore" to "特拉奥雷", "kone" to "科内",
        "camara" to "卡马拉", "keita" to "凯塔", "mendy" to "门迪", "sarr" to "萨尔",
        "da" to "达", "de" to "德", "van" to "范", "von" to "冯", "jr" to "小",
    )

    private val syllableZh = mapOf(
        "ba" to "巴", "be" to "贝", "bi" to "比", "bo" to "博", "bu" to "布", "by" to "比",
        "ca" to "卡", "ce" to "塞", "ci" to "奇", "co" to "科", "cu" to "库", "cy" to "西",
        "cha" to "查", "che" to "切", "chi" to "奇", "cho" to "乔", "chu" to "丘",
        "da" to "达", "de" to "德", "di" to "迪", "do" to "多", "du" to "杜", "dy" to "迪",
        "fa" to "法", "fe" to "费", "fi" to "菲", "fo" to "福", "fu" to "富", "fy" to "菲",
        "ga" to "加", "ge" to "格", "gi" to "吉", "go" to "戈", "gu" to "古", "gy" to "吉",
        "ha" to "哈", "he" to "赫", "hi" to "希", "ho" to "霍", "hu" to "胡", "hy" to "希",
        "ja" to "亚", "je" to "耶", "ji" to "吉", "jo" to "约", "ju" to "尤",
        "ka" to "卡", "ke" to "克", "ki" to "基", "ko" to "科", "ku" to "库", "ky" to "基",
        "la" to "拉", "le" to "莱", "li" to "利", "lo" to "洛", "lu" to "卢", "ly" to "利",
        "ma" to "马", "me" to "梅", "mi" to "米", "mo" to "莫", "mu" to "穆", "my" to "米",
        "na" to "纳", "ne" to "内", "ni" to "尼", "no" to "诺", "nu" to "努", "ny" to "尼",
        "pa" to "帕", "pe" to "佩", "pi" to "皮", "po" to "波", "pu" to "普", "py" to "皮",
        "qa" to "卡", "qi" to "齐", "qu" to "库",
        "ra" to "拉", "re" to "雷", "ri" to "里", "ro" to "罗", "ru" to "鲁", "ry" to "里",
        "sa" to "萨", "se" to "塞", "si" to "西", "so" to "索", "su" to "苏", "sy" to "西",
        "sha" to "沙", "she" to "谢", "shi" to "希", "sho" to "肖", "shu" to "舒",
        "ta" to "塔", "te" to "特", "ti" to "蒂", "to" to "托", "tu" to "图", "ty" to "蒂",
        "tha" to "萨", "the" to "瑟", "thi" to "蒂", "tho" to "托",
        "va" to "瓦", "ve" to "维", "vi" to "维", "vo" to "沃", "vu" to "武",
        "wa" to "瓦", "we" to "韦", "wi" to "威", "wo" to "沃", "wu" to "伍",
        "xa" to "沙", "xi" to "西", "xu" to "舒",
        "ya" to "亚", "ye" to "耶", "yi" to "伊", "yo" to "约", "yu" to "尤",
        "za" to "扎", "ze" to "泽", "zi" to "齐", "zo" to "佐", "zu" to "祖",
    )

    private val codaZh = mapOf(
        "b" to "布", "c" to "克", "d" to "德", "f" to "夫", "g" to "格", "h" to "", "j" to "奇",
        "k" to "克", "l" to "尔", "m" to "姆", "n" to "恩", "p" to "普", "q" to "克",
        "r" to "尔", "s" to "斯", "t" to "特", "v" to "夫", "w" to "夫", "x" to "克斯", "z" to "茨",
        "ch" to "奇", "sh" to "什", "th" to "斯", "ck" to "克", "nd" to "德", "nt" to "特", "st" to "斯特",
    )

    private val vowelZh = mapOf('a' to "阿", 'e' to "埃", 'i' to "伊", 'o' to "奥", 'u' to "乌", 'y' to "伊")

    private val positionZhMap = mapOf(
        "Goalkeeper" to "门将", "Defence" to "后卫", "Defender" to "后卫",
        "Centre-Back" to "中后卫", "Left-Back" to "左后卫", "Right-Back" to "右后卫",
        "Midfield" to "中场", "Midfielder" to "中场",
        "Defensive Midfield" to "后腰", "Central Midfield" to "中前卫",
        "Attacking Midfield" to "前腰", "Offence" to "前锋", "Forward" to "前锋",
        "Attacker" to "前锋", "Centre-Forward" to "中锋",
        "Left Winger" to "左边锋", "Right Winger" to "右边锋",
    )

    private val normalizedTeamZhMap: Map<String, String> = exactTeamZhMap
        .entries
        .associate { (k, v) -> normalizeKey(k) to v }

    private val normalizedPlayerZhMap: Map<String, String> = playerZhMap
        .entries
        .associate { (k, v) -> stripAccents(k).lowercase().trim() to v }

    fun containsCjk(text: String): Boolean = text.any { it.code in 0x4E00..0x9FFF }

    private fun stripAccents(input: String): String {
        val norm = Normalizer.normalize(input, Normalizer.Form.NFD)
        return norm.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
    }

    private fun normalizeKey(raw: String): String {
        return stripAccents(raw)
            .lowercase()
            .replace("ø", "o")
            .replace("ł", "l")
            .replace("đ", "d")
            .replace("&", " ")
            .replace(".", " ")
            .replace("-", " ")
            .replace(Regex("\\b(fc|cf|afc|kv|bc|sc|sv|sk|fk|ssc|ac|as|us|rc|rcd|cd|ud|ca|ogc|vfl|vfb|tsg|fsv|bsc|gnk|sl|jk|ff|sd|ad|spvgg|04|05|07|96|98|1846|1848|1893|1899|1901|1907|1909|1910|1913|1945)\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun phoneticWord(word: String): String {
        val w = stripAccents(word).lowercase().replace(Regex("[^a-z]"), "")
        if (w.isEmpty()) return word
        if (w.length == 1) return w.uppercase()
        val sb = StringBuilder()
        var i = 0
        val vowels = setOf('a', 'e', 'i', 'o', 'u', 'y')
        while (i < w.length) {
            var matched = false
            for (cLen in listOf(2, 1)) {
                if (i + cLen < w.length && w[i + cLen] in vowels) {
                    val key = w.substring(i, i + cLen + 1)
                    val zh = syllableZh[key]
                    if (zh != null) {
                        sb.append(zh)
                        i += cLen + 1
                        while (i < w.length && w[i] in vowels && i > 0 && w[i] == w[i - 1]) i++
                        matched = true
                        break
                    }
                }
            }
            if (matched) continue
            if (w[i] in vowels) {
                sb.append(vowelZh[w[i]] ?: "")
                i++
                continue
            }
            if (i + 2 <= w.length) {
                val two = codaZh[w.substring(i, i + 2)]
                if (two != null) {
                    sb.append(two)
                    i += 2
                    continue
                }
            }
            sb.append(codaZh[w.substring(i, i + 1)] ?: "")
            i++
        }
        return sb.toString().ifEmpty { word }
    }

    fun transliterateToChinese(name: String, isClub: Boolean = false): String {
        val cleaned = name.trim()
        if (cleaned.isEmpty()) return ""
        if (containsCjk(cleaned)) return cleaned
        val rawTokens = cleaned.split(Regex("\\s+")).filter { it.isNotBlank() }
        val tokens = if (isClub) {
            rawTokens.filterNot { it.lowercase().trim('.') in setOf("fc", "sc", "cf", "afc", "united", "city") }
                .ifEmpty { rawTokens }
        } else {
            rawTokens
        }
        val mapped = tokens.mapNotNull { tok ->
            val cleanTok = stripAccents(tok).trim('.', ',')
            if (cleanTok.isEmpty()) return@mapNotNull null
            if (cleanTok.length == 1 && '.' in tok) return@mapNotNull cleanTok.uppercase()
            val low = cleanTok.lowercase()
            if ('-' in low) {
                low.split('-').filter { it.isNotBlank() }
                    .joinToString("·") { tokenZhMap[it] ?: phoneticWord(it) }
            } else {
                tokenZhMap[low] ?: phoneticWord(low)
            }
        }
        if (mapped.isEmpty()) return cleaned
        return if (isClub) mapped.joinToString("") else mapped.joinToString("·")
    }

    fun localizeTeam(fullName: String, shortName: String? = null): String {
        if (containsCjk(fullName)) return fullName
        ZhEntityDictionary.findTeam(fullName)?.let { return it.zhName }
        if (!shortName.isNullOrBlank()) {
            ZhEntityDictionary.findTeam(shortName)?.let { return it.zhName }
        }
        val youthMatch = Regex("^(.*?)\\s+(U17|U19|U20|U21|U23)(?:\\s+.*)?$", RegexOption.IGNORE_CASE)
            .find(fullName.trim())
        if (youthMatch != null) {
            val base = youthMatch.groupValues[1].trim()
            val age = youthMatch.groupValues[2].uppercase()
            val baseZh = localizeTeam(base).removeSuffix("国家队")
            return "$baseZh$age"
        }
        exactTeamZhMap[fullName]?.let { return it }
        if (!shortName.isNullOrBlank()) {
            exactTeamZhMap[shortName]?.let { return it }
        }
        normalizedTeamZhMap[normalizeKey(fullName)]?.let { return it }
        if (!shortName.isNullOrBlank()) {
            normalizedTeamZhMap[normalizeKey(shortName)]?.let { return it }
        }
        return transliterateToChinese(shortName?.takeIf { it.isNotBlank() } ?: fullName, isClub = true)
    }

    fun localizePlayer(rawName: String): Pair<String, Boolean> {
        val cleaned = rawName.trim()
        if (cleaned.isEmpty()) return "" to false
        if (containsCjk(cleaned)) return cleaned to false
        ZhEntityDictionary.findPlayer(cleaned)?.let { return it.zhName to false }
        playerZhMap[cleaned]?.let { return it to false }
        normalizedPlayerZhMap[stripAccents(cleaned).lowercase()]?.let { return it to false }
        return transliterateToChinese(cleaned, isClub = false) to true
    }

    fun localizeCoach(rawName: String): String {
        val cleaned = rawName.trim()
        if (cleaned.isEmpty()) return ""
        if (containsCjk(cleaned)) return cleaned
        ZhEntityDictionary.findCoach(cleaned)?.let { return it.zhName }
        return localizePlayer(cleaned).first
    }

    fun preferChineseCoach(rawName: String, existingZh: String?): String {
        val cleanedEn = rawName.trim()
        if (cleanedEn.isNotEmpty()) {
            ZhEntityDictionary.findCoach(cleanedEn)?.let { return it.zhName }
            playerZhMap[cleanedEn]?.let { return it }
            normalizedPlayerZhMap[stripAccents(cleanedEn).lowercase()]?.let { return it }
        }
        if (!existingZh.isNullOrBlank() && containsCjk(existingZh)) {
            return existingZh.trim()
        }
        return localizeCoach(cleanedEn)
    }

    fun shortPlayerDisplayName(rawName: String, zhName: String): String {
        ZhEntityDictionary.findPlayer(rawName)?.let { entry ->
            if (entry.shortZh.isNotBlank()) return entry.shortZh
        }
        val cleanZh = zhName.trim()
        if (cleanZh.isEmpty()) return rawName.trim().substringAfterLast(' ').take(8)
        val surname = cleanZh.substringAfter("·").ifBlank { cleanZh }
        return if (surname.length > 5) surname.take(5) else surname
    }

    fun localizePosition(pos: String?): String =
        if (pos.isNullOrBlank()) "球员" else positionZhMap[pos.trim()] ?: pos.trim()

    fun preferChinese(rawName: String, existingZh: String?): String {
        ZhEntityDictionary.findTeam(rawName)?.let { return it.zhName }
        if (!existingZh.isNullOrBlank() && containsCjk(existingZh)) {
            val fromDict = exactTeamZhMap[rawName] ?: normalizedTeamZhMap[normalizeKey(rawName)]
            return fromDict ?: existingZh
        }
        return localizeTeam(rawName, existingZh)
    }

    fun localizeCompetition(code: String, fallbackName: String): String =
        CompetitionCatalog.zhNameOrFallback(code, fallbackName)

    fun determineStandingZone(code: String, rank: Int, totalRows: Int): Pair<String, String> {
        return when (code.uppercase()) {
            "PL" -> when {
                rank <= 4 -> "UCL" to "欧冠正赛区"
                rank == 5 -> "UEL" to "欧联杯资格区"
                totalRows >= 18 && rank >= totalRows - 2 -> "RELEGATION" to "降级区"
                else -> "NONE" to ""
            }
            "PD" -> when {
                rank <= 4 -> "UCL" to "欧冠正赛区"
                rank in 5..6 -> "UEL" to "欧联/欧协联区"
                totalRows >= 18 && rank >= totalRows - 2 -> "RELEGATION" to "降级区"
                else -> "NONE" to ""
            }
            "BL1" -> when {
                rank <= 4 -> "UCL" to "欧冠正赛区"
                rank == 5 -> "UEL" to "欧联杯区"
                rank == 6 -> "UECL" to "欧协联附加赛区"
                totalRows >= 18 && rank == totalRows - 2 -> "RELEGATION_PLAYOFF" to "升降级附加赛区"
                totalRows >= 18 && rank >= totalRows - 1 -> "RELEGATION" to "降级区"
                else -> "NONE" to ""
            }
            "SA" -> when {
                rank <= 4 -> "UCL" to "欧冠正赛区"
                rank == 5 -> "UEL" to "欧联杯区"
                rank == 6 -> "UECL" to "欧协联资格区"
                totalRows >= 18 && rank >= totalRows - 2 -> "RELEGATION" to "降级区"
                else -> "NONE" to ""
            }
            "FL1" -> when {
                rank <= 3 -> "UCL" to "欧冠正赛区"
                rank == 4 -> "UCL_QUAL" to "欧冠资格赛区"
                rank == 5 -> "UEL" to "欧联杯区"
                rank == 6 -> "UECL" to "欧协联附加赛区"
                totalRows >= 18 && rank == totalRows - 2 -> "RELEGATION_PLAYOFF" to "升降级附加赛区"
                totalRows >= 18 && rank >= totalRows - 1 -> "RELEGATION" to "降级区"
                else -> "NONE" to ""
            }
            "CL", "UEL", "UECL" -> when {
                rank <= 8 -> "UCL" to "16强直通区"
                rank <= 24 -> "PLAYOFF" to "淘汰赛附加赛区"
                else -> "RELEGATION" to "出局区"
            }
            "ACL" -> when {
                rank <= 8 -> "UCL" to "淘汰赛晋级区"
                else -> "RELEGATION" to "出局区"
            }
            "J1" -> when {
                rank <= 2 -> "UCL" to "亚冠精英赛区"
                rank == 3 -> "UEL" to "亚冠二级资格区"
                totalRows >= 18 && rank >= totalRows - 2 -> "RELEGATION" to "降级区"
                else -> "NONE" to ""
            }
            "AL" -> when {
                rank <= 6 -> "PLAYOFF" to "季后赛区"
                else -> "NONE" to ""
            }
            "MLS" -> when {
                rank <= 7 -> "UCL" to "季后赛直通区"
                rank in 8..9 -> "PLAYOFF" to "外卡附加赛区"
                else -> "NONE" to ""
            }
            "ELC" -> when {
                rank <= 2 -> "PROMOTION" to "英超直通升级区"
                rank <= 6 -> "PLAYOFF" to "升级附加赛区"
                totalRows >= 20 && rank >= totalRows - 2 -> "RELEGATION" to "英甲降级区"
                else -> "NONE" to ""
            }
            "DED", "PPL" -> when {
                rank <= 2 -> "UCL" to "欧冠正赛/资格区"
                rank == 3 -> "UEL" to "欧联资格区"
                rank == 4 -> "UECL" to "欧协联资格区"
                totalRows >= 16 && rank == totalRows - 2 -> "RELEGATION_PLAYOFF" to "保级附加赛区"
                totalRows >= 16 && rank >= totalRows - 1 -> "RELEGATION" to "降级区"
                else -> "NONE" to ""
            }
            "BSA" -> when {
                rank <= 4 -> "UCL" to "解放者杯正赛区"
                rank <= 6 -> "UCL_QUAL" to "解放者杯资格区"
                rank <= 12 -> "UECL" to "南美杯区"
                totalRows >= 18 && rank >= totalRows - 3 -> "RELEGATION" to "降级区"
                else -> "NONE" to ""
            }
            else -> "NONE" to ""
        }
    }

    fun localizeEspnGroup(name: String?): String {
        val raw = name?.trim().orEmpty()
        if (raw.isEmpty()) return ""
        val low = raw.lowercase()
        if (low in setOf("regular season", "league phase", "overall")) return ""
        return when {
            low == "eastern conference" -> "东部联盟"
            low == "western conference" -> "西部联盟"
            low == "east region" -> "东亚区"
            low == "west region" -> "西亚区"
            low == "east" -> "东区"
            low == "west" -> "西区"
            low.startsWith("group ") -> "小组 ${raw.substring(6)}"
            else -> raw
        }
    }

    fun localizeEspnZone(desc: String?, code: String, rank: Int, totalRows: Int): Pair<String, String> {
        val d = desc.orEmpty().lowercase()
        return when {
            "relegation playoff" in d || "relegation play-off" in d -> "RELEGATION_PLAYOFF" to "升降级附加赛区"
            "relegation" in d || "relegated" in d -> "RELEGATION" to "降级区"
            "champions league" in d && ("qualif" in d || "playoff" in d) -> "UCL_QUAL" to "欧冠资格赛区"
            "champions league" in d -> "UCL" to "欧冠正赛区"
            "europa league" in d -> "UEL" to "欧联杯区"
            "conference league" in d -> "UECL" to "欧协联区"
            "round of 16" in d || "next round" in d || "knockout stage" in d -> "UCL" to "16强直通区"
            "knockout" in d || "playoff" in d || "play-off" in d || "finals series" in d || "final series" in d -> "PLAYOFF" to "季后/淘汰附加赛区"
            "promotion playoff" in d -> "PLAYOFF" to "升级附加赛区"
            "promotion" in d || "promoted" in d -> "PROMOTION" to "升级区"
            else -> determineStandingZone(code, rank, totalRows)
        }
    }
}
