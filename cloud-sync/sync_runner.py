#!/usr/bin/env python3
"""Zero-cost cloud aggregation runner: Football-Data + API-Football (quota-guarded) + Sporttery + validators."""

from __future__ import annotations

import argparse
import datetime as dt
import json
import os
import pathlib
import sys
from typing import Any

CLOUD_SYNC_DIR = pathlib.Path(__file__).resolve().parent
REPO_ROOT = CLOUD_SYNC_DIR.parent
FEED_DIR = REPO_ROOT / "feed"
for p in (CLOUD_SYNC_DIR, FEED_DIR):
    if str(p) not in sys.path:
        sys.path.insert(0, str(p))

import generate_feed as gf  # noqa: E402
from matchers.entity_matcher import MatchEntityMatcher  # noqa: E402
from providers.api_football import ApiFootballQuotaGuard  # noqa: E402
from providers.sporttery import (  # noqa: E402
    SportteryFixture,
    fetch_sporttery_fixtures,
)
from validators.feed_validator import validate_feed_payload  # noqa: E402


def _extract_previous_sporttery_fixtures(existing_feed: dict[str, Any]) -> list[SportteryFixture]:
    out: list[SportteryFixture] = []
    for row in existing_feed.get("matches") or []:
        if not isinstance(row, dict):
            continue
        code = str(row.get("lotteryCode") or "").strip()
        if not code:
            continue
        ko = str(row.get("utcDate") or "")
        from providers.sporttery import parse_beijing_datetime_to_utc

        parsed = parse_beijing_datetime_to_utc(ko)
        ko_ms = parsed[1] if parsed else 0
        dl = int(row.get("lotteryDeadlineUtc") or (ko_ms - 600_000 if ko_ms > 0 else 0))
        out.append(
            SportteryFixture(
                sportteryId=str(row.get("id") or code).removeprefix("st-"),
                lotteryCode=code,
                homeName=str(row.get("homeTeamZhName") or row.get("homeTeamName") or ""),
                awayName=str(row.get("awayTeamZhName") or row.get("awayTeamName") or ""),
                leagueName=str(row.get("competitionZhName") or row.get("competitionCode") or ""),
                kickoffUtc=ko,
                kickoffMillis=ko_ms,
                lotteryHandicap=row.get("lotteryHandicap"),
                lotteryDeadlineUtc=dl,
            )
        )
    return out


def merge_sporttery_onto_matches(
    matches: list[gf.NormalizedMatch],
    sporttery_fixtures: list[SportteryFixture],
) -> tuple[list[gf.NormalizedMatch], dict[str, int]]:
    matcher = MatchEntityMatcher()
    raw_dicts = [m.to_dict() for m in matches]
    merged_dicts, stats = matcher.apply_sporttery_fixtures(raw_dicts, sporttery_fixtures)

    out_matches: list[gf.NormalizedMatch] = []
    for d in merged_dicts:
        out_matches.append(
            gf.NormalizedMatch(
                id=str(d["id"]),
                competitionCode=str(d.get("competitionCode") or "OTHER"),
                competitionName=str(d.get("competitionName") or ""),
                competitionZhName=str(d.get("competitionZhName") or ""),
                matchday=d.get("matchday"),
                stage=str(d.get("stage") or "REGULAR_SEASON"),
                utcDate=str(d["utcDate"]),
                status=str(d.get("status") or "TIMED"),
                homeTeamId=int(d.get("homeTeamId") or 0),
                homeTeamName=str(d.get("homeTeamName") or "TBD"),
                homeTeamShortName=str(d.get("homeTeamShortName") or "TBD"),
                homeTeamZhName=str(d.get("homeTeamZhName") or "待定"),
                homeTeamCrest=str(d.get("homeTeamCrest") or ""),
                awayTeamId=int(d.get("awayTeamId") or 0),
                awayTeamName=str(d.get("awayTeamName") or "TBD"),
                awayTeamShortName=str(d.get("awayTeamShortName") or "TBD"),
                awayTeamZhName=str(d.get("awayTeamZhName") or "待定"),
                awayTeamCrest=str(d.get("awayTeamCrest") or ""),
                homeScore=d.get("homeScore"),
                awayScore=d.get("awayScore"),
                venue=str(d.get("venue") or ""),
                lastUpdatedUtc=str(d.get("lastUpdatedUtc") or d["utcDate"]),
                elapsedMinutes=d.get("elapsedMinutes"),
                apiFootballFixtureId=d.get("apiFootballFixtureId"),
                espnSlug=d.get("espnSlug"),
                espnEventId=d.get("espnEventId"),
                lotteryCode=d.get("lotteryCode"),
                lotteryHandicap=d.get("lotteryHandicap"),
                lotteryDeadlineUtc=d.get("lotteryDeadlineUtc"),
                isFinalized=bool(d.get("isFinalized", False)),
            )
        )
    return (out_matches, {"matched": stats.matched_count, "standalone": stats.standalone_count})


def run_sync(
    *,
    output_dir: pathlib.Path,
    competitions: list[str],
    days_back: int = 10,
    days_ahead: int = 18,
    existing_url: str | None = None,
    enable_sporttery: bool = True,
) -> dict[str, Any]:
    api_key = os.environ.get("FOOTBALL_DATA_API_KEY", "").strip() or None
    af_key = os.environ.get("API_FOOTBALL_KEY", "").strip() or None

    existing_feed = gf._load_existing_feed(existing_url) if existing_url else {}
    prev_sporttery = _extract_previous_sporttery_fixtures(existing_feed)

    matches, standings, teams, players, live_details, mode = gf.collect_data(
        api_key=api_key,
        competitions=competitions,
        days_back=days_back,
        days_ahead=days_ahead,
        existing_feed_url=existing_url,
        api_football_key=af_key,
        enable_espn=True,
    )

    quota_guard = ApiFootballQuotaGuard()
    snapshot_path = output_dir / "sporttery_snapshot.json"
    sporttery_used_fallback = False
    sporttery_err: str | None = None
    sporttery_fixtures: list[SportteryFixture] = []

    if enable_sporttery:
        sporttery_fixtures, sporttery_used_fallback, sporttery_err = fetch_sporttery_fixtures(
            snapshot_path=snapshot_path,
            fallback_fixtures=prev_sporttery,
        )
        matches, st_stats = merge_sporttery_onto_matches(matches, sporttery_fixtures)
    else:
        st_stats = {"matched": 0, "standalone": 0}

    paths = gf.write_feed_outputs(
        output_dir=output_dir,
        matches=matches,
        standings=standings,
        players=players,
        mode=mode,
        competitions=competitions,
        teams=teams,
        live_details=live_details,
    )

    # Ensure finalized payload passes cloud validator cleanly
    fixtures_payload = json.loads(paths["fixtures_json"].read_text(encoding="utf-8"))
    validated_payload = validate_feed_payload(fixtures_payload)
    paths["fixtures_json"].write_text(
        json.dumps(validated_payload, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )

    summary = {
        "mode": mode,
        "generatedAtUtc": validated_payload.get("generatedAtUtc"),
        "matches": len(validated_payload.get("matches") or []),
        "lotteryMatches": sum(1 for m in validated_payload.get("matches") or [] if m.get("lotteryCode")),
        "finalizedMatches": sum(1 for m in validated_payload.get("matches") or [] if m.get("isFinalized")),
        "sportteryMatched": st_stats["matched"],
        "sportteryStandalone": st_stats["standalone"],
        "sportteryFallbackUsed": sporttery_used_fallback,
        "sportteryError": sporttery_err,
        "apiFootballBudgetLimit": quota_guard.daily_limit,
    }
    (output_dir / "sync_status.json").write_text(
        json.dumps(summary, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    print(f"[cloud-sync] summary={json.dumps(summary, ensure_ascii=False)}")
    return summary


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Run zero-cost cloud aggregation pipeline")
    parser.add_argument("--out", default="_site", help="Output directory for GitHub Pages feed")
    parser.add_argument("--days-back", type=int, default=10)
    parser.add_argument("--days-ahead", type=int, default=18)
    parser.add_argument("--existing-url", default="")
    parser.add_argument("--no-sporttery", action="store_true")
    parser.add_argument("--competitions", default=",".join(gf.DEFAULT_COMPETITIONS))
    args = parser.parse_args(argv)

    comps = [c.strip() for c in args.competitions.split(",") if c.strip()]
    run_sync(
        output_dir=pathlib.Path(args.out),
        competitions=comps or gf.DEFAULT_COMPETITIONS,
        days_back=args.days_back,
        days_ahead=args.days_ahead,
        existing_url=args.existing_url.strip() or None,
        enable_sporttery=not args.no_sporttery,
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
