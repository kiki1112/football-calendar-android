#!/usr/bin/env python3
"""API-Football v3 provider with strict Free-tier (100 req/day) quota guard."""

from __future__ import annotations

import datetime as dt
import json
import urllib.request
from dataclasses import dataclass, field
from typing import Any

API_FOOTBALL_BASE = "https://v3.football.api-sports.io"
DAILY_FREE_LIMIT = 100
IN_PLAY_STATUSES = {"LIVE", "IN_PLAY", "PAUSED", "HALFTIME", "1H", "HT", "2H", "ET", "BT", "P"}
FINAL_STATUSES = {"FINISHED", "FT", "AET", "PEN", "AWARDED"}


def parse_utc_iso(raw: str) -> dt.datetime | None:
    if not raw:
        return None
    s = str(raw).strip()
    if s.endswith("Z"):
        s = s[:-1] + "+00:00"
    try:
        parsed = dt.datetime.fromisoformat(s)
        if parsed.tzinfo is None:
            parsed = parsed.replace(tzinfo=dt.timezone.utc)
        return parsed.astimezone(dt.timezone.utc)
    except ValueError:
        return None


@dataclass
class ApiFootballQuotaGuard:
    """Enforces Free 100 calls/day limit and prevents re-fetching finalized fixtures."""

    daily_limit: int = DAILY_FREE_LIMIT
    per_run_budget: int = 12
    calls_used_today: int = 0
    calls_used_this_run: int = 0
    skipped_finalized: int = 0
    skipped_out_of_window: int = 0
    history_log: list[str] = field(default_factory=list)

    @property
    def remaining_run_budget(self) -> int:
        daily_rem = max(0, self.daily_limit - self.calls_used_today)
        run_rem = max(0, self.per_run_budget - self.calls_used_this_run)
        return min(daily_rem, run_rem)

    def can_spend(self, calls: int = 1) -> bool:
        return self.remaining_run_budget >= calls

    def record_call(self, label: str = "") -> None:
        self.calls_used_this_run += 1
        self.calls_used_today += 1
        if label:
            self.history_log.append(label)

    def should_fetch_match_detail(
        self,
        match: dict[str, Any],
        now_utc: dt.datetime | None = None,
    ) -> bool:
        """Strictly skip finalized fixtures and only allow in-play or当日焦点 matches."""
        if not self.can_spend(1):
            return False

        status = str(match.get("status") or "").upper().strip()
        if bool(match.get("isFinalized")) or status in FINAL_STATUSES:
            self.skipped_finalized += 1
            return False

        if status in IN_PLAY_STATUSES:
            return True

        now = now_utc or dt.datetime.now(dt.timezone.utc)
        ko = parse_utc_iso(str(match.get("utcDate") or ""))
        if ko is None:
            self.skipped_out_of_window += 1
            return False

        delta_hours = (ko - now).total_seconds() / 3600.0
        # Allow当日焦点 matches (-3h .. +24h), prioritized if lottery-tagged or top leagues
        if -3.0 <= delta_hours <= 24.0:
            is_focus = bool(match.get("lotteryCode")) or str(match.get("competitionCode") or "") in {
                "PL", "PD", "BL1", "SA", "FL1", "CL", "UEL", "ACL", "J1", "J2", "KL1", "WC", "EC"
            }
            if is_focus:
                return True

        self.skipped_out_of_window += 1
        return False


class ApiFootballClient:
    """API-Football cloud client backed by ApiFootballQuotaGuard."""

    def __init__(
        self,
        api_key: str,
        quota_guard: ApiFootballQuotaGuard | None = None,
        timeout_seconds: int = 20,
    ) -> None:
        self.api_key = (api_key or "").strip()
        self.quota = quota_guard or ApiFootballQuotaGuard()
        self.timeout_seconds = timeout_seconds

    @property
    def configured(self) -> bool:
        return bool(self.api_key)

    def _get(self, path_and_query: str) -> dict[str, Any]:
        if not self.configured:
            raise ValueError("API_FOOTBALL_KEY is not configured")
        if not self.quota.can_spend(1):
            raise RuntimeError("API-Football quota budget exhausted")
        url = f"{API_FOOTBALL_BASE}/{path_and_query.lstrip('/')}"
        req = urllib.request.Request(
            url,
            headers={
                "x-apisports-key": self.api_key,
                "Accept": "application/json",
                "User-Agent": "FootballCalendarCloudSync/2.0",
            },
        )
        self.quota.record_call(path_and_query)
        with urllib.request.urlopen(req, timeout=self.timeout_seconds) as resp:
            return json.loads(resp.read().decode("utf-8"))

    def fetch_fixtures_by_date(self, date_yyyy_mm_dd: str) -> list[dict[str, Any]]:
        if not self.configured or not self.quota.can_spend(1):
            return []
        payload = self._get(f"fixtures?date={date_yyyy_mm_dd}")
        return list(payload.get("response") or [])

    def fetch_live_fixtures(self) -> list[dict[str, Any]]:
        if not self.configured or not self.quota.can_spend(1):
            return []
        payload = self._get("fixtures?live=all")
        return list(payload.get("response") or [])

    def fetch_fixture_detail(self, fixture_id: int) -> dict[str, Any] | None:
        if not self.configured or fixture_id <= 0 or not self.quota.can_spend(1):
            return None
        payload = self._get(f"fixtures?id={fixture_id}")
        rows = payload.get("response") or []
        return rows[0] if rows else None

    @staticmethod
    def select_active_coach(coach_records: list[dict[str, Any]], team_id: int) -> tuple[str, str]:
        """Prefer active career tenure (end is None) with newest start date."""
        candidates: list[tuple[int, str, str, str]] = []
        for coach in coach_records:
            name = str(coach.get("name") or "").strip()
            photo = str(coach.get("photo") or "").strip()
            if not name:
                continue
            for st in coach.get("career") or []:
                tid = int(((st.get("team") or {}).get("id")) or 0)
                if team_id > 0 and tid != team_id:
                    continue
                is_active = 1 if st.get("end") in (None, "") else 0
                start = str(st.get("start") or "")
                candidates.append((is_active, start, name, photo))
        if not candidates:
            return ("", "")
        candidates.sort(key=lambda item: (item[0], item[1]), reverse=True)
        _, _, best_name, best_photo = candidates[0]
        return (best_name, best_photo)
