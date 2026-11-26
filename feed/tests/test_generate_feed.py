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
import localization as loc  # noqa: E402


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
            "homeTeam": {
                "id": 57,
                "name": "Arsenal FC",
                "shortName": "Arsenal",
                "tla": "ARS",
                "crest": "https://crests.football-data.org/57.png",
            },
            "awayTeam": {"id": 65, "name": "Manchester City FC", "shortName": "Man City", "tla": "MCI"},
            "score": {"fullTime": {"home": 2, "away": 1}},
        }
        match = gf.normalize_api_match(raw, "PL")
        self.assertEqual(match.id, "fd-99001")
        self.assertEqual(match.competitionZhName, "英超")
        self.assertEqual(match.homeTeamZhName, "阿森纳")
        self.assertEqual(match.homeTeamCrest, "https://crests.football-data.org/57.png")
        self.assertTrue(match.awayTeamCrest.startswith("https://"))

    def test_transliteration_and_af_normalization(self) -> None:
        zh_known, is_tr_known = loc.localize_player("Kylian Mbappé")
        self.assertEqual(zh_known, "姆巴佩")
        self.assertFalse(is_tr_known)

        zh_obscure, is_tr_obscure = loc.localize_player("Mateo Bramble")
        self.assertTrue(is_tr_obscure)
        self.assertTrue(loc.contains_cjk(zh_obscure))
        self.assertIn("马特奥", zh_obscure)

        self.assertEqual(loc.localize_team("Japan U17"), "日本U17")
        self.assertEqual(loc.localize_team("Vissel Kobe"), "神户胜利船")

        af_raw = {
            "fixture": {"id": 1649497, "date": "2026-11-25T10:00:00+00:00", "status": {"short": "1H", "elapsed": 34}, "venue": {"name": "Noevir Stadium Kobe"}},
            "league": {"id": 98, "name": "J1 League", "round": "Regular Season - 37"},
            "teams": {
                "home": {"id": 289, "name": "Vissel Kobe", "logo": "https://media.api-sports.io/football/teams/289.png"},
                "away": {"id": 290, "name": "Yokohama F. Marinos", "logo": "https://media.api-sports.io/football/teams/290.png"},
            },
            "goals": {"home": 1, "away": 0},
        }
        af_match = gf.normalize_af_fixture(af_raw)
        self.assertIsNotNone(af_match)
        assert af_match is not None
        self.assertEqual(af_match.id, "af-1649497")
        self.assertEqual(af_match.competitionCode, "J1")
        self.assertEqual(af_match.homeTeamZhName, "神户胜利船")
        self.assertEqual(af_match.elapsedMinutes, 34)
        self.assertEqual(af_match.apiFootballFixtureId, 1649497)

    def test_standing_zones_cover_ucl_playoffs_and_relegation(self) -> None:
        self.assertEqual(gf.determine_standing_zone("PL", 1, 20), ("UCL", "欧冠正赛区"))
        self.assertEqual(gf.determine_standing_zone("PL", 19, 20), ("RELEGATION", "降级区"))
        self.assertEqual(gf.determine_standing_zone("ELC", 4, 24), ("PLAYOFF", "升级附加赛区"))
        self.assertEqual(gf.determine_standing_zone("BL1", 16, 18), ("RELEGATION_PLAYOFF", "升降级附加赛区"))
        self.assertEqual(gf.determine_standing_zone("CL", 12, 36), ("PLAYOFF", "淘汰赛附加赛区"))
        self.assertEqual(loc.localize_espn_zone("Qualifies for round of 16", "UEL", 1, 36), ("UCL", "16强直通区"))
        self.assertEqual(loc.localize_espn_group("Eastern Conference"), "东部联盟")

    def test_normalize_espn_event_parses_live_clock_and_teams(self) -> None:
        raw_ev = {
            "id": "765242",
            "date": "2026-11-26T17:45Z",
            "status": {"displayClock": "67'", "type": {"state": "in", "name": "STATUS_SECOND_HALF"}},
            "competitions": [
                {
                    "venue": {"fullName": "Sukru Saracoglu"},
                    "competitors": [
                        {
                            "homeAway": "home",
                            "score": "2",
                            "team": {"id": "436", "displayName": "Fenerbahce", "shortDisplayName": "Fenerbahce", "logo": "https://a.espncdn.com/i/teamlogos/soccer/500/436.png"},
                        },
                        {
                            "homeAway": "away",
                            "score": "1",
                            "team": {"id": "134", "displayName": "VfB Stuttgart", "shortDisplayName": "Stuttgart", "logo": "https://a.espncdn.com/i/teamlogos/soccer/500/134.png"},
                        },
                    ],
                }
            ],
        }
        m = gf.normalize_espn_event(raw_ev, "uefa.europa")
        self.assertIsNotNone(m)
        assert m is not None
        self.assertEqual(m.competitionCode, "UEL")
        self.assertEqual(m.homeTeamZhName, "费内巴切")
        self.assertEqual(m.awayTeamZhName, "斯图加特")
        self.assertEqual(m.status, "LIVE")
        self.assertEqual(m.elapsedMinutes, 67)
        self.assertEqual(m.homeScore, 2)
        self.assertEqual(m.espnSlug, "uefa.europa")
        self.assertEqual(m.espnEventId, "765242")

    def test_write_feed_outputs_creates_v4_json_ics_and_html(self) -> None:
        anchor = dt.datetime(2026, 11, 25, 12, 0, tzinfo=dt.timezone.utc)
        matches = gf.generate_demo_matches(anchor)
        standings = gf.generate_demo_standings()
        players = gf.enrich_players()
        teams = [
            {
                "teamId": 57,
                "competitionCode": "PL",
                "name": "Arsenal FC",
                "shortName": "Arsenal",
                "zhName": "阿森纳",
                "crest": "https://crests.football-data.org/57.png",
                "venue": "Emirates Stadium",
                "founded": 1886,
                "clubColors": "Red / White",
                "coachName": "Mikel Arteta",
                "coachZhName": "米克尔·阿尔特塔",
                "coachNationality": "Spain",
                "squad": [],
            }
        ]

        with tempfile.TemporaryDirectory() as tmp:
            out_dir = pathlib.Path(tmp)
            gf.write_feed_outputs(
                out_dir, matches, standings, players, "demo_fallback", gf.DEFAULT_COMPETITIONS, anchor, teams=teams
            )

            fixtures_json = json.loads((out_dir / "fixtures.json").read_text(encoding="utf-8"))
            self.assertEqual(fixtures_json["schemaVersion"], 5)
            self.assertEqual(len(fixtures_json["matches"]), len(matches))
            self.assertEqual(len(fixtures_json["standings"]), len(standings))
            self.assertEqual(len(fixtures_json["players"]), len(players))
            self.assertEqual(len(fixtures_json["teams"]), 1)
            self.assertIn("liveDetails", fixtures_json)

            self.assertTrue((out_dir / "ics" / "WC.ics").exists())
            self.assertTrue((out_dir / "ics" / "J1.ics").exists())
            html_text = (out_dir / "index.html").read_text(encoding="utf-8")
            self.assertEqual(html_text.count("No.1") >= 1, True)
            self.assertIn("联赛积分榜", html_text)
            self.assertIn("球员榜", html_text)
            self.assertIn("No.1", html_text)
            self.assertNotIn("第1名", html_text)
            self.assertTrue(gf.resolve_team_logo("Norway").endswith("/nor.png"))
            self.assertTrue(gf.resolve_team_logo("埃及国家队").endswith("/egy.png"))


if __name__ == "__main__":
    unittest.main()
