#!/usr/bin/env python3
"""Football match schedule feed generator for GitHub Pages and Android App sync.

Fetches upcoming/recent fixtures from football-data.org v4 when FOOTBALL_DATA_API_KEY
is available (or falls back to realistic dynamic demo fixtures anchored to the
current UTC date) and writes:
  - fixtures.json : Normalized feed consumed by the Android app
  - football.ics  : Combined RFC 5545 iCalendar subscription file
  - ics/<CODE>.ics: Per-competition iCalendar subscription files
  - index.html    : Static web dashboard for GitHub Pages
"""

from __future__ import annotations

import argparse
import dataclasses
import datetime as dt
import html
import json
import os
import pathlib
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from typing import Any

DEFAULT_COMPETITIONS = ["PL", "PD", "BL1", "SA", "FL1", "CL"]

COMPETITION_META: dict[str, dict[str, str]] = {
    "PL": {"name": "Premier League", "zhName": "英超", "country": "英格兰", "color": "#38003C"},
    "PD": {"name": "Primera Division", "zhName": "西甲", "country": "西班牙", "color": "#EE8707"},
    "BL1": {"name": "Bundesliga", "zhName": "德甲", "country": "德国", "color": "#D20515"},
    "SA": {"name": "Serie A", "zhName": "意甲", "country": "意大利", "color": "#024494"},
    "FL1": {"name": "Ligue 1", "zhName": "法甲", "country": "法国", "color": "#091C3E"},
    "CL": {"name": "UEFA Champions League", "zhName": "欧冠", "country": "欧洲", "color": "#0E1E5B"},
}

TEAM_ZH_MAP: dict[str, str] = {
    "Arsenal FC": "阿森纳",
    "Arsenal": "阿森纳",
    "Manchester City FC": "曼城",
    "Man City": "曼城",
    "Liverpool FC": "利物浦",
    "Liverpool": "利物浦",
    "Chelsea FC": "切尔西",
    "Chelsea": "切尔西",
    "Manchester United FC": "曼联",
    "Man United": "曼联",
    "Tottenham Hotspur FC": "热刺",
    "Tottenham": "热刺",
    "Newcastle United FC": "纽卡斯尔联",
    "Aston Villa FC": "阿斯顿维拉",
    "Real Madrid CF": "皇家马德里",
    "Real Madrid": "皇家马德里",
    "FC Barcelona": "巴塞罗那",
    "Barcelona": "巴塞罗那",
    "Club Atlético de Madrid": "马德里竞技",
    "Atlético Madrid": "马德里竞技",
    "Atletico Madrid": "马德里竞技",
    "Athletic Club": "毕尔巴鄂竞技",
    "FC Bayern München": "拜仁慕尼黑",
    "Bayern Munich": "拜仁慕尼黑",
    "Borussia Dortmund": "多特蒙德",
    "Dortmund": "多特蒙德",
    "Bayer 04 Leverkusen": "勒沃库森",
    "Leverkusen": "勒沃库森",
    "RB Leipzig": "RB莱比锡",
    "FC Internazionale Milano": "国际米兰",
    "Inter": "国际米兰",
    "AC Milan": "AC米兰",
    "Milan": "AC米兰",
    "Juventus FC": "尤文图斯",
    "Juventus": "尤文图斯",
    "SSC Napoli": "那不勒斯",
    "Napoli": "那不勒斯",
    "AS Roma": "罗马",
    "Atalanta BC": "亚特兰大",
    "Paris Saint-Germain FC": "巴黎圣日耳曼",
    "PSG": "巴黎圣日耳曼",
    "Olympique de Marseille": "马赛",
    "AS Monaco FC": "摩纳哥",
    "Olympique Lyonnais": "里昂",
}

STATUS_ZH_MAP: dict[str, str] = {
    "TIMED": "未开赛",
    "SCHEDULED": "待定开球",
    "LIVE": "进行中",
    "IN_PLAY": "进行中",
    "PAUSED": "中场休息",
    "FINISHED": "已完赛",
    "POSTPONED": "已延期",
    "SUSPENDED": "已暂停",
    "CANCELLED": "已取消",
}


@dataclasses.dataclass(frozen=True)
class NormalizedMatch:
    id: str
    competitionCode: str
    competitionName: str
    competitionZhName: str
    matchday: int | None
    stage: str
    utcDate: str
    status: str
    homeTeamId: int
    homeTeamName: str
    homeTeamShortName: str
    homeTeamZhName: str
    awayTeamId: int
    awayTeamName: str
    awayTeamShortName: str
    awayTeamZhName: str
    homeScore: int | None
    awayScore: int | None
    venue: str
    lastUpdatedUtc: str

    def to_dict(self) -> dict[str, Any]:
        return dataclasses.asdict(self)


def localize_team(name: str, short_name: str | None = None) -> str:
    """Return Chinese team name if mapped, otherwise fall back to short/full name."""
    if name in TEAM_ZH_MAP:
        return TEAM_ZH_MAP[name]
    if short_name and short_name in TEAM_ZH_MAP:
        return TEAM_ZH_MAP[short_name]
    return short_name or name


def parse_utc_iso(iso_str: str) -> dt.datetime:
    """Parse an ISO-8601 UTC timestamp string into an aware UTC datetime."""
    cleaned = iso_str.strip()
    if cleaned.endswith("Z"):
        cleaned = cleaned[:-1] + "+00:00"
    parsed = dt.datetime.fromisoformat(cleaned)
    if parsed.tzinfo is None:
        return parsed.replace(tzinfo=dt.timezone.utc)
    return parsed.astimezone(dt.timezone.utc)


def format_utc_iso(ts: dt.datetime) -> str:
    return ts.astimezone(dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def normalize_api_match(raw: dict[str, Any], fallback_code: str) -> NormalizedMatch:
    """Convert one football-data.org v4 match JSON object into NormalizedMatch."""
    comp = raw.get("competition") or {}
    comp_code = (comp.get("code") or fallback_code or "OTHER").upper()
    meta = COMPETITION_META.get(comp_code, {})
    comp_name = comp.get("name") or meta.get("name") or comp_code
    comp_zh = meta.get("zhName") or comp_name

    home = raw.get("homeTeam") or {}
    away = raw.get("awayTeam") or {}
    home_name = home.get("name") or "TBD"
    home_short = home.get("shortName") or home.get("tla") or home_name
    away_name = away.get("name") or "TBD"
    away_short = away.get("shortName") or away.get("tla") or away_name

    score_full = (raw.get("score") or {}).get("fullTime") or {}
    utc_raw = raw.get("utcDate") or format_utc_iso(dt.datetime.now(dt.timezone.utc))
    last_updated = raw.get("lastUpdated") or utc_raw

    return NormalizedMatch(
        id=f"fd-{raw.get('id')}",
        competitionCode=comp_code,
        competitionName=comp_name,
        competitionZhName=comp_zh,
        matchday=raw.get("matchday"),
        stage=raw.get("stage") or "REGULAR_SEASON",
        utcDate=format_utc_iso(parse_utc_iso(utc_raw)),
        status=(raw.get("status") or "SCHEDULED").upper(),
        homeTeamId=int(home.get("id") or 0),
        homeTeamName=home_name,
        homeTeamShortName=home_short,
        homeTeamZhName=localize_team(home_name, home_short),
        awayTeamId=int(away.get("id") or 0),
        awayTeamName=away_name,
        awayTeamShortName=away_short,
        awayTeamZhName=localize_team(away_name, away_short),
        homeScore=score_full.get("home"),
        awayScore=score_full.get("away"),
        venue=raw.get("venue") or "",
        lastUpdatedUtc=format_utc_iso(parse_utc_iso(last_updated)),
    )


def fetch_competition_matches(
    api_key: str,
    competition_code: str,
    date_from: str,
    date_to: str,
    timeout_seconds: int = 20,
) -> list[NormalizedMatch]:
    """Fetch matches for one competition from football-data.org v4."""
    params = urllib.parse.urlencode({"dateFrom": date_from, "dateTo": date_to})
    url = f"https://api.football-data.org/v4/competitions/{competition_code}/matches?{params}"
    req = urllib.request.Request(
        url,
        headers={
            "X-Auth-Token": api_key,
            "User-Agent": "FootballCalendarSync/1.0",
        },
    )
    with urllib.request.urlopen(req, timeout=timeout_seconds) as resp:
        payload = json.loads(resp.read().decode("utf-8"))
    raw_matches = payload.get("matches") or []
    return [normalize_api_match(m, competition_code) for m in raw_matches]


def generate_demo_matches(anchor_utc: dt.datetime | None = None) -> list[NormalizedMatch]:
    """Generate deterministic demo fixtures around anchor_utc when no API key is configured."""
    now = (anchor_utc or dt.datetime.now(dt.timezone.utc)).replace(
        minute=0, second=0, microsecond=0
    )
    base_day = dt.datetime(now.year, now.month, now.day, tzinfo=dt.timezone.utc)
    now_iso = format_utc_iso(now)

    templates: list[tuple[str, str, int, int, int, str, str, str, int | None, int | None, str]] = [
        # (id, comp, day_offset, utc_hour, utc_min, home, away, status, home_score, away_score, venue)
        ("demo-1001", "PL", -2, 16, 30, "Arsenal FC", "Chelsea FC", "FINISHED", 2, 1, "Emirates Stadium"),
        ("demo-1002", "PD", -1, 20, 0, "Real Madrid CF", "FC Barcelona", "FINISHED", 3, 2, "Santiago Bernabéu"),
        ("demo-1003", "PL", 0, 12, 30, "Liverpool FC", "Manchester City FC", "TIMED", None, None, "Anfield"),
        ("demo-1004", "BL1", 0, 17, 30, "FC Bayern München", "Borussia Dortmund", "TIMED", None, None, "Allianz Arena"),
        ("demo-1005", "SA", 0, 19, 45, "FC Internazionale Milano", "AC Milan", "TIMED", None, None, "San Siro"),
        ("demo-1006", "CL", 1, 20, 0, "Paris Saint-Germain FC", "Real Madrid CF", "TIMED", None, None, "Parc des Princes"),
        ("demo-1007", "CL", 1, 20, 0, "Arsenal FC", "FC Bayern München", "TIMED", None, None, "Emirates Stadium"),
        ("demo-1008", "PL", 2, 15, 0, "Manchester United FC", "Tottenham Hotspur FC", "TIMED", None, None, "Old Trafford"),
        ("demo-1009", "PD", 2, 19, 0, "Club Atlético de Madrid", "FC Barcelona", "TIMED", None, None, "Cívitas Metropolitano"),
        ("demo-1010", "SA", 3, 19, 45, "Juventus FC", "SSC Napoli", "TIMED", None, None, "Allianz Stadium"),
        ("demo-1011", "FL1", 4, 19, 0, "Olympique de Marseille", "Paris Saint-Germain FC", "TIMED", None, None, "Stade Vélodrome"),
        ("demo-1012", "BL1", 5, 17, 30, "Bayer 04 Leverkusen", "RB Leipzig", "POSTPONED", None, None, "BayArena"),
        ("demo-1013", "PL", 6, 16, 30, "Chelsea FC", "Liverpool FC", "TIMED", None, None, "Stamford Bridge"),
        ("demo-1014", "CL", 8, 20, 0, "Manchester City FC", "FC Internazionale Milano", "SCHEDULED", None, None, "Etihad Stadium"),
    ]

    result: list[NormalizedMatch] = []
    for idx, (
        match_id,
        comp_code,
        day_offset,
        hour,
        minute,
        home_name,
        away_name,
        status,
        h_score,
        a_score,
        venue,
    ) in enumerate(templates, start=1):
        meta = COMPETITION_META[comp_code]
        kickoff = base_day + dt.timedelta(days=day_offset, hours=hour, minutes=minute)
        result.append(
            NormalizedMatch(
                id=match_id,
                competitionCode=comp_code,
                competitionName=meta["name"],
                competitionZhName=meta["zhName"],
                matchday=10 + (idx % 5),
                stage="GROUP_STAGE" if comp_code == "CL" else "REGULAR_SEASON",
                utcDate=format_utc_iso(kickoff),
                status=status,
                homeTeamId=idx * 10 + 1,
                homeTeamName=home_name,
                homeTeamShortName=home_name.replace(" FC", "").replace(" CF", ""),
                homeTeamZhName=localize_team(home_name),
                awayTeamId=idx * 10 + 2,
                awayTeamName=away_name,
                awayTeamShortName=away_name.replace(" FC", "").replace(" CF", ""),
                awayTeamZhName=localize_team(away_name),
                homeScore=h_score,
                awayScore=a_score,
                venue=venue,
                lastUpdatedUtc=now_iso,
            )
        )
    return result


def ics_escape(value: str) -> str:
    """Escape special characters according to RFC 5545 text rules."""
    return (
        value.replace("\\", "\\\\")
        .replace("\r\n", "\\n")
        .replace("\n", "\\n")
        .replace(",", "\\,")
        .replace(";", "\\;")
    )


def fold_ics_line(line: str, limit: int = 75) -> str:
    """Fold an iCalendar line cleanly at octets/characters boundary."""
    if len(line) <= limit:
        return line
    chunks = [line[:limit]]
    pos = limit
    while pos < len(line):
        chunks.append(" " + line[pos : pos + limit - 1])
        pos += limit - 1
    return "\r\n".join(chunks)


def build_ics(matches: list[NormalizedMatch], calendar_name: str, generated_at: dt.datetime) -> str:
    """Build an RFC 5545 iCalendar string from normalized matches."""
    stamp = generated_at.astimezone(dt.timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    lines = [
        "BEGIN:VCALENDAR",
        "VERSION:2.0",
        "PRODID:-//Football Calendar//Auto Sync Feed//ZH",
        "CALSCALE:GREGORIAN",
        "METHOD:PUBLISH",
        f"X-WR-CALNAME:{ics_escape(calendar_name)}",
        "X-WR-TIMEZONE:Asia/Shanghai",
        "REFRESH-INTERVAL;VALUE=DURATION:PT6H",
        "X-PUBLISHED-TTL:PT6H",
    ]

    for m in matches:
        start_dt = parse_utc_iso(m.utcDate)
        end_dt = start_dt + dt.timedelta(minutes=110)
        score_text = (
            f" ({m.homeScore}-{m.awayScore})"
            if m.homeScore is not None and m.awayScore is not None
            else ""
        )
        status_zh = STATUS_ZH_MAP.get(m.status, m.status)
        summary = f"[{m.competitionZhName}] {m.homeTeamZhName} vs {m.awayTeamZhName}{score_text}"
        desc = (
            f"赛事: {m.competitionZhName} ({m.competitionName})\\n"
            f"状态: {status_zh}\\n"
            f"对阵: {m.homeTeamName} vs {m.awayTeamName}"
        )
        ics_status = "CANCELLED" if m.status in {"CANCELLED", "POSTPONED"} else "CONFIRMED"
        event_lines = [
            "BEGIN:VEVENT",
            f"UID:{m.id}@football-calendar",
            f"DTSTAMP:{stamp}",
            f"DTSTART:{start_dt.strftime('%Y%m%dT%H%M%SZ')}",
            f"DTEND:{end_dt.strftime('%Y%m%dT%H%M%SZ')}",
            f"SUMMARY:{ics_escape(summary)}",
            f"DESCRIPTION:{desc}",
            f"STATUS:{ics_status}",
            f"CATEGORIES:{ics_escape(m.competitionZhName)}",
        ]
        if m.venue:
            event_lines.append(f"LOCATION:{ics_escape(m.venue)}")
        event_lines.append("END:VEVENT")
        lines.extend(fold_ics_line(line) for line in event_lines)

    lines.append("END:VCALENDAR")
    return "\r\n".join(lines) + "\r\n"


def build_index_html(
    matches: list[NormalizedMatch],
    mode: str,
    generated_at: dt.datetime,
    competitions: list[str],
) -> str:
    """Build a clean, self-contained HTML dashboard for GitHub Pages."""
    bj_tz = dt.timezone(dt.timedelta(hours=8))
    gen_bj = generated_at.astimezone(bj_tz).strftime("%Y-%m-%d %H:%M UTC+8")
    mode_badge = (
        "Live API 实时同步模式 (football-data.org)"
        if mode == "live_api"
        else "演示赛程模式（配置 FOOTBALL_DATA_API_KEY Secret 后自动切换真实赛程）"
    )

    cards_html: list[str] = []
    for m in matches[:60]:
        kickoff_bj = parse_utc_iso(m.utcDate).astimezone(bj_tz)
        date_str = kickoff_bj.strftime("%m-%d %H:%M")
        early_tag = '<span class="badge early">凌晨场</span>' if 0 <= kickoff_bj.hour < 6 else ""
        score_display = (
            f"{m.homeScore} : {m.awayScore}"
            if m.homeScore is not None and m.awayScore is not None
            else "VS"
        )
        status_zh = STATUS_ZH_MAP.get(m.status, m.status)
        status_cls = "postponed" if m.status in {"POSTPONED", "CANCELLED"} else "normal"
        cards_html.append(
            f"""
            <article class="card" data-comp="{html.escape(m.competitionCode)}">
              <div class="card-top">
                <span class="league">{html.escape(m.competitionZhName)}</span>
                <span class="time">{html.escape(date_str)} (北京时间) {early_tag}</span>
              </div>
              <div class="teams">
                <span class="team home">{html.escape(m.homeTeamZhName)}</span>
                <span class="score">{html.escape(score_display)}</span>
                <span class="team away">{html.escape(m.awayTeamZhName)}</span>
              </div>
              <div class="card-foot">
                <span class="status {status_cls}">{html.escape(status_zh)}</span>
                <span class="venue">{html.escape(m.venue or m.competitionName)}</span>
              </div>
            </article>
            """
        )

    ics_links = " ".join(
        f'<a class="chip" href="./ics/{html.escape(c)}.ics">{html.escape(COMPETITION_META.get(c, {}).get("zhName", c))}.ics</a>'
        for c in competitions
    )

    return f"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>足球赛事日历 · 自动同步订阅中心</title>
  <style>
    :root {{
      --bg: #0b1310;
      --surface: #13201b;
      --border: #21382e;
      --text: #ecf5f0;
      --muted: #9ab3a7;
      --accent: #22c55e;
      --warn: #f59e0b;
      --danger: #ef4444;
    }}
    * {{ box-sizing: border-box; }}
    body {{
      margin: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      background: radial-gradient(circle at top, #13281e, var(--bg) 55%);
      color: var(--text); line-height: 1.5; padding: 24px 16px 48px;
    }}
    .wrap {{ max-width: 900px; margin: 0 auto; }}
    header {{
      background: var(--surface); border: 1px solid var(--border); border-radius: 16px;
      padding: 20px; margin-bottom: 20px;
    }}
    h1 {{ margin: 0 0 8px; font-size: 1.5rem; }}
    .sub {{ color: var(--muted); font-size: 0.92rem; margin: 0 0 12px; }}
    .pill {{
      display: inline-block; padding: 4px 10px; border-radius: 999px; font-size: 0.8rem;
      background: rgba(34, 197, 94, 0.14); color: var(--accent); border: 1px solid rgba(34, 197, 94, 0.3);
    }}
    .actions {{ display: flex; flex-wrap: wrap; gap: 10px; margin-top: 14px; }}
    .btn, .chip {{
      text-decoration: none; color: var(--text); background: #1a2d25; border: 1px solid var(--border);
      padding: 8px 12px; border-radius: 10px; font-size: 0.86rem; cursor: pointer;
    }}
    .btn.primary {{ background: var(--accent); color: #052010; font-weight: 600; border-color: var(--accent); }}
    .filters {{ display: flex; flex-wrap: wrap; gap: 8px; margin: 14px 0 18px; }}
    .filters button {{
      background: var(--surface); color: var(--muted); border: 1px solid var(--border);
      padding: 6px 12px; border-radius: 999px; cursor: pointer; font-size: 0.85rem;
    }}
    .filters button.active {{ background: var(--accent); color: #052010; font-weight: 600; border-color: var(--accent); }}
    .grid {{ display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 12px; }}
    .card {{
      background: var(--surface); border: 1px solid var(--border); border-radius: 14px;
      padding: 14px; display: flex; flex-direction: column; gap: 10px;
    }}
    .card-top, .card-foot {{ display: flex; justify-content: space-between; align-items: center; font-size: 0.8rem; color: var(--muted); }}
    .league {{ font-weight: 600; color: var(--accent); }}
    .teams {{ display: flex; align-items: center; justify-content: space-between; gap: 8px; font-weight: 600; font-size: 1rem; }}
    .team {{ flex: 1; }}
    .team.away {{ text-align: right; }}
    .score {{ padding: 2px 10px; border-radius: 8px; background: #0f1915; border: 1px solid var(--border); font-variant-numeric: tabular-nums; }}
    .badge.early {{ background: rgba(245, 158, 11, 0.16); color: var(--warn); padding: 1px 6px; border-radius: 6px; margin-left: 4px; }}
    .status.postponed {{ color: var(--danger); font-weight: 600; }}
    code {{ background: #0f1915; padding: 2px 6px; border-radius: 6px; font-size: 0.82rem; }}
  </style>
</head>
<body>
  <div class="wrap">
    <header>
      <h1>⚽ 足球赛事日历 · 自动更新订阅源</h1>
      <p class="sub">最近更新：{html.escape(gen_bj)} · 共 {len(matches)} 场近期/未来赛程</p>
      <span class="pill">{html.escape(mode_badge)}</span>
      <div class="actions">
        <a class="btn primary" href="./football.ics">📅 订阅全部赛程 (.ics)</a>
        <a class="btn" href="./fixtures.json">🔗 查看 JSON 接口 (fixtures.json)</a>
        <button class="btn" onclick="copyFeedUrl()">📋 复制 App 订阅地址</button>
      </div>
      <div class="actions" style="margin-top:10px; align-items:center;">
        <span style="font-size:0.82rem;color:var(--muted);">单联赛日历：</span>
        {ics_links}
      </div>
      <p class="sub" style="margin-top:10px;">
        安卓 App 接入方式：打开 App → 设置 → 数据源选择 <strong>GitHub Pages 订阅源</strong> → 填入本页地址或 <code>fixtures.json</code> 完整地址即可后台自动同步。
      </p>
    </header>

    <div class="filters" id="filters">
      <button class="active" data-filter="ALL">全部赛事</button>
      {"".join(f'<button data-filter="{html.escape(c)}">{html.escape(COMPETITION_META.get(c, {}).get("zhName", c))}</button>' for c in competitions)}
    </div>

    <section class="grid" id="cards">
      {"".join(cards_html)}
    </section>
  </div>
  <script>
    function copyFeedUrl() {{
      const url = new URL('./fixtures.json', window.location.href).href;
      navigator.clipboard.writeText(url).then(() => alert('已复制订阅地址：' + url));
    }}
    document.querySelectorAll('#filters button').forEach(btn => {{
      btn.addEventListener('click', () => {{
        document.querySelectorAll('#filters button').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        const target = btn.dataset.filter;
        document.querySelectorAll('#cards .card').forEach(card => {{
          card.style.display = (target === 'ALL' || card.dataset.comp === target) ? 'flex' : 'none';
        }});
      }});
    }});
  </script>
</body>
</html>
"""


def write_feed_outputs(
    output_dir: pathlib.Path,
    matches: list[NormalizedMatch],
    mode: str,
    competitions: list[str],
    generated_at: dt.datetime | None = None,
) -> dict[str, pathlib.Path]:
    """Write fixtures.json, football.ics, per-competition .ics files, and index.html."""
    now = (generated_at or dt.datetime.now(dt.timezone.utc)).astimezone(dt.timezone.utc)
    sorted_matches = sorted(matches, key=lambda m: (m.utcDate, m.competitionCode, m.id))

    output_dir.mkdir(parents=True, exist_ok=True)
    ics_dir = output_dir / "ics"
    ics_dir.mkdir(parents=True, exist_ok=True)

    payload = {
        "schemaVersion": 1,
        "generatedAtUtc": format_utc_iso(now),
        "mode": mode,
        "competitions": [
            {
                "code": c,
                "name": COMPETITION_META.get(c, {}).get("name", c),
                "zhName": COMPETITION_META.get(c, {}).get("zhName", c),
                "colorHex": COMPETITION_META.get(c, {}).get("color", "#1E88E5"),
            }
            for c in competitions
        ],
        "matches": [m.to_dict() for m in sorted_matches],
    }

    fixtures_path = output_dir / "fixtures.json"
    fixtures_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")

    ics_path = output_dir / "football.ics"
    ics_path.write_text(build_ics(sorted_matches, "足球赛事日历 (全部)", now), encoding="utf-8")

    for code in competitions:
        comp_matches = [m for m in sorted_matches if m.competitionCode == code]
        comp_zh = COMPETITION_META.get(code, {}).get("zhName", code)
        (ics_dir / f"{code}.ics").write_text(
            build_ics(comp_matches, f"足球赛事 - {comp_zh}", now), encoding="utf-8"
        )

    index_path = output_dir / "index.html"
    index_path.write_text(build_index_html(sorted_matches, mode, now, competitions), encoding="utf-8")

    return {"fixtures_json": fixtures_path, "football_ics": ics_path, "index_html": index_path}


def collect_matches(
    api_key: str | None,
    competitions: list[str],
    days_back: int = 3,
    days_ahead: int = 21,
    rate_limit_sleep: float = 6.2,
) -> tuple[list[NormalizedMatch], str]:
    """Fetch live API matches if api_key is given, falling back to demo fixtures on empty/error."""
    now = dt.datetime.now(dt.timezone.utc)
    if not api_key:
        return generate_demo_matches(now), "demo_fallback"

    date_from = (now - dt.timedelta(days=days_back)).strftime("%Y-%m-%d")
    date_to = (now + dt.timedelta(days=days_ahead)).strftime("%Y-%m-%d")

    collected: dict[str, NormalizedMatch] = {}
    errors: list[str] = []

    for idx, code in enumerate(competitions):
        if idx > 0 and rate_limit_sleep > 0:
            time.sleep(rate_limit_sleep)
        try:
            for match in fetch_competition_matches(api_key, code, date_from, date_to):
                collected[match.id] = match
        except urllib.error.HTTPError as exc:
            errors.append(f"{code}: HTTP {exc.code}")
            if exc.code == 429:
                time.sleep(max(rate_limit_sleep, 10.0))
        except Exception as exc:  # noqa: BLE001
            errors.append(f"{code}: {exc}")

    if not collected:
        print(f"[warn] No live matches fetched ({errors}); falling back to demo feed.", file=sys.stderr)
        return generate_demo_matches(now), "demo_fallback"

    if errors:
        print(f"[warn] Partial competition fetch warnings: {errors}", file=sys.stderr)
    return list(collected.values()), "live_api"


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Generate Football Calendar JSON/ICS/Pages outputs.")
    parser.add_argument("--output-dir", default="_site", help="Output directory for generated assets.")
    parser.add_argument(
        "--competitions",
        default=",".join(DEFAULT_COMPETITIONS),
        help="Comma-separated competition codes (e.g. PL,PD,BL1,SA,FL1,CL).",
    )
    parser.add_argument("--days-back", type=int, default=3, help="Days in the past to include.")
    parser.add_argument("--days-ahead", type=int, default=21, help="Days in the future to include.")
    parser.add_argument("--sleep", type=float, default=6.2, help="Delay between competition API calls.")
    args = parser.parse_args(argv)

    competitions = [c.strip().upper() for c in args.competitions.split(",") if c.strip()]
    api_key = os.environ.get("FOOTBALL_DATA_API_KEY", "").strip() or None

    matches, mode = collect_matches(
        api_key=api_key,
        competitions=competitions,
        days_back=args.days_back,
        days_ahead=args.days_ahead,
        rate_limit_sleep=args.sleep,
    )
    outputs = write_feed_outputs(
        output_dir=pathlib.Path(args.output_dir),
        matches=matches,
        mode=mode,
        competitions=competitions,
    )
    print(f"Generated {len(matches)} matches in mode={mode}: {outputs['fixtures_json']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
