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
    def test_normalize_api_match_localizes_teams_and_crests(self) -> None:
        raw = {
            "id": 99001,
            "utcDate": "2026-11-26T20:00:00Z",
            "status": "FINISHED",
            "matchday": 12,
            "stage": "REGULAR_SEASON",
            "lastUpdated": "2026-11-26T22:05:00Z",
            "competition": {"code": "PL", "name": "Premier League"},
            "homeTeam": {"id": 57, "name": "Arsenal FC", "shortName": "Arsenal", "tla": "ARS", "crest": "https://crests.football-data.org/57.png"},
            "awayTeam": {"id": 65, "name": "Manchester City FC", "shortName": "Man City", "tla": "MCI"},
            "score": {"fullTime": {"home": 2, "away": 1}},
        }
        match = gf.normalize_api_match(raw, "PL")
        self.assertEqual(match.id, "fd-99001")
        self.assertEqual(match.competitionZhName, "英超")
        self.assertEqual(match.homeTeamZhName, "阿森纳")
        self.assertEqual(match.homeTeamCrest, "https://crests.football-data.org/57.png")
        self.assertTrue(match.awayTeamCrest.startswith("https://"))

    def test_demo_includes_national_teams_standings_and_players(self) -> None:
        anchor = dt.datetime(2026, 11, 25, 12, 0, tzinfo=dt.timezone.utc)
        matches = gf.generate_demo_matches(anchor)
        standings = gf.generate_demo_standings()
        players = gf.enrich_players()

        self.assertTrue(any(m.competitionCode in {"WC", "EC", "INT"} for m in matches))
        self.assertTrue(all(m.homeTeamCrest and m.awayTeamCrest for m in matches))
        self.assertGreaterEqual(len(standings), 20)
        self.assertGreaterEqual(len(players), 14)
        self.assertTrue(all(p["portraitUrl"].startswith("https://") for p in players))

    def test_write_feed_outputs_creates_v2_json_ics_and_html(self) -> None:
        anchor = dt.datetime(2026, 11, 25, 12, 0, tzinfo=dt.timezone.utc)
        matches = gf.generate_demo_matches(anchor)
        standings = gf.generate_demo_standings()
        players = gf.enrich_players()

        with tempfile.TemporaryDirectory() as tmp:
            out_dir = pathlib.Path(tmp)
            gf.write_feed_outputs(
                out_dir, matches, standings, players, "demo_fallback", gf.DEFAULT_COMPETITIONS, anchor
            )

            fixtures_json = json.loads((out_dir / "fixtures.json").read_text(encoding="utf-8"))
            self.assertEqual(fixtures_json["schemaVersion"], 2)
            self.assertEqual(len(fixtures_json["matches"]), len(matches))
            self.assertEqual(len(fixtures_json["standings"]), len(standings))
            self.assertEqual(len(fixtures_json["players"]), len(players))

            self.assertTrue((out_dir / "ics" / "WC.ics").exists())
            html_text = (out_dir / "index.html").read_text(encoding="utf-8")
            self.assertIn("联赛积分榜", html_text)
            self.assertIn("热门球星库", html_text)
            self.assertIn("#E11D48", html_text)


if __name__ == "__main__":
    unittest.main()
