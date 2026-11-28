#!/usr/bin/env python3
"""Data hygiene validators for cloud-sync feed outputs (coaches, lineups, finalization)."""

from __future__ import annotations

import datetime as dt
import pathlib
import re
import sys
import unicodedata
from typing import Any

CLOUD_SYNC_DIR = pathlib.Path(__file__).resolve().parents[1]
FEED_DIR = CLOUD_SYNC_DIR.parent / "feed"
if str(FEED_DIR) not in sys.path:
    sys.path.insert(0, str(FEED_DIR))

from localization import localize_coach, localize_team  # noqa: E402

FINAL_RAW_STATUSES = {"FINISHED", "FT", "AET", "PEN", "AWARDED", "CANCELED", "CANCELLED"}
VALID_NORMALIZED_STATUSES = {
    "SCHEDULED",
    "TIMED",
    "LIVE",
    "IN_PLAY",
    "PAUSED",
    "FINISHED",
    "POSTPONED",
    "SUSPENDED",
    "CANCELLED",
    "UNKNOWN",
}

STATUS_NORMALIZATION_MAP = {
    "FT": "FINISHED",
    "AET": "FINISHED",
    "PEN": "FINISHED",
    "AWARDED": "FINISHED",
    "1H": "LIVE",
    "2H": "LIVE",
    "ET": "LIVE",
    "BT": "PAUSED",
    "HT": "PAUSED",
    "HALFTIME": "PAUSED",
    "NS": "TIMED",
    "TBD": "SCHEDULED",
    "PST": "POSTPONED",
    "CANC": "CANCELLED",
    "SUSP": "SUSPENDED",
}

# Stale / expired club coaches known to leak from upstream metadata endpoints
STALE_HISTORICAL_CLUB_COACHES: list[dict[str, Any]] = [
    {
        "club_tokens": {"real madrid", "皇家马德里"},
        "stale_tokens": {"jose mourinho", "carlo ancelotti", "何塞·莫乌里恩霍"},
        "active_en": "Álvaro Arbeloa",
        "active_zh": "阿韦洛亚",
    },
]


def _norm(raw: str) -> str:
    s = unicodedata.normalize("NFD", str(raw or ""))
    s = "".join(ch for ch in s if unicodedata.category(ch) != "Mn").lower()
    s = re.sub(r"[^a-z0-9\u4e00-\u9fff]+", " ", s)
    s = re.sub(r"\b(fc|cf|afc|sc|sv|sk|fk|ssc|ac|as|us|rc|vfl|vfb)\b", " ", s)
    return re.sub(r"\s+", " ", s).strip()


def _dedupe_players(rows: list[dict[str, Any]], max_items: int | None = None) -> list[dict[str, Any]]:
    seen_keys: set[str] = set()
    cleaned: list[dict[str, Any]] = []
    for p in rows:
        if not isinstance(p, dict):
            continue
        pname = _norm(str(p.get("name") or p.get("zhName") or ""))
        pid = str(p.get("playerId") or p.get("id") or "").strip()
        num = str(p.get("number") or p.get("shirtNumber") or "")
        key = pid if pid and pid != "0" else f"{pname}|{num}"
        if not key or key == "|" or key in seen_keys:
            continue
        seen_keys.add(key)
        cleaned.append(p)
        if max_items is not None and len(cleaned) >= max_items:
            break
    return cleaned


def validate_lineups_and_coaches(
    detail: dict[str, Any],
    team_zh_index: dict[str, tuple[str, str]] | None = None,
) -> dict[str, Any]:
    """Deduplicate startXI/substitutes and normalize coach Chinese names."""
    lineups = detail.get("lineups")
    if not isinstance(lineups, list):
        return detail

    cleaned_sides: list[dict[str, Any]] = []
    for side in lineups:
        if not isinstance(side, dict):
            continue
        start_xi = _dedupe_players(list(side.get("startXI") or []), max_items=11)
        start_names = {_norm(str(p.get("name") or "")) for p in start_xi if p.get("name")}
        subs_raw = [
            p for p in _dedupe_players(list(side.get("substitutes") or []))
            if _norm(str(p.get("name") or "")) not in start_names
        ]
        coach_en = str(side.get("coachName") or "").strip()
        coach_zh = localize_coach(coach_en, str(side.get("coachZhName") or ""))
        team_en = str(side.get("teamName") or "").strip()
        raw_team_zh = str(side.get("teamZhName") or "").strip()
        team_zh = localize_team(team_en) if team_en else raw_team_zh

        if team_zh_index is not None and coach_en:
            tkey = _norm(team_en or raw_team_zh)
            if tkey:
                team_zh_index[tkey] = (coach_en, coach_zh)

        cleaned_sides.append(
            {
                **side,
                "teamZhName": team_zh or raw_team_zh,
                "coachName": coach_en,
                "coachZhName": coach_zh,
                "startXI": start_xi,
                "substitutes": subs_raw,
            }
        )
    detail["lineups"] = cleaned_sides
    return detail


def sanitize_team_coach(
    team: dict[str, Any],
    lineup_coach_by_team: dict[str, tuple[str, str]] | None = None,
) -> dict[str, Any]:
    """Reject known stale historical coaches and enforce zh_entities coach Chinese names."""
    t_en = str(team.get("name") or "").strip()
    t_zh = str(team.get("zhName") or "").strip()
    c_en = str(team.get("coachName") or "").strip()
    c_zh = str(team.get("coachZhName") or "").strip()

    # 1. Prefer fresh official lineup coach if indexed
    for cand in (_norm(t_en), _norm(t_zh)):
        if lineup_coach_by_team and cand in lineup_coach_by_team:
            lineup_en, lineup_zh = lineup_coach_by_team[cand]
            if lineup_en:
                c_en = lineup_en
                c_zh = lineup_zh
                break

    # 2. Check stale historical coach rules
    norm_team = _norm(t_en)
    norm_zh = t_zh.strip()
    norm_coach = _norm(c_en)
    for rule in STALE_HISTORICAL_CLUB_COACHES:
        if any(tok in norm_team or tok == norm_zh for tok in rule["club_tokens"]):
            if norm_coach in rule["stale_tokens"] or c_zh in rule["stale_tokens"]:
                c_en = rule["active_en"]
                c_zh = rule["active_zh"]
                break

    # 3. Always re-run localize_coach so phonetic strings like "米克尔·阿尔特塔" or "何塞·莫乌里恩霍" become canonical
    team["coachName"] = c_en
    team["coachZhName"] = localize_coach(c_en, c_zh)
    return team


def normalize_match_record(match: dict[str, Any], now_utc: dt.datetime | None = None) -> dict[str, Any]:
    """Normalize match status strings and stamp isFinalized for finished fixtures."""
    raw_status = str(match.get("status") or "TIMED").upper().strip()
    norm_status = STATUS_NORMALIZATION_MAP.get(raw_status, raw_status)
    if norm_status not in VALID_NORMALIZED_STATUSES:
        norm_status = "UNKNOWN"
    match["status"] = norm_status

    already_final = bool(match.get("isFinalized"))
    if already_final or norm_status == "FINISHED":
        match["isFinalized"] = True
    else:
        match["isFinalized"] = False
    return match


def validate_feed_payload(payload: dict[str, Any], now_utc: dt.datetime | None = None) -> dict[str, Any]:
    """Run all cloud validators in deterministic order before writing fixtures.json."""
    lineup_coach_index: dict[str, tuple[str, str]] = {}

    live_details = payload.get("liveDetails")
    if isinstance(live_details, dict):
        for mid, det in live_details.items():
            if isinstance(det, dict):
                live_details[mid] = validate_lineups_and_coaches(det, lineup_coach_index)

    matches = payload.get("matches")
    if isinstance(matches, list):
        payload["matches"] = [
            normalize_match_record(m, now_utc=now_utc) for m in matches if isinstance(m, dict)
        ]

    teams = payload.get("teams")
    if isinstance(teams, list):
        payload["teams"] = [
            sanitize_team_coach(t, lineup_coach_by_team=lineup_coach_index)
            for t in teams
            if isinstance(t, dict)
        ]

    return payload
