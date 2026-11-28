#!/usr/bin/env python3
"""Official China Sporttery (竞彩足球) public fixture schedule provider with snapshot fallback."""

from __future__ import annotations

import datetime as dt
import json
import pathlib
import re
import urllib.request
from dataclasses import asdict, dataclass
from typing import Any, Callable

BEIJING_TZ = dt.timezone(dt.timedelta(hours=8))

SPORTTERY_CALCULATOR_URL = (
    "https://webapi.sporttery.cn/gateway/jc/football/getMatchCalculatorV1.qry?poolCode=hhad,had&channel=c"
)

SPORTTERY_HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    ),
    "Referer": "https://www.sporttery.cn/",
    "Origin": "https://www.sporttery.cn",
    "Accept": "application/json, text/plain, */*",
    "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
}

WEEKDAY_ZH = {
    0: "周一",
    1: "周二",
    2: "周三",
    3: "周四",
    4: "周五",
    5: "周六",
    6: "周日",
}


@dataclass(frozen=True)
class SportteryFixture:
    """Strictly compliant public Sporttery match record (no betting links, odds, or prompts)."""

    sportteryId: str
    lotteryCode: str
    homeName: str
    awayName: str
    leagueName: str
    kickoffUtc: str
    kickoffMillis: int
    lotteryHandicap: int | None
    lotteryDeadlineUtc: int

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)

    @classmethod
    def from_dict(cls, raw: dict[str, Any]) -> SportteryFixture:
        return cls(
            sportteryId=str(raw.get("sportteryId") or ""),
            lotteryCode=str(raw.get("lotteryCode") or ""),
            homeName=str(raw.get("homeName") or ""),
            awayName=str(raw.get("awayName") or ""),
            leagueName=str(raw.get("leagueName") or ""),
            kickoffUtc=str(raw.get("kickoffUtc") or ""),
            kickoffMillis=int(raw.get("kickoffMillis") or 0),
            lotteryHandicap=raw.get("lotteryHandicap"),
            lotteryDeadlineUtc=int(raw.get("lotteryDeadlineUtc") or 0),
        )


def parse_beijing_datetime_to_utc(raw: str) -> tuple[str, int] | None:
    """Parse Beijing time string ('YYYY-MM-DD HH:MM[:SS]') into (utc_iso, epoch_millis)."""
    cleaned = str(raw or "").strip()
    if not cleaned:
        return None
    if cleaned.endswith("Z"):
        try:
            dt_utc = dt.datetime.fromisoformat(cleaned[:-1] + "+00:00").astimezone(dt.timezone.utc)
            return (dt_utc.strftime("%Y-%m-%dT%H:%M:%SZ"), int(dt_utc.timestamp() * 1000))
        except ValueError:
            return None
    for fmt in ("%Y-%m-%d %H:%M:%S", "%Y-%m-%d %H:%M", "%Y/%m/%d %H:%M:%S", "%Y/%m/%d %H:%M"):
        try:
            naive = dt.datetime.strptime(cleaned, fmt)
            bjt = naive.replace(tzinfo=BEIJING_TZ)
            utc = bjt.astimezone(dt.timezone.utc)
            return (utc.strftime("%Y-%m-%dT%H:%M:%SZ"), int(utc.timestamp() * 1000))
        except ValueError:
            continue
    try:
        iso = dt.datetime.fromisoformat(cleaned)
        if iso.tzinfo is None:
            iso = iso.replace(tzinfo=BEIJING_TZ)
        utc = iso.astimezone(dt.timezone.utc)
        return (utc.strftime("%Y-%m-%dT%H:%M:%SZ"), int(utc.timestamp() * 1000))
    except ValueError:
        return None


def parse_handicap_value(row: dict[str, Any]) -> int | None:
    """Extract integer handicap ('让球') value like -1, +1, or 0 cleanly."""
    candidates = [
        (row.get("hhad") or {}).get("goalLine") if isinstance(row.get("hhad"), dict) else None,
        row.get("goalLine"),
        row.get("fixedodds"),
        row.get("handicap"),
    ]
    for cand in candidates:
        if cand is None or str(cand).strip() == "":
            continue
        m = re.search(r"([+-]?\d+)", str(cand).strip())
        if m:
            try:
                return int(m.group(1))
            except ValueError:
                continue
    return None


def _extract_raw_rows(payload: dict[str, Any]) -> list[dict[str, Any]]:
    value = payload.get("value")
    if isinstance(value, dict):
        groups = value.get("matchInfoList")
        if isinstance(groups, list):
            rows: list[dict[str, Any]] = []
            for group in groups:
                if isinstance(group, dict):
                    sub = group.get("subMatchList")
                    if isinstance(sub, list):
                        rows.extend([x for x in sub if isinstance(x, dict)])
            if rows:
                return rows
        flat = value.get("list")
        if isinstance(flat, list):
            return [x for x in flat if isinstance(x, dict)]
    if isinstance(payload.get("matches"), list):
        return [x for x in payload["matches"] if isinstance(x, dict)]
    return []


def _format_lottery_code(row: dict[str, Any], ko_ms: int) -> str:
    raw_code = str(row.get("matchNumStr") or row.get("lotteryCode") or "").strip()
    if raw_code:
        return raw_code
    num = str(row.get("matchNum") or "").strip()
    bjt_dt = dt.datetime.fromtimestamp(ko_ms / 1000.0, tz=dt.timezone.utc).astimezone(BEIJING_TZ)
    prefix = WEEKDAY_ZH.get(bjt_dt.weekday(), "周赛")
    if num.isdigit():
        return f"{prefix}{int(num):03d}" if len(num) <= 3 else num
    return f"{prefix}{num or '001'}"


def parse_sporttery_payload(payload: dict[str, Any]) -> list[SportteryFixture]:
    """Normalize Sporttery JSON response into sorted, de-duplicated SportteryFixture items."""
    rows = _extract_raw_rows(payload)
    results: list[SportteryFixture] = []
    seen_codes: set[str] = set()

    for row in rows:
        # Skip explicitly cancelled/suspended sale rows if labeled hidden, keep open fixtures
        sell_status = str(row.get("sellStatus") or row.get("matchStatus") or "").strip().lower()
        if sell_status in {"cancel", "cancelled", "delete"}:
            continue

        home_name = str(
            row.get("homeTeamAbbName")
            or row.get("homeTeamAllName")
            or row.get("homeTeam")
            or ""
        ).strip()
        away_name = str(
            row.get("awayTeamAbbName")
            or row.get("awayTeamAllName")
            or row.get("awayTeam")
            or ""
        ).strip()
        if not home_name or not away_name:
            continue

        ko_raw = (
            row.get("kickoffUtc")
            or row.get("matchDateTime")
            or (f"{row.get('matchDate', '').strip()} {row.get('matchTime', '').strip()}".strip())
        )
        parsed_ko = parse_beijing_datetime_to_utc(str(ko_raw or ""))
        if not parsed_ko:
            continue
        ko_utc, ko_ms = parsed_ko

        deadline_raw = (
            row.get("lotteryDeadlineUtc")
            or row.get("sellEndTime")
            or row.get("saleEndTime")
            or row.get("stopSellTime")
            or row.get("deadline")
        )
        deadline_ms = ko_ms - 10 * 60 * 1000
        if isinstance(deadline_raw, (int, float)) and deadline_raw > 1_000_000_000_000:
            deadline_ms = int(deadline_raw)
        elif isinstance(deadline_raw, str) and deadline_raw.strip():
            parsed_dl = parse_beijing_datetime_to_utc(deadline_raw)
            if parsed_dl:
                deadline_ms = parsed_dl[1]

        lottery_code = _format_lottery_code(row, ko_ms)
        if not lottery_code or lottery_code in seen_codes:
            continue
        seen_codes.add(lottery_code)

        sporttery_id = str(row.get("matchId") or row.get("sportteryId") or lottery_code).strip()
        league_name = str(
            row.get("leagueAbbName") or row.get("leagueAllName") or row.get("leagueName") or ""
        ).strip()
        handicap = parse_handicap_value(row)

        results.append(
            SportteryFixture(
                sportteryId=sporttery_id,
                lotteryCode=lottery_code,
                homeName=home_name,
                awayName=away_name,
                leagueName=league_name,
                kickoffUtc=ko_utc,
                kickoffMillis=ko_ms,
                lotteryHandicap=handicap,
                lotteryDeadlineUtc=deadline_ms,
            )
        )

    results.sort(key=lambda item: (item.kickoffMillis, item.lotteryCode))
    return results


def load_sporttery_snapshot(path: pathlib.Path | None) -> list[SportteryFixture]:
    if path is None or not path.exists():
        return []
    try:
        raw = json.loads(path.read_text(encoding="utf-8"))
        rows = raw.get("fixtures") if isinstance(raw, dict) else raw
        if not isinstance(rows, list):
            return []
        return [SportteryFixture.from_dict(r) for r in rows if isinstance(r, dict) and r.get("lotteryCode")]
    except Exception:  # noqa: BLE001
        return []


def save_sporttery_snapshot(path: pathlib.Path | None, fixtures: list[SportteryFixture]) -> None:
    if path is None or not fixtures:
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    payload = {
        "updatedAtUtc": dt.datetime.now(dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "count": len(fixtures),
        "fixtures": [f.to_dict() for f in fixtures],
    }
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")


def default_http_get_json(url: str, timeout_seconds: int = 15) -> dict[str, Any]:
    req = urllib.request.Request(url, headers=SPORTTERY_HEADERS)
    with urllib.request.urlopen(req, timeout=timeout_seconds) as resp:
        return json.loads(resp.read().decode("utf-8"))


def fetch_sporttery_fixtures(
    *,
    snapshot_path: pathlib.Path | None = None,
    fallback_fixtures: list[SportteryFixture] | None = None,
    fetcher: Callable[[str, int], dict[str, Any]] | None = None,
    timeout_seconds: int = 15,
) -> tuple[list[SportteryFixture], bool, str | None]:
    """Fetch live Sporttery list; retain previous valid snapshot if fetch fails or yields empty."""
    get_json = fetcher or default_http_get_json
    try:
        raw_payload = get_json(SPORTTERY_CALCULATOR_URL, timeout_seconds)
        if not isinstance(raw_payload, dict):
            raise ValueError("Sporttery payload is not a JSON object")
        parsed = parse_sporttery_payload(raw_payload)
        if not parsed:
            raise ValueError("Sporttery payload parsed 0 valid fixtures")
        save_sporttery_snapshot(snapshot_path, parsed)
        return (parsed, False, None)
    except Exception as exc:  # noqa: BLE001
        cached = list(fallback_fixtures or []) or load_sporttery_snapshot(snapshot_path)
        return (cached, True, str(exc))
