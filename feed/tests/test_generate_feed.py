import datetime as dt
import json
import pathlib
import sys
import tempfile
import unittest

FEED_DIR = pathlib.Path(__file__).resolve().parents[1]
if str(FEED_DIR) not in sys.path:
    sys.path.insert(0, str(FEED_DIR))

import generate_feed as gf  # noqa: E402


class GenerateFeedTest(unittest.TestCase):
    def test_normalize_api_match_localizes_teams_and_scores(self) -> None:
        raw = {
            "id": 99001,
            "utcDate": "2026-11-26T20:00:00Z",
            "status": "FINISHED",
            "matchday": 12,
            "stage": "REGULAR_SEASON",
            "lastUpdated": "2026-11-26T22:05:00Z",
            "competition": {"code": "PL", "name": "Premier League"},
            "homeTeam": {"id": 57, "name": "Arsenal FC", "shortName": "Arsenal", "tla": "ARS"},
            "awayTeam": {"id": 65, "name": "Manchester City FC", "shortName": "Man City", "tla": "MCI"},
            "score": {"fullTime": {"home": 2, "away": 1}},
        }
        match = gf.normalize_api_match(raw, "PL")
        self.assertEqual(match.id, "fd-99001")
        self.assertEqual(match.competitionZhName, "英超")
        self.assertEqual(match.homeTeamZhName, "阿森纳")
        self.assertEqual(match.awayTeamZhName, "曼城")
        self.assertEqual(match.homeScore, 2)
        self.assertEqual(match.awayScore, 1)
        self.assertEqual(match.utcDate, "2026-11-26T20:00:00Z")

    def test_build_ics_contains_rfc5545_vevents_and_postponed_status(self) -> None:
        anchor = dt.datetime(2026, 11, 25, 12, 0, tzinfo=dt.timezone.utc)
        matches = gf.generate_demo_matches(anchor)
        ics = gf.build_ics(matches, "测试赛程", anchor)

        self.assertIn("BEGIN:VCALENDAR", ics)
        self.assertIn("END:VCALENDAR", ics)
        self.assertIn("BEGIN:VEVENT", ics)
        self.assertIn("UID:demo-1003@football-calendar", ics)
        self.assertIn("STATUS:CANCELLED", ics)  # postponed fixture demo-1012
        self.assertIn("SUMMARY:[英超] 利物浦 vs 曼城", ics)

    def test_write_feed_outputs_creates_json_ics_and_html(self) -> None:
        anchor = dt.datetime(2026, 11, 25, 12, 0, tzinfo=dt.timezone.utc)
        matches = gf.generate_demo_matches(anchor)
        with tempfile.TemporaryDirectory() as tmp:
            out_dir = pathlib.Path(tmp)
            gf.write_feed_outputs(out_dir, matches, "demo_fallback", gf.DEFAULT_COMPETITIONS, anchor)

            fixtures_json = json.loads((out_dir / "fixtures.json").read_text(encoding="utf-8"))
            self.assertEqual(fixtures_json["schemaVersion"], 1)
            self.assertEqual(fixtures_json["mode"], "demo_fallback")
            self.assertEqual(len(fixtures_json["matches"]), len(matches))

            self.assertTrue((out_dir / "football.ics").exists())
            self.assertTrue((out_dir / "ics" / "PL.ics").exists())
            self.assertTrue((out_dir / "ics" / "CL.ics").exists())
            html_text = (out_dir / "index.html").read_text(encoding="utf-8")
            self.assertIn("足球赛事日历", html_text)
            self.assertIn("利物浦", html_text)


if __name__ == "__main__":
    unittest.main()
