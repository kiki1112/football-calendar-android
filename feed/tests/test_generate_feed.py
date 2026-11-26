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
            self.assertEqual(fixtures_json["schemaVersion"], 6)
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

    def test_commentary_translation_and_clock_parsing(self) -> None:
        self.assertEqual(gf._parse_clock_parts("45'+3'"), (45, 3))
        self.assertEqual(gf._parse_clock_parts("19'"), (19, None))
        goal_zh = gf._translate_espn_commentary(
            "Goal! Bournemouth 1, Nottingham Forest 0. Evanilson right footed shot.",
            "goal",
            "伯恩茅斯",
            "Evanilson",
            "James Hill",
        )
        self.assertIn("破门得分", goal_zh)
        self.assertIn("伯恩茅斯", goal_zh)
        sub_zh = gf._translate_espn_commentary(
            "Substitution, Nottingham Forest. Igor Jesus replaces Dan Ndoye.",
            "substitution",
            "诺丁汉森林",
            "Igor Jesus",
            "Dan Ndoye",
        )
        self.assertIn("↑", sub_zh)
        self.assertIn("↓", sub_zh)

    def test_v25_acl_logos_flagcdn_and_brazil_dedup(self) -> None:
        self.assertEqual(
            gf.resolve_team_logo("Jeonbuk Hyundai Motors"),
            "https://media.api-sports.io/football/teams/2762.png",
        )
        self.assertEqual(
            gf.resolve_team_logo("DR Congo"),
            "https://flagcdn.com/w160/cd.png",
        )
        self.assertEqual(loc.localize_team("Atlético-MG"), "米内罗竞技")
        self.assertEqual(loc.localize_team("CA Mineiro"), "米内罗竞技")
        self.assertEqual(loc.localize_team("Corinthians"), "科林蒂安")

        import dataclasses
        raw_fd = {
            "id": 555091,
            "utcDate": "2026-11-26T22:00:00Z",
            "status": "FINISHED",
            "matchday": 35,
            "stage": "REGULAR_SEASON",
            "lastUpdated": "2026-11-27T00:05:00Z",
            "competition": {"code": "BSA", "name": "Campeonato Brasileiro Série A"},
            "homeTeam": {"id": 1766, "name": "CA Mineiro", "shortName": "Mineiro", "tla": "CAM"},
            "awayTeam": {"id": 1779, "name": "SC Corinthians Paulista", "shortName": "Corinthians", "tla": "COR"},
            "score": {"fullTime": {"home": 1, "away": 1}},
        }
        fd_match = gf.normalize_api_match(raw_fd, "BSA")
        espn_dup = dataclasses.replace(
            fd_match,
            id="espn-401841319",
            homeTeamName="Atlético-MG",
            awayTeamName="Corinthians",
            homeTeamZhName=loc.localize_team("Atlético-MG"),
            awayTeamZhName=loc.localize_team("Corinthians"),
        )
        collected = {fd_match.id: fd_match}
        espn_index: dict[tuple[str, str], gf.NormalizedMatch] = {}
        gf._merge_espn_match(espn_dup, collected, espn_index)
        self.assertEqual(list(collected.keys()), [fd_match.id])

    def test_v26_espn_uid_slug_inference(self) -> None:
        ev = {
            "id": "401879234",
            "uid": "s:600~l:700~e:401879234",
            "date": "2026-11-22T15:00Z",
            "season": {"year": 2026, "slug": "2026-27-english-premier-league"},
            "status": {"type": {"state": "post", "name": "STATUS_FULL_TIME"}},
            "competitions": [
                {
                    "competitors": [
                        {"homeAway": "home", "score": "2", "team": {"id": "364", "displayName": "Liverpool"}},
                        {"homeAway": "away", "score": "0", "team": {"id": "360", "displayName": "Manchester United"}},
                    ]
                }
            ],
        }
        nm = gf.normalize_espn_event(ev)
        self.assertIsNotNone(nm)
        assert nm is not None
        self.assertEqual(nm.competitionCode, "PL")
        self.assertEqual(nm.espnSlug, "eng.1")
        self.assertEqual(nm.homeTeamZhName, "利物浦")


if __name__ == "__main__":
    unittest.main()
