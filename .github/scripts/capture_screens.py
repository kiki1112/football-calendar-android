#!/usr/bin/env python3
"""Drive the Football Calendar APK on an Android Emulator via ADB and capture every screen."""

from __future__ import annotations

import argparse
import pathlib
import re
import subprocess
import time
import xml.etree.ElementTree as ET


PKG = "com.footballcalendar.app"
ACTIVITY = f"{PKG}/.MainActivity"
BOUNDS_RE = re.compile(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]")


def adb(*args: str, check: bool = True, binary: bool = False) -> bytes | str:
    cmd = ["adb", *args]
    res = subprocess.run(cmd, capture_output=True, check=check)
    return res.stdout if binary else res.stdout.decode("utf-8", errors="replace")


def wait_boot_and_time() -> None:
    adb("wait-for-device")
    for _ in range(90):
        booted = str(adb("shell", "getprop", "sys.boot_completed", check=False)).strip()
        if booted == "1":
            break
        time.sleep(2)
    adb("shell", "settings", "put", "global", "auto_time_zone", "0", check=False)
    adb("shell", "service", "call", "alarm", "3", "s16", "Asia/Shanghai", check=False)


def get_screen_size() -> tuple[int, int]:
    out = str(adb("shell", "wm", "size", check=False))
    m = re.search(r"(\d+)x(\d+)", out)
    return (int(m.group(1)), int(m.group(2))) if m else (1080, 2400)


def dump_nodes() -> list[dict[str, object]]:
    for attempt in range(4):
        adb("shell", "rm", "-f", "/data/local/tmp/uidump.xml", check=False)
        dump_out = str(
            adb("shell", "uiautomator", "dump", "--compressed", "/data/local/tmp/uidump.xml", check=False)
        ).strip()
        raw = str(adb("exec-out", "cat", "/data/local/tmp/uidump.xml", check=False))
        if "<hierarchy" not in raw:
            raw = str(adb("exec-out", "uiautomator", "dump", "/dev/tty", check=False))
        m = re.search(r"<hierarchy.*?</hierarchy>", raw, re.S)
        if m:
            try:
                root = ET.fromstring(m.group(0))
                nodes: list[dict[str, object]] = []
                for elem in root.iter("node"):
                    b = elem.attrib.get("bounds", "")
                    bm = BOUNDS_RE.match(b)
                    if not bm:
                        continue
                    x1, y1, x2, y2 = map(int, bm.groups())
                    nodes.append(
                        {
                            "text": elem.attrib.get("text", ""),
                            "desc": elem.attrib.get("content-desc", ""),
                            "clickable": elem.attrib.get("clickable") == "true",
                            "x1": x1,
                            "y1": y1,
                            "x2": x2,
                            "y2": y2,
                            "cx": (x1 + x2) // 2,
                            "cy": (y1 + y2) // 2,
                        }
                    )
                if nodes:
                    return nodes
            except ET.ParseError:
                pass
        print(f"[dump attempt {attempt + 1}] out={dump_out!r}")
        time.sleep(0.8 * (attempt + 1))
    return []


def dismiss_system_dialogs() -> None:
    for _ in range(2):
        nodes = dump_nodes()
        hit = None
        for n in nodes:
            txt = f"{n['text']} {n['desc']}".strip()
            if txt in ("Allow", "While using the app", "允许", "仅在使用中允许", "Wait", "OK", "确定"):
                hit = (int(n["cx"]), int(n["cy"]))
                break
        if hit:
            print(f"[dialog] tap {hit}")
            adb("shell", "input", "tap", str(hit[0]), str(hit[1]))
            time.sleep(1.0)
        else:
            break


def find_node(
    nodes: list[dict[str, object]],
    *,
    exact: str | None = None,
    contains: str | None = None,
    min_y: int = 0,
    max_y: int = 99999,
    min_x: int = 0,
    max_x: int = 99999,
    prefer_last: bool = False,
) -> dict[str, object] | None:
    matches: list[dict[str, object]] = []
    for n in nodes:
        cx = int(n["cx"])
        cy = int(n["cy"])
        if not (min_y <= cy <= max_y and min_x <= cx <= max_x):
            continue
        t = str(n["text"]).strip()
        d = str(n["desc"]).strip()
        if exact is not None and (t == exact or d == exact):
            matches.append(n)
        elif contains is not None and (contains in t or contains in d):
            matches.append(n)
    if not matches:
        return None
    return matches[-1] if prefer_last else matches[0]


def tap_xy(x: int, y: int, label: str = "", wait_s: float = 1.8) -> None:
    print(f"[tap_xy] {label} ({x}, {y})")
    adb("shell", "input", "tap", str(x), str(y))
    time.sleep(wait_s)


def tap_match(
    *,
    exact: str | None = None,
    contains: str | None = None,
    min_y: int = 0,
    max_y: int = 99999,
    min_x: int = 0,
    max_x: int = 99999,
    prefer_last: bool = False,
    wait_s: float = 2.0,
) -> bool:
    nodes = dump_nodes()
    n = find_node(
        nodes,
        exact=exact,
        contains=contains,
        min_y=min_y,
        max_y=max_y,
        min_x=min_x,
        max_x=max_x,
        prefer_last=prefer_last,
    )
    if not n:
        sample = [f"{(x['text'] or x['desc'])}@{x['cx']},{x['cy']}" for x in nodes if (str(x["text"]).strip() or str(x["desc"]).strip())][:35]
        print(f"[warn] node not found exact={exact!r} contains={contains!r} y=[{min_y},{max_y}]. sample={sample}")
        return False
    cx, cy = int(n["cx"]), int(n["cy"])
    print(f"[tap] {(n['text'] or n['desc'])!r} at ({cx}, {cy})")
    adb("shell", "input", "tap", str(cx), str(cy))
    time.sleep(wait_s)
    return True


def tap_any(
    candidates: list[str],
    *,
    wait_s: float = 2.5,
    min_y: int = 0,
    max_y: int = 99999,
) -> bool:
    nodes = dump_nodes()
    for c in candidates:
        n = find_node(nodes, contains=c, min_y=min_y, max_y=max_y)
        if n:
            print(f"[tap_any] matched {c!r} ({(n['text'] or n['desc'])!r}) -> ({n['cx']}, {n['cy']})")
            adb("shell", "input", "tap", str(n["cx"]), str(n["cy"]))
            time.sleep(wait_s)
            return True
    sample = [f"{(x['text'] or x['desc'])}@{x['cy']}" for x in nodes if (str(x["text"]).strip() or str(x["desc"]).strip())][:35]
    print(f"[warn] tap_any missed {candidates}. sample={sample}")
    return False


def swipe_up(w: int, h: int, dist_ratio: float = 0.45, wait_s: float = 1.2) -> None:
    x = w // 2
    y1 = int(h * 0.78)
    y2 = int(h * (0.78 - dist_ratio))
    adb("shell", "input", "swipe", str(x), str(y1), str(x), str(y2), "420")
    time.sleep(wait_s)


def swipe_down(w: int, h: int, dist_ratio: float = 0.45, wait_s: float = 1.2) -> None:
    x = w // 2
    y1 = int(h * 0.30)
    y2 = int(h * (0.30 + dist_ratio))
    adb("shell", "input", "swipe", str(x), str(y1), str(x), str(y2), "420")
    time.sleep(wait_s)


def screencap(out_dir: pathlib.Path, filename: str) -> None:
    data = adb("exec-out", "screencap", "-p", binary=True)
    path = out_dir / filename
    path.write_bytes(data if isinstance(data, bytes) else bytes(data))
    print(f"[shot] saved {path.name} ({len(data)} bytes)")


def tap_bottom_tab(index: int, label: str, w: int, h: int, wait_s: float = 2.5) -> None:
    if tap_match(exact=label, min_y=int(h * 0.84), wait_s=wait_s):
        return
    x = int(w * (0.125 + 0.25 * index))
    y = int(h * 0.925)
    tap_xy(x, y, f"bottom_tab_{label}", wait_s=wait_s)


def dismiss_modal(w: int, h: int, attempts: int = 1) -> None:
    for _ in range(attempts):
        if tap_match(exact="关闭", max_y=int(h * 0.45), wait_s=1.2):
            return
        nodes = dump_nodes()
        has_modal = any(
            str(n["text"]).strip().startswith("‹ 返回")
            or str(n["desc"]).strip() == "关闭"
            for n in nodes
        )
        if has_modal:
            print("[dismiss_modal] sending KEYCODE_BACK for active modal")
            adb("shell", "input", "keyevent", "KEYCODE_BACK")
            time.sleep(1.3)
        else:
            return


def ensure_app_foreground() -> None:
    top = str(adb("shell", "dumpsys", "activity", "activities", check=False))
    if PKG not in top:
        print("[fg] bringing MainActivity to foreground")
        adb("shell", "am", "start", "-n", ACTIVITY)
        time.sleep(2.5)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", required=True)
    parser.add_argument("--out", default="screenshots")
    args = parser.parse_args()

    out_dir = pathlib.Path(args.out)
    out_dir.mkdir(parents=True, exist_ok=True)

    wait_boot_and_time()
    w, h = get_screen_size()
    print(f"[info] screen size: {w}x{h}")

    adb("logcat", "-c", check=False)
    adb("install", "-r", "-g", args.apk)
    for perm in (
        "android.permission.POST_NOTIFICATIONS",
        "android.permission.READ_CALENDAR",
        "android.permission.WRITE_CALENDAR",
    ):
        adb("shell", "pm", "grant", PKG, perm, check=False)

    adb("shell", "am", "start", "-n", ACTIVITY)
    time.sleep(7.0)
    dismiss_system_dialogs()
    time.sleep(1.5)

    # 1. Home Calendar Overview (Hero countdown card + filter pills + calendar grid)
    screencap(out_dir, "01_calendar_home.png")

    # 2. Switch calendar date to 25 (Nov 25 Beijing Watch-Night: UCL Barcelona, Man Utd, Liverpool, Inter, PSG, Bayern — all with full lineups/events/stats/commentary!)
    if not tap_match(exact="25", min_y=int(h * 0.35), max_y=int(h * 0.90), prefer_last=True, wait_s=1.8):
        swipe_up(w, h, dist_ratio=0.18, wait_s=1.0)
        if not tap_match(exact="25", min_y=int(h * 0.30), max_y=int(h * 0.90), prefer_last=True, wait_s=1.8):
            tap_match(exact="26", min_y=int(h * 0.48), max_y=int(h * 0.88), prefer_last=True, wait_s=1.8)
    swipe_up(w, h, dist_ratio=0.48, wait_s=1.6)
    screencap(out_dir, "02_calendar_day_matches.png")

    # 3. Tap a rich finished UCL/UEL match card on Home Calendar -> Blurred Popup (图二: 赛况时间轴)
    opened_match = tap_any(
        ["曼联", "利物浦", "巴塞罗那", "国际米兰", "巴黎圣日耳曼", "拜仁慕尼黑", "水晶宫", "已完赛", "进行中"],
        wait_s=3.0,
        min_y=int(h * 0.12),
        max_y=int(h * 0.87),
    )
    if not opened_match:
        tap_xy(w // 2, int(h * 0.52), "fallback_match_card", wait_s=3.0)
    screencap(out_dir, "03_match_popup_events.png")

    # 4. Switch to 图文直播 inside 赛况 Tab
    if tap_match(contains="图文直播", min_y=int(h * 0.15), max_y=int(h * 0.65), wait_s=1.6):
        screencap(out_dir, "04_match_popup_commentary.png")

    # 5. Switch to 阵容 Tab -> 绿色球场战术阵型图 + 替补与主帅 (图一!)
    if tap_match(exact="阵容", min_y=int(h * 0.08), max_y=int(h * 0.50), wait_s=2.8):
        screencap(out_dir, "05_match_lineup_pitch.png")
        swipe_up(w, h, dist_ratio=0.48, wait_s=1.5)
        screencap(out_dir, "06_match_lineup_bench.png")

    # 6. Switch to 统计 Tab -> 球队统计 & 球员数据 (图三!)
    if tap_match(exact="统计", min_y=int(h * 0.08), max_y=int(h * 0.50), wait_s=2.0):
        screencap(out_dir, "07_match_stats_team.png")
        if tap_match(exact="球员数据", min_y=int(h * 0.15), max_y=int(h * 0.60), wait_s=2.0):
            screencap(out_dir, "08_match_stats_players.png")

    # 7. Switch to 交锋积分 Tab
    if tap_match(exact="交锋积分", min_y=int(h * 0.08), max_y=int(h * 0.50), wait_s=2.5):
        screencap(out_dir, "09_match_h2h_standings.png")

    # Dismiss popup modal back to Home Calendar
    dismiss_modal(w, h, attempts=1)
    ensure_app_foreground()

    # 8. Standings Tab (积分榜)
    tap_bottom_tab(1, "积分榜", w, h, wait_s=2.5)
    screencap(out_dir, "10_standings.png")

    # Switch to 西甲 (La Liga) so we capture Real Madrid CF -> 图四 Fullscreen Team Detail!
    tap_match(exact="西甲", min_y=int(h * 0.08), max_y=int(h * 0.35), wait_s=1.8)
    if tap_any(["皇家马德里", "巴塞罗那", "马德里竞技", "阿森纳", "利物浦"], wait_s=3.5, min_y=int(h * 0.25), max_y=int(h * 0.86)):
        screencap(out_dir, "11_team_detail_fullscreen.png")
        swipe_up(w, h, dist_ratio=0.52, wait_s=1.8)
        screencap(out_dir, "12_team_detail_squad.png")

        swipe_down(w, h, dist_ratio=0.35, wait_s=1.2)
        if tap_match(contains="查看全部", min_y=int(h * 0.25), max_y=int(h * 0.86), wait_s=1.8):
            screencap(out_dir, "13_team_history_all.png")
            tap_match(contains="返回球队档案", max_y=int(h * 0.25), wait_s=1.2)

        dismiss_modal(w, h, attempts=1)

    # 9. Players Tab (球员榜)
    ensure_app_foreground()
    tap_bottom_tab(2, "球员榜", w, h, wait_s=2.5)
    screencap(out_dir, "14_players_leaderboard.png")

    swipe_up(w, h, dist_ratio=0.22, wait_s=1.2)
    if tap_any(["姆巴佩", "哈兰德", "凯恩", "萨拉赫", "拉菲尼亚", "莱万多夫斯基", "No.1"], wait_s=3.5, min_y=int(h * 0.28), max_y=int(h * 0.86)):
        screencap(out_dir, "15_player_detail_fullscreen.png")
        swipe_up(w, h, dist_ratio=0.45, wait_s=1.5)
        screencap(out_dir, "16_player_detail_schedule_history.png")
        if tap_match(contains="查看全部", min_y=int(h * 0.25), max_y=int(h * 0.88), wait_s=1.6):
            screencap(out_dir, "17_player_history_all.png")
            tap_match(contains="返回球员档案", max_y=int(h * 0.25), wait_s=1.2)
        dismiss_modal(w, h, attempts=2)

    if tap_match(contains="主队档案", min_y=int(h * 0.08), max_y=int(h * 0.35), wait_s=2.0):
        screencap(out_dir, "18_players_fav_teams.png")

    # 10. Settings Tab (设置) + Trigger In-App Live Broadcast Banner!
    ensure_app_foreground()
    tap_bottom_tab(3, "设置", w, h, wait_s=2.0)
    screencap(out_dir, "19_settings.png")
    swipe_up(w, h, dist_ratio=0.55, wait_s=1.2)
    nodes = dump_nodes()
    if not find_node(nodes, contains="测试实时播报", min_y=int(h * 0.12), max_y=int(h * 0.88)):
        swipe_up(w, h, dist_ratio=0.40, wait_s=1.2)
    screencap(out_dir, "20_settings_notifications.png")
    if tap_match(contains="测试实时播报", min_y=int(h * 0.12), max_y=int(h * 0.90), wait_s=1.4):
        dismiss_system_dialogs()
        time.sleep(0.8)
        screencap(out_dir, "21_live_broadcast_banner.png")

    # Save filtered logcat for diagnostics
    log_text = str(adb("logcat", "-d", "-t", "800", check=False))
    (out_dir / "logcat.txt").write_text(log_text, encoding="utf-8", errors="replace")

    shots = sorted(p.name for p in out_dir.glob("*.png"))
    print(f"[done] captured {len(shots)} screenshots: {shots}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
