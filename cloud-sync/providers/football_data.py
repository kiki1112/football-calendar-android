#!/usr/bin/env python3
"""Official Football-Data.org v4 provider client with rate-limit protection."""

from __future__ import annotations

import json
import time
import urllib.error
import urllib.request
from dataclasses import dataclass
from typing import Any

FOOTBALL_DATA_BASE = "https://api.football-data.org/v4"


@dataclass
class FootballDataQuotaTracker:
    calls_made: int = 0
    min_interval_seconds: float = 0.65
    last_call_ts: float = 0.0

    def wait_slot(self) -> None:
        now = time.monotonic()
        elapsed = now - self.last_call_ts
        if self.last_call_ts > 0 and elapsed < self.min_interval_seconds:
            time.sleep(self.min_interval_seconds - elapsed)
        self.last_call_ts = time.monotonic()
        self.calls_made += 1


class FootballDataClient:
    """Rate-limited cloud client for Football-Data.org v4 (Top 5 leagues + UCL + ELC/DED/PPL/BSA)."""

    def __init__(self, api_key: str, timeout_seconds: int = 20) -> None:
        self.api_key = (api_key or "").strip()
        self.timeout_seconds = timeout_seconds
        self.quota = FootballDataQuotaTracker()

    @property
    def configured(self) -> bool:
        return bool(self.api_key)

    def _get(self, path: str, retries: int = 2) -> dict[str, Any]:
        if not self.configured:
            raise ValueError("FOOTBALL_DATA_API_KEY is not configured")
        url = f"{FOOTBALL_DATA_BASE}/{path.lstrip('/')}"
        headers = {
            "X-Auth-Token": self.api_key,
            "Accept": "application/json",
            "User-Agent": "FootballCalendarCloudSync/2.0",
        }
        last_err: Exception | None = None
        for attempt in range(retries + 1):
            self.quota.wait_slot()
            req = urllib.request.Request(url, headers=headers)
            try:
                with urllib.request.urlopen(req, timeout=self.timeout_seconds) as resp:
                    return json.loads(resp.read().decode("utf-8"))
            except urllib.error.HTTPError as exc:
                last_err = exc
                if exc.code == 429 and attempt < retries:
                    retry_after = float(exc.headers.get("Retry-After") or "6.5")
                    time.sleep(min(max(retry_after, 1.0), 15.0))
                    continue
                raise
            except Exception as exc:  # noqa: BLE001
                last_err = exc
                if attempt < retries:
                    time.sleep(1.2)
                    continue
                raise
        raise RuntimeError(f"Football-Data request failed: {last_err}")

    def fetch_matches(self, competitions: list[str], date_from: str, date_to: str) -> dict[str, Any]:
        comp_csv = ",".join(competitions)
        return self._get(f"matches?competitions={comp_csv}&dateFrom={date_from}&dateTo={date_to}")

    def fetch_standings(self, competition_code: str) -> dict[str, Any]:
        return self._get(f"competitions/{competition_code}/standings")

    def fetch_scorers(self, competition_code: str, limit: int = 30) -> dict[str, Any]:
        return self._get(f"competitions/{competition_code}/scorers?limit={limit}")

    def fetch_teams(self, competition_code: str) -> dict[str, Any]:
        return self._get(f"competitions/{competition_code}/teams")
