#!/usr/bin/env python3
"""Drive the installed Football Calendar v2.6.0 APK on Android Emulator via ADB and capture every screen."""

from __future__ import annotations

import argparse
import os
import pathlib
import re
import subprocess
import sys
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
        booted = adb("shell", "getprop", "sys.boot_completed").strip()
        if booted == "1":
            break
        time.sleep(2)
    # Try setting Beijing timezone if allowed (non-fatal on userdebug/release builds)
    adb("shell", "settings", "put", "global", "auto_time_zone", "0", check=False)
    adb("shell", "service", "call", "alarm", "3", "s16", "Asia/Shanghai", check=False)


def get_screen_size() -> tuple[int, int]:
    out = adb("shell", "wm", "size")
    m = re.search(r"(\d+)x(\d+)", str(out))
    return (int(m.group(1)), int(m.group(2))) if m else (1080, 2400)


def dump_nodes() -> list[dict[str, object]]:
    for attempt in range(3):
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
                return nodes
            except ET.ParseError:
                pass
        time.sleep(0.7 * (attempt + 1))
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
) -> dict[str, object] | None:
    for n in nodes:
        cy = int(n["cy"])
        if not (min_y <= cy <= max_y):
            continue
        t = str(n["text"]).strip()
        d = str(n["desc"]).strip()
        if exact is not None and (t == exact or d == exact):
            return n
        if contains is not None and (contains in t or contains in d):
            return n
    return None


def tap_match(
    *,
    exact: str | None = None,
    contains: str | None = None,
    min_y: int = 0,
    max_y: int = 99999,
    wait_s: float = 2.0,
) -> bool:
    nodes = dump_nodes()
    n = find_node(nodes, exact=exact, contains=contains, min_y=min_y, max_y=max_y)
    if not n:
        sample = [f"{x['text']}@{x['cy']}" for x in nodes if str(x["text"]).strip()][:28]
        print(f"[warn] node not found exact={exact!r} contains={contains!r} y=[{min_y},{max_y}]. sample={sample}")
        return False
    cx, cy = int(n["cx"]), int(n["cy"])
    print(f"[tap] {n['text'] or n['desc']!r} at ({cx}, {cy})")
    adb("shell", "input", "tap", str(cx), str(cy))
    time.sleep(wait_s)
    return True


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


def tap_any(candidates: list[str], wait_s: float = 2.5, min_y: int = 0, max_y: int = 99999) -> bool:
    nodes = dump_nodes()
    for c in candidates:
        n = find_node(nodes, contains=c, min_y=min_y, max_y=max_y)
        if n:
            print(f"[tap_any] matched {c!r} -> ({n['cx']}, {n['cy']})")
            adb("shell", "input", "tap", str(n["cx"]), str(n["cy"]))
            time.sleep(wait_s)
            return True
    return False


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

    adb("install", "-r", "-g", args.apk)
    for perm in (
        "android.permission.POST_NOTIFICATIONS",
        "android.permission.READ_CALENDAR",
        "android.permission.WRITE_CALENDAR",
    ):
        adb("shell", "pm", "grant", PKG, perm, check=False)

    adb("shell", "am", "start", "-n", ACTIVITY)
    time.sleep(9.0)
    dismiss_system_dialogs()
    time.sleep(2.0)

    # 1. Home Calendar Overview (countdown hero card + calendar grid)
    screencap(out_dir, "01_calendar_home.png")

    # 2. Switch calendar date to 26 (rich finished UCL/UEL/BSA matches!) and scroll down to cards
    tap_match(exact="26", min_y=int(h * 0.25), max_y=int(h * 0.78), wait_s=1.8)
    swipe_up(w, h, dist_ratio=0.42, wait_s=1.5)
    screencap(out_dir, "02_calendar_day_matches.png")

    # 3. Tap a finished rich match card on Home Calendar -> Blurred Popup (图二: 赛况时间轴)
    opened_match = tap_any(
        ["水晶宫", "霍芬海姆", "尤文图斯", "弗赖堡", "塞尔塔", "已完赛", "LIVE"],
        wait_s=3.5,
        min_y=int(h * 0.12),
        max_y=int(h * 0.88),
    )
    if not opened_match:
        # Fallback: tap middle area where first card lives
        adb("shell", "input", "tap", str(w // 2), str(int(h * 0.58)))
        time.sleep(3.5)
    screencap(out_dir, "03_match_popup_events.png")

    # 4. Switch to 图文直播 inside 赛况 Tab
    if tap_match(contains="图文直播", min_y=int(h * 0.15), max_y=int(h * 0.55), wait_s=1.5):
        screencap(out_dir, "04_match_popup_commentary.png")

    # 5. Switch to 阵容 Tab -> 绿色球场战术阵型图 (图一!)
    if tap_match(exact="阵容", min_y=int(h * 0.08), max_y=int(h * 0.45), wait_s=3.0):
        screencap(out_dir, "05_match_lineup_pitch.png")
        swipe_up(w, h, dist_ratio=0.48, wait_s=1.5)
        screencap(out_dir, "06_match_lineup_bench.png")

    # 6. Switch to 统计 Tab -> 球队统计 & 球员数据 (图三!)
    if tap_match(exact="统计", min_y=int(h * 0.08), max_y=int(h * 0.45), wait_s=2.0):
        screencap(out_dir, "07_match_stats_team.png")
        if tap_match(exact="球员数据", min_y=int(h * 0.15), max_y=int(h * 0.55), wait_s=2.0):
            screencap(out_dir, "08_match_stats_players.png")

    # 7. Switch to 交锋积分 Tab
    if tap_match(exact="交锋积分", min_y=int(h * 0.08), max_y=int(h * 0.45), wait_s=2.8):
        screencap(out_dir, "09_match_h2h_standings.png")

    # Dismiss popup modal cleanly
    if not tap_match(exact="关闭", max_y=int(h * 0.35), wait_s=1.2):
        adb("shell", "input", "keyevent", "KEYCODE_BACK")
        time.sleep(1.5)

    # 8. Standings Tab (积分榜)
    if tap_match(exact="积分榜", min_y=int(h * 0.85), wait_s=2.5):
        screencap(out_dir, "10_standings.png")

        # Switch to 西甲 (La Liga) so we capture Real Madrid CF (direct comparison for screenshot #4!)
        tap_match(exact="西甲", min_y=int(h * 0.08), max_y=int(h * 0.30), wait_s=1.8)

        # 9. Tap club row -> Fullscreen Team Detail Page (图四!)
        if tap_any(["皇家马德里", "巴塞罗那", "阿森纳", "利物浦"], wait_s=4.0, min_y=int(h * 0.25), max_y=int(h * 0.85)):
            screencap(out_dir, "11_team_detail_fullscreen.png")
            swipe_up(w, h, dist_ratio=0.52, wait_s=2.0)
            screencap(out_dir, "12_team_detail_squad.png")

            # Scroll back up slightly and tap 查看全部 on history or upcoming
            swipe_down(w, h, dist_ratio=0.35, wait_s=1.2)
            if tap_match(contains="查看全部", min_y=int(h * 0.25), max_y=int(h * 0.85), wait_s=1.8):
                screencap(out_dir, "13_team_history_all.png")
                tap_match(contains="返回球队档案", max_y=int(h * 0.20), wait_s=1.2)

            # Tap closes fullscreen team overlay
            if not tap_match(exact="关闭", max_y=int(h * 0.20), wait_s=1.2):
                adb("shell", "input", "keyevent", "KEYCODE_BACK")
                time.sleep(1.2)

    # 10. Players Tab (球员榜)
    if tap_match(exact="球员榜", min_y=int(h * 0.85), wait_s=3.0):
        screencap(out_dir, "14_players_leaderboard.png")

        # 11. Tap first player card -> Fullscreen Player Detail Page!
        swipe_up(w, h, dist_ratio=0.22, wait_s=1.2)
        if tap_any(["哈兰德", "姆巴佩", "凯恩", "萨拉赫", "维尼修斯", "No.1"], wait_s=3.5, min_y=int(h * 0.30), max_y=int(h * 0.86)):
            screencap(out_dir, "15_player_detail_fullscreen.png")
            swipe_up(w, h, dist_ratio=0.45, wait_s=1.5)
            screencap(out_dir, "16_player_detail_schedule_history.png")
            if tap_match(contains="查看全部", min_y=int(h * 0.25), max_y=int(h * 0.88), wait_s=1.5):
                screencap(out_dir, "17_player_history_all.png")
            if not tap_match(exact="关闭", max_y=int(h * 0.20), wait_s=1.2):
                adb("shell", "input", "keyevent", "KEYCODE_BACK")
                time.sleep(1.2)
                adb("shell", "input", "keyevent", "KEYCODE_BACK")
                time.sleep(1.2)

        # Switch to 主队档案 subtab inside 球员榜
        if tap_match(contains="主队档案", min_y=int(h * 0.08), max_y=int(h * 0.35), wait_s=2.0):
            screencap(out_dir, "18_players_fav_teams.png")

    # 12. Settings Tab (设置) + Trigger In-App Live Broadcast Banner!
    if tap_match(exact="设置", min_y=int(h * 0.85), wait_s=2.0):
        screencap(out_dir, "19_settings.png")
        swipe_up(w, h, dist_ratio=0.52, wait_s=1.2)
        if not tap_match(contains="测试实时播报", min_y=int(h * 0.15), max_y=int(h * 0.90), wait_s=1.5):
            swipe_up(w, h, dist_ratio=0.45, wait_s=1.2)
            screencap(out_dir, "20_settings_notifications.png")
            if tap_match(contains="测试实时播报", min_y=int(h * 0.15), max_y=int(h * 0.90), wait_s=1.5):
                dismiss_system_dialogs()
                time.sleep(0.8)
                screencap(out_dir, "21_live_broadcast_banner.png")
        else:
            screencap(out_dir, "20_settings_notifications.png")
            dismiss_system_dialogs()
            time.sleep(0.8)
            screencap(out_dir, "21_live_broadcast_banner.png")

    shots = sorted(p.name for p in out_dir.glob("*.png"))
    print(f"[done] captured {len(shots)} screenshots: {shots}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
