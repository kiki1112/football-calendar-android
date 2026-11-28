#!/usr/bin/env python3
"""Matches official Sporttery fixtures onto unified international schedule within +-15m window."""

from __future__ import annotations

import datetime as dt
import json
import pathlib
import re
import sys
import unicodedata
from dataclasses import dataclass
from typing import Any

CLOUD_SYNC_DIR = pathlib.Path(__file__).resolve().parents[1]
REPO_ROOT = CLOUD_SYNC_DIR.parent
FEED_DIR = REPO_ROOT / "feed"
if str(FEED_DIR) not in sys.path:
    sys.path.insert(0, str(FEED_DIR))

from localization import TEAM_ZH_MAP, localize_team  # noqa: E402
from providers.sporttery import SportteryFixture  # noqa: E402

FIFTEEN_MINUTES_MS = 15 * 60 * 1000

# Common Sporttery short names / aliases mapped to canonical Chinese club/national names
SPORTTERY_ALIAS_TO_ZH: dict[str, str] = {
    "皇马": "皇家马德里",
    "巴萨": "巴塞罗那",
    "马竞": "马德里竞技",
    "毕尔巴鄂": "毕尔巴鄂竞技",
    "毕巴": "毕尔巴鄂竞技",
    "皇社": "皇家社会",
    "贝蒂斯": "皇家贝蒂斯",
    "黄潜": "比利亚雷亚尔",
    "黄潜艇": "比利亚雷亚尔",
    "西班牙人": "西班牙人",
    "塞尔塔": "塞尔塔",
    "塞维利亚": "塞维利亚",
    "阿森纳": "阿森纳",
    "曼城": "曼城",
    "利物浦": "利物浦",
    "切尔西": "切尔西",
    "曼联": "曼联",
    "热刺": "热刺",
    "纽卡": "纽卡斯尔联",
    "纽卡斯尔": "纽卡斯尔联",
    "维拉": "阿斯顿维拉",
    "布莱顿": "布莱顿",
    "伯恩茅斯": "伯恩茅斯",
    "布伦特": "布伦特福德",
    "富勒姆": "富勒姆",
    "埃弗顿": "埃弗顿",
    "西汉姆": "西汉姆联",
    "水晶宫": "水晶宫",
    "狼队": "狼队",
    "诺丁汉": "诺丁汉森林",
    "森林": "诺丁汉森林",
    "拜仁": "拜仁慕尼黑",
    "多特": "多特蒙德",
    "药厂": "勒沃库森",
    "莱比锡": "RB莱比锡",
    "斯图加特": "斯图加特",
    "法兰克福": "法兰克福",
    "国米": "国际米兰",
    "国际米兰": "国际米兰",
    "AC米兰": "AC米兰",
    "米兰": "AC米兰",
    "尤文": "尤文图斯",
    "那不勒斯": "那不勒斯",
    "罗马": "罗马",
    "亚特兰大": "亚特兰大",
    "拉齐奥": "拉齐奥",
    "佛罗伦萨": "佛罗伦萨",
    "巴黎": "巴黎圣日耳曼",
    "大巴黎": "巴黎圣日耳曼",
    "圣日耳曼": "巴黎圣日耳曼",
    "马赛": "马赛",
    "摩纳哥": "摩纳哥",
    "里昂": "里昂",
    "里尔": "里尔",
    "利尔莱": "里尔",
    "埃因霍温": "埃因霍温",
    "阿贾克斯": "阿贾克斯",
    "费耶诺德": "费耶诺德",
    "本菲卡": "本菲卡",
    "波尔图": "波尔图",
    "葡体": "葡萄牙体育",
    "里斯本": "葡萄牙体育",
    "神户": "神户胜利船",
    "广岛": "广岛三箭",
    "川崎": "川崎前锋",
    "横滨": "横滨水手",
    "横滨水手": "横滨水手",
    "浦和": "浦和红钻",
    "鹿岛": "鹿岛鹿角",
    "町田": "町田泽维亚",
    "东京": "FC东京",
    "蔚山": "蔚山HD",
    "蔚山现代": "蔚山HD",
    "全北": "全北现代",
    "浦项": "浦项铁人",
    "首尔": "首尔FC",
    "首尔衣恋": "首尔衣恋",
    "横滨FC": "横滨FC",
    "湘南": "湘南丽海",
    "甲府": "甲府风林",
    "澳超": "澳超",
    "墨尔本胜利": "墨尔本胜利",
    "悉尼FC": "悉尼FC",
    "奥克兰FC": "奥克兰FC",
}

LEAGUE_ZH_TO_CODE: dict[str, str] = {
    "英超": "PL",
    "英冠": "ELC",
    "西甲": "PD",
    "德甲": "BL1",
    "意甲": "SA",
    "法甲": "FL1",
    "欧冠": "CL",
    "欧罗巴": "UEL",
    "欧联": "UEL",
    "欧联杯": "UEL",
    "欧协联": "UECL",
    "荷甲": "DED",
    "葡超": "PPL",
    "日职": "J1",
    "日职联": "J1",
    "日乙": "J2",
    "日职乙": "J2",
    "韩K联": "KL1",
    "韩职": "KL1",
    "澳超": "AL",
    "美职": "MLS",
    "美职足": "MLS",
    "解放者杯": "CLI",
    "亚冠精英": "ACL",
    "亚冠": "ACL",
    "巴甲": "BSA",
    "世界杯": "WC",
    "欧洲杯": "EC",
    "欧国联": "UNL",
}


def _strip_accents(raw: str) -> str:
    decomposed = unicodedata.normalize("NFD", raw)
    return "".join(ch for ch in decomposed if unicodedata.category(ch) != "Mn")


def _normalize_match_token(raw: str) -> str:
    cleaned = _strip_accents(str(raw or "")).strip().lower()
    cleaned = re.sub(r"[\s·•.\-_&/]+", "", cleaned)
    cleaned = re.sub(r"(fc|cf|afc|sc|sv|sk|fk|ssc|ac|as|us|rc|rcd|vfb|vfl)$", "", cleaned)
    return cleaned


def _parse_utc_ms(raw_utc: str) -> int | None:
    s = str(raw_utc or "").strip()
    if not s:
        return None
    if s.endswith("Z"):
        s = s[:-1] + "+00:00"
    try:
        parsed = dt.datetime.fromisoformat(s)
        if parsed.tzinfo is None:
            parsed = parsed.replace(tzinfo=dt.timezone.utc)
        return int(parsed.astimezone(dt.timezone.utc).timestamp() * 1000)
    except ValueError:
        return None


@dataclass
class MatchStats:
    matched_count: int = 0
    standalone_count: int = 0


class MatchEntityMatcher:
    """Matches Sporttery fixtures to normalized matches using zh_entities.json + +-15m window."""

    def __init__(self, zh_entities_path: pathlib.Path | None = None) -> None:
        self.alias_to_canonical_zh: dict[str, str] = {}
        self._load_dictionary(zh_entities_path)

    def _load_dictionary(self, zh_entities_path: pathlib.Path | None) -> None:
        # 1. Seed from TEAM_ZH_MAP & Sporttery aliases
        for en_name, zh_name in TEAM_ZH_MAP.items():
            self._register_alias(en_name, zh_name)
            self._register_alias(zh_name, zh_name)
        for alias, canonical_zh in SPORTTERY_ALIAS_TO_ZH.items():
            self._register_alias(alias, canonical_zh)

        # 2. Load authoritative zh_entities.json
        candidates = [
            zh_entities_path,
            CLOUD_SYNC_DIR / "dictionary" / "zh_entities.json",
            REPO_ROOT / "app" / "src" / "main" / "assets" / "zh_entities.json",
        ]
        for path in candidates:
            if path and path.exists():
                payload = json.loads(path.read_text(encoding="utf-8"))
                for en_key, entry in (payload.get("teams") or {}).items():
                    if not isinstance(entry, dict):
                        continue
                    zh_name = str(entry.get("zhName") or "").strip()
                    short_zh = str(entry.get("shortZh") or "").strip()
                    if not zh_name:
                        continue
                    self._register_alias(en_key, zh_name)
                    self._register_alias(zh_name, zh_name)
                    if short_zh:
                        self._register_alias(short_zh, zh_name)
                    for alt in entry.get("aliases") or []:
                        self._register_alias(str(alt), zh_name)
                break

    def _register_alias(self, raw_alias: str, canonical_zh: str) -> None:
        token = _normalize_match_token(raw_alias)
        if token:
            self.alias_to_canonical_zh[token] = canonical_zh.strip()

    def canonical_team_key(self, raw_name: str, existing_zh: str = "") -> str:
        for cand in (existing_zh, raw_name):
            token = _normalize_match_token(cand)
            if token and token in self.alias_to_canonical_zh:
                return _normalize_match_token(self.alias_to_canonical_zh[token])
        localized = localize_team(raw_name) if raw_name else ""
        for cand in (localized, existing_zh, raw_name):
            token = _normalize_match_token(cand)
            if token:
                return self.alias_to_canonical_zh.get(token, token)
        return ""

    def infer_competition_code(self, league_name: str) -> str:
        clean = str(league_name or "").strip()
        for prefix, code in LEAGUE_ZH_TO_CODE.items():
            if prefix in clean or clean == prefix:
                return code
        return "OTHER"

    def apply_sporttery_fixtures(
        self,
        matches: list[dict[str, Any]],
        sporttery_fixtures: list[SportteryFixture],
        *,
        kickoff_window_ms: int = FIFTEEN_MINUTES_MS,
    ) -> tuple[list[dict[str, Any]], MatchStats]:
        """Annotate matching schedule items and preserve unmatched Sporttery items cleanly."""
        if not sporttery_fixtures:
            return (matches, MatchStats())

        stats = MatchStats()
        # Index candidate matches by (home_key, away_key)
        index: dict[tuple[str, str], list[dict[str, Any]]] = {}
        for m in matches:
            hk = self.canonical_team_key(
                str(m.get("homeTeamName") or ""),
                str(m.get("homeTeamZhName") or ""),
            )
            ak = self.canonical_team_key(
                str(m.get("awayTeamName") or ""),
                str(m.get("awayTeamZhName") or ""),
            )
            if hk and ak:
                index.setdefault((hk, ak), []).append(m)

        standalone: list[dict[str, Any]] = []
        seen_ids = {str(m.get("id") or "") for m in matches}

        for sf in sporttery_fixtures:
            s_home = self.canonical_team_key(sf.homeName, sf.homeName)
            s_away = self.canonical_team_key(sf.awayName, sf.awayName)
            matched_row: dict[str, Any] | None = None
            best_diff = kickoff_window_ms + 1

            for cand in index.get((s_home, s_away), []):
                ko_ms = _parse_utc_ms(str(cand.get("utcDate") or ""))
                if ko_ms is None:
                    continue
                diff = abs(ko_ms - sf.kickoffMillis)
                if diff <= kickoff_window_ms and diff < best_diff:
                    best_diff = diff
                    matched_row = cand

            if matched_row is not None:
                matched_row["lotteryCode"] = sf.lotteryCode
                matched_row["lotteryHandicap"] = sf.lotteryHandicap
                matched_row["lotteryDeadlineUtc"] = sf.lotteryDeadlineUtc
                stats.matched_count += 1
            else:
                # Preserve unmatched niche Sporttery matches as standalone rows
                comp_code = self.infer_competition_code(sf.leagueName)
                comp_zh = sf.leagueName or next(
                    (name for name, c in LEAGUE_ZH_TO_CODE.items() if c == comp_code),
                    "竞彩焦点",
                )
                sid = f"st-{sf.sportteryId or sf.lotteryCode}"
                if sid in seen_ids:
                    continue
                seen_ids.add(sid)
                home_zh = SPORTTERY_ALIAS_TO_ZH.get(sf.homeName, sf.homeName)
                away_zh = SPORTTERY_ALIAS_TO_ZH.get(sf.awayName, sf.awayName)
                standalone.append(
                    {
                        "id": sid,
                        "competitionCode": comp_code,
                        "competitionName": sf.leagueName or comp_code,
                        "competitionZhName": comp_zh,
                        "matchday": None,
                        "stage": "REGULAR_SEASON",
                        "utcDate": sf.kickoffUtc,
                        "status": "TIMED",
                        "homeTeamId": 0,
                        "homeTeamName": sf.homeName,
                        "homeTeamShortName": home_zh,
                        "homeTeamZhName": home_zh,
                        "homeTeamCrest": "",
                        "awayTeamId": 0,
                        "awayTeamName": sf.awayName,
                        "awayTeamShortName": away_zh,
                        "awayTeamZhName": away_zh,
                        "awayTeamCrest": "",
                        "homeScore": None,
                        "awayScore": None,
                        "venue": "",
                        "lastUpdatedUtc": sf.kickoffUtc,
                        "elapsedMinutes": None,
                        "apiFootballFixtureId": None,
                        "espnSlug": None,
                        "espnEventId": None,
                        "lotteryCode": sf.lotteryCode,
                        "lotteryHandicap": sf.lotteryHandicap,
                        "lotteryDeadlineUtc": sf.lotteryDeadlineUtc,
                        "isFinalized": False,
                    }
                )
                stats.standalone_count += 1

        combined = sorted(matches + standalone, key=lambda x: str(x.get("utcDate") or ""))
        return (combined, stats)
