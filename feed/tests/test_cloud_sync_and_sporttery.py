import datetime as dt
import pathlib
import sys
import tempfile
import unittest

REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
CLOUD_SYNC = REPO_ROOT / "cloud-sync"
FEED_DIR = REPO_ROOT / "feed"
for p in (CLOUD_SYNC, FEED_DIR):
    if str(p) not in sys.path:
        sys.path.insert(0, str(p))

from matchers.entity_matcher import MatchEntityMatcher  # noqa: E402
from providers.api_football import ApiFootballQuotaGuard  # noqa: E402
from providers.sporttery import (  # noqa: E402
    SportteryFixture,
    fetch_sporttery_fixtures,
    parse_sporttery_payload,
)
from validators.feed_validator import validate_feed_payload  # noqa: E402


class CloudSyncAndSportteryTest(unittest.TestCase):
    def test_sporttery_parser_extracts_code_handicap_and_deadline_fallback(self) -> None:
        raw = {
            "value": {
                "matchInfoList": [
                    {
                        "businessDate": "2026-11-28",
                        "subMatchList": [
                            {
                                "matchId": "102901",
                                "matchNumStr": "周六008",
                                "homeTeamAbbName": "曼联",
                                "awayTeamAbbName": "布伦特",
                                "leagueAbbName": "英超",
                                "matchDate": "2026-11-28",
                                "matchTime": "23:00:00",
                                "hhad": {"goalLine": "-1"},
                            },
                            {
                                "matchId": "102902",
                                "matchNumStr": "周六009",
                                "homeTeamAbbName": "巴萨",
                                "awayTeamAbbName": "马竞",
                                "leagueAbbName": "西甲",
                                "matchDate": "2026-11-29",
                                "matchTime": "04:00:00",
                                "sellEndTime": "2026-11-29 03:45:00",
                                "hhad": {"goalLine": "+1"},
                            },
                        ],
                    }
                ]
            }
        }
        parsed = parse_sporttery_payload(raw)
        self.assertEqual(len(parsed), 2)
        first, second = parsed
        self.assertEqual(first.lotteryCode, "周六008")
        self.assertEqual(first.lotteryHandicap, -1)
        # No sellEndTime -> defaults to kickoff - 10 min (22:50 BJT == 14:50 UTC)
        expected_first_dl = int(dt.datetime(2026, 11, 28, 14, 50, tzinfo=dt.timezone.utc).timestamp() * 1000)
        self.assertEqual(first.lotteryDeadlineUtc, expected_first_dl)

        self.assertEqual(second.lotteryCode, "周六009")
        self.assertEqual(second.lotteryHandicap, 1)
        expected_second_dl = int(dt.datetime(2026, 11, 28, 19, 45, tzinfo=dt.timezone.utc).timestamp() * 1000)
        self.assertEqual(second.lotteryDeadlineUtc, expected_second_dl)

    def test_sporttery_fetcher_retains_snapshot_when_upstream_fails(self) -> None:
        cached = SportteryFixture(
            sportteryId="102901",
            lotteryCode="周六008",
            homeName="曼联",
            awayName="布伦特福德",
            leagueName="英超",
            kickoffUtc="2026-11-28T15:00:00Z",
            kickoffMillis=int(dt.datetime(2026, 11, 28, 15, 0, tzinfo=dt.timezone.utc).timestamp() * 1000),
            lotteryHandicap=-1,
            lotteryDeadlineUtc=int(dt.datetime(2026, 11, 28, 14, 50, tzinfo=dt.timezone.utc).timestamp() * 1000),
        )

        def broken_fetcher(_url: str, _timeout: int) -> dict:
            raise TimeoutError("sporttery upstream timeout")

        with tempfile.TemporaryDirectory() as tmp:
            snap_file = pathlib.Path(tmp) / "sporttery_snapshot.json"
            # First simulate healthy fetch saving snapshot
            fixtures_ok, used_fb1, err1 = fetch_sporttery_fixtures(
                snapshot_path=snap_file,
                fetcher=lambda _u, _t: {
                    "value": {
                        "list": [
                            {
                                "matchId": "102901",
                                "matchNumStr": "周六008",
                                "homeTeamAbbName": "曼联",
                                "awayTeamAbbName": "布伦特",
                                "leagueAbbName": "英超",
                                "matchDate": "2026-11-28",
                                "matchTime": "23:00:00",
                                "hhad": {"goalLine": "-1"},
                            }
                        ]
                    }
                },
            )
            self.assertFalse(used_fb1)
            self.assertIsNone(err1)
            self.assertEqual(len(fixtures_ok), 1)
            self.assertTrue(snap_file.exists())

            # Next simulate outage -> snapshot preserved instead of wiped
            fixtures_fb, used_fb2, err2 = fetch_sporttery_fixtures(
                snapshot_path=snap_file,
                fallback_fixtures=[cached],
                fetcher=broken_fetcher,
            )
            self.assertTrue(used_fb2)
            self.assertIsNotNone(err2)
            self.assertEqual(len(fixtures_fb), 1)
            self.assertEqual(fixtures_fb[0].lotteryCode, "周六008")

    def test_entity_matcher_window_and_standalone_fallback(self) -> None:
        matcher = MatchEntityMatcher()
        base_ko_ms = int(dt.datetime(2026, 11, 28, 15, 0, tzinfo=dt.timezone.utc).timestamp() * 1000)

        matches = [
            {
                "id": "fd-560655",
                "competitionCode": "PL",
                "utcDate": "2026-11-28T15:05:00Z",  # 5m offset -> within +-15m
                "homeTeamName": "Manchester United FC",
                "homeTeamZhName": "曼联",
                "awayTeamName": "Brentford FC",
                "awayTeamZhName": "布伦特福德",
                "status": "TIMED",
            },
            {
                "id": "fd-564762",
                "competitionCode": "PD",
                "utcDate": "2026-11-28T18:00:00Z",  # 30m difference from 18:30 -> outside +-15m
                "homeTeamName": "Real Madrid CF",
                "homeTeamZhName": "皇家马德里",
                "awayTeamName": "Deportivo Alavés",
                "awayTeamZhName": "阿拉维斯",
                "status": "TIMED",
            },
        ]

        st_fixtures = [
            SportteryFixture(
                sportteryId="st1",
                lotteryCode="周六008",
                homeName="曼联",
                awayName="布伦特",
                leagueName="英超",
                kickoffUtc="2026-11-28T15:00:00Z",
                kickoffMillis=base_ko_ms,
                lotteryHandicap=-1,
                lotteryDeadlineUtc=base_ko_ms - 600_000,
            ),
            SportteryFixture(
                sportteryId="st2",
                lotteryCode="周六019",
                homeName="首尔衣恋",
                awayName="釜山偶像",
                leagueName="韩K联",
                kickoffUtc="2026-11-28T11:00:00Z",
                kickoffMillis=int(dt.datetime(2026, 11, 28, 11, 0, tzinfo=dt.timezone.utc).timestamp() * 1000),
                lotteryHandicap=0,
                lotteryDeadlineUtc=int(dt.datetime(2026, 11, 28, 10, 50, tzinfo=dt.timezone.utc).timestamp() * 1000),
            ),
        ]

        merged, stats = matcher.apply_sporttery_fixtures(matches, st_fixtures)
        self.assertEqual(stats.matched_count, 1)
        self.assertEqual(stats.standalone_count, 1)
        man_utd = [m for m in merged if m["id"] == "fd-560655"][0]
        self.assertEqual(man_utd["lotteryCode"], "周六008")
        self.assertEqual(man_utd["lotteryHandicap"], -1)
        standalone = [m for m in merged if m["id"] == "st-st2"][0]
        self.assertEqual(standalone["lotteryCode"], "周六019")
        self.assertEqual(standalone["competitionCode"], "KL1")

    def test_validator_cleans_coaches_lineups_and_quota_guard_skips_finalized(self) -> None:
        payload = {
            "matches": [
                {"id": "m-fin", "status": "FT", "utcDate": "2026-11-24T20:00:00Z", "competitionCode": "CL"},
                {
                    "id": "m-live",
                    "status": "1H",
                    "utcDate": "2026-11-28T14:00:00Z",
                    "competitionCode": "PL",
                },
            ],
            "teams": [
                {
                    "teamId": 86,
                    "name": "Real Madrid CF",
                    "zhName": "皇家马德里",
                    "coachName": "José Mourinho",
                    "coachZhName": "何塞·莫乌里恩霍",
                },
                {
                    "teamId": 57,
                    "name": "Arsenal FC",
                    "zhName": "阿森纳",
                    "coachName": "Mikel Arteta",
                    "coachZhName": "米克尔·阿尔特塔",
                },
            ],
            "liveDetails": {
                "m-live": {
                    "matchId": "m-live",
                    "lineups": [
                        {
                            "teamName": "Arsenal FC",
                            "coachName": "Mikel Arteta",
                            "coachZhName": "米克尔·阿尔特塔",
                            "startXI": [
                                {"name": "David Raya", "number": 22},
                                {"name": "David Raya", "number": 22},
                                {"name": "Bukayo Saka", "number": 7},
                            ],
                            "substitutes": [
                                {"name": "Bukayo Saka", "number": 7},
                                {"name": "Leandro Trossard", "number": 19},
                            ],
                        }
                    ],
                }
            },
        }

        cleaned = validate_feed_payload(payload)
        m_fin = cleaned["matches"][0]
        self.assertEqual(m_fin["status"], "FINISHED")
        self.assertTrue(m_fin["isFinalized"])

        rm = cleaned["teams"][0]
        self.assertEqual(rm["coachName"], "Álvaro Arbeloa")
        self.assertEqual(rm["coachZhName"], "阿韦洛亚")

        ars = cleaned["teams"][1]
        self.assertEqual(ars["coachZhName"], "阿尔特塔")

        side = cleaned["liveDetails"]["m-live"]["lineups"][0]
        self.assertEqual(len(side["startXI"]), 2)
        self.assertEqual(len(side["substitutes"]), 1)
        self.assertEqual(side["coachZhName"], "阿尔特塔")

        # Quota guard skips finalized matches and honors in-play focus within budget
        guard = ApiFootballQuotaGuard(daily_limit=100, per_run_budget=2)
        now = dt.datetime(2026, 11, 28, 14, 15, tzinfo=dt.timezone.utc)
        self.assertFalse(guard.should_fetch_match_detail(m_fin, now_utc=now))
        self.assertEqual(guard.skipped_finalized, 1)
        self.assertTrue(guard.should_fetch_match_detail(cleaned["matches"][1], now_utc=now))


if __name__ == "__main__":
    unittest.main()
