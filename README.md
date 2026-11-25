# ⚽ 足球赛事日历 Android App + GitHub 全托管自动同步源

一套开箱即用的**自动更新足球赛事日历**解决方案，包含：
1. **原生 Android App**（Kotlin + Jetpack Compose + Material 3 + Room + WorkManager）
2. **GitHub Actions 云端自动编译 APK**（无需本地配置 Android Studio 即可生成安装包）
3. **GitHub Actions + GitHub Pages 自动更新赛程订阅服务**（每 6 小时自动抓取/更新赛程，输出 `fixtures.json`、`football.ics` 及网页看板）

---

## 核心功能

- **多视图赛事日历**：月历 / 周历切换，日历格点标注比赛场次与主队星标（琥珀色高亮），支持“观赛夜”分组（将次日 00:00–05:59 的欧洲凌晨赛事归入前夜并标注`次日凌晨`）。
- **后台自动更新与改期检测**：通过 `WorkManager` 按 6h / 12h / 24h 周期自动拉取最新赛程，内置 `ScheduleDiffEngine` 对比开球时间变更、延期（`POSTPONED`）、新增赛程及比分更新，及时推送通知。
- **三种数据源无缝切换**：
  - **演示模式**（默认开箱可用，支持在设置页一键模拟改期与赛果变化测试通知）；
  - **GitHub Pages 订阅源**（推荐：填入你的 GitHub Pages 地址即可免 Key 后台同步）；
  - **Football-Data.org 直连**（填入个人免费 API Key 直接拉取官方数据）。
- **系统日历集成与 `.ics` 导出**：支持单场一键加入手机系统日历、开启“自动托管同步到系统日历（改期自动修正）”，以及一键分享标准 `.ics` 文件。
- **桌面小组件**：提供 `下一场焦点战` 桌面小组件。

---

## GitHub 全托管部署指南（3 分钟完成）

### 第 1 步：推送代码到你的 GitHub 仓库
在 GitHub 新建一个空仓库（例如 `football-calendar-android`），然后在项目根目录执行：

```bash
git remote add origin https://github.com/<你的用户名>/football-calendar-android.git
git branch -M main
git push -u origin main
```

### 第 2 步：开启 GitHub Pages（托管自动更新赛程接口）
1. 进入 GitHub 仓库的 **Settings → Pages**。
2. 在 **Build and deployment → Source** 中选择 **GitHub Actions**。
3. 前往仓库的 **Actions** 页面，确认 **Update Football Schedule & Deploy GitHub Pages** 已运行成功。
4. 完成后即可访问你的专属赛程看板与订阅地址：
   - 网页看板：`https://<你的用户名>.github.io/football-calendar-android/`
   - 安卓端同步 JSON：`https://<你的用户名>.github.io/football-calendar-android/fixtures.json`
   - 通用日历订阅（苹果/谷歌/Outlook）：`https://<你的用户名>.github.io/football-calendar-android/football.ics`

> 💡 **切换为真实实时赛程（推荐）**：
> 前往 [football-data.org](https://www.football-data.org/) 免费注册获取 API Token，然后在仓库的 **Settings → Secrets and variables → Actions → New repository secret** 中添加：
> - Name: `FOOTBALL_DATA_API_KEY`
> - Value: 你的 API Token
>
> 添加后在 Actions 中手动触发一次 `Update Football Schedule & Deploy GitHub Pages`，此后每 6 小时自动更新五大联赛与欧冠真实赛程（未配置 Secret 时默认生成演示赛程以保证链路可用）。

### 第 3 步：下载并安装自动编译的 APK
1. 打开仓库 **Actions → Android CI & APK Build**，进入最近一次成功的构建。
2. 在页面底部 **Artifacts** 下载 `football-calendar-debug-apk`，解压得到 `app-debug.apk` 安装到安卓手机。
3. 打开 App → **同步设置** → 选择 **GitHub Pages 订阅源** → 填入第 2 步生成的 `https://<你的用户名>.github.io/football-calendar-android` → 点击 **保存并立即同步**。

---

## 目录结构

```text
├── .github/workflows/
│   ├── android-build.yml      # 自动单元测试 + 编译 APK + Tag 自动发 Release
│   └── update-feed.yml        # 每 6 小时更新赛程并部署 GitHub Pages
├── feed/
│   ├── generate_feed.py       # 纯 Python 标准库赛程抓取与 JSON/ICS/HTML 生成器
│   └── tests/                 # Python 单元测试
└── app/                       # 原生 Android 工程 (Kotlin + Jetpack Compose + Room + WorkManager)
```
