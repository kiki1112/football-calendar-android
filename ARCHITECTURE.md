# ARCHITECTURE.md — 个人极致版内核架构契约（小米 HyperOS + 0 元免费零错乱方案）

> **目标**：为单一小米 Android 设备打造 **2026 最前沿内核 + 零数据错乱/缺失/不同步 + 0 元起步可扩展**的足球比赛日历架构。
> **强制约束**：本架构彻底废除 v2.6.1 的「客户端内置免费 API Key + 多源无序混拼 + WorkManager 后台轮询推送」旧模式。

---

## 1. 2026 最前沿 Android 客户端技术栈

| 架构分层 | 选型方案 | 选用理由与工程规范 |
| :--- | :--- | :--- |
| **语言与并发** | Kotlin 2.x + Coroutines + `StateFlow` / `SharedFlow` | 全异步非阻塞数据流，杜绝主线程卡顿 |
| **UI 与材质** | [Jetpack Compose 1.13](https://developer.android.com/blog/posts/jetpack-compose-november-2026-release) + [Material 3 Expressive](https://m3.material.io/blog/whats-new-at-io26) + [Haze 2](https://github.com/chrisbanes/haze) | 原生 Progressive Blur 渐进模糊 + GPU 120Hz 液态玻璃 |
| **导航架构** | [Jetpack Navigation 3](https://developer.android.com/guide/navigation/navigation-3) | 2026 官方 Compose-first 稳定导航，原生掌控回退栈与转场状态 |
| **状态管理** | MVI（单向数据流 UDF） | 严格区分 `UiState`、`UiIntent`、`UiEffect`，根治状态分裂 |
| **本地真相源** | **Room DB (Single Source of Truth)** + DataStore | 所有界面只订阅 Room `Flow`，网络数据先校验入库再驱动 UI |
| **网络与解析** | Ktor Client (OkHttp 引擎) + `kotlinx.serialization` | 编译期类型安全序列化，零反射开销 |
| **图片缓存** | Coil 3（内存 + 磁盘双缓存 + 矢量兜底） | 队徽与球员头像秒开，404 自动回退至球队色+背号组件 |
| **推送与锁屏** | 轻量云端事件差分 → `ntfy` / FCM → [Android 16 ProgressStyle](https://developer.android.com/about/versions/16/features/progress-centric-notifications) & [Android 17 Metric Style](https://www.androidauthority.com/android-17-live-updates-metric-style-template-3669117/) + [Jetpack Glance](https://developer.android.com/develop/ui/compose/glance) | 锁屏常驻实时比分进度条 + 桌面 4×2 今日赛程小组件 |

---

## 2. 彻底根治数据错乱、缺失与不同步的「四重铁律」

### 铁律一：免费数据源「云端单点分工」，客户端零密钥
在 GitHub 仓库新增 `/cloud-sync` 目录，利用 **GitHub Actions（定时静态全量）+ Cloudflare Workers 免费版（10万次/日，用于按需实时拉取与代理）** 组成 0 元云端中台，客户端只访问你自己的统一 JSON 接口：
1. **基础赛程、积分榜与射手榜（零配额压力）**：由 **Football-Data.org 免费版**（10 次/分钟，覆盖英超/西甲/德甲/意甲/法甲/欧冠/巴甲/葡超等核心赛事）每 15 分钟云端同步一次并写入缓存。
2. **焦点战实时事件、阵容与中小联赛（精打细算）**：将 [API-Football 免费版（100 次/日）](https://www.api-football.com/pricing) 全部留在云端环境变量，**只对「当日已星标关注」或「正在进行中的焦点战」按需触发拉取**（每场 90 分钟核心节点约消耗 8–12 次请求，足够覆盖每日 8–10 场主队/重点战），严禁在未开赛或已完赛页面空转消耗配额。
3. **体彩竞彩赛程（0 成本双模式引擎）**：云端每日 09:00 / 15:00 / 21:00 同步官方体彩竞彩足球开售清单，通过 `MatchEntityMatcher` 将竞彩编号（如 `周五008`）、停售时间、让球数（`-1/+1`）精准挂载到主赛程表的 `lotteryMeta` 字段。

> **平滑升级预留**：所有外部接口封装在云端 `DataProviderAdapter` 之后。未来若预算允许升级至 [API-Football Pro（$16/月，7,500 次/日）](https://toolsforbettors.com/tools/api-football/)，**Android 客户端一行代码都不用改**，仅替换云端 Key 即可瞬间解锁全量 1,200+ 赛事 15 秒级覆盖。

### 铁律二：云端入库质检拦截器 + 中文黄金词典（`zh_entities.json`）
数据写入缓存前必须经过 4 道硬校验，不通过直接丢弃或修复，绝不污染客户端：
1. **首发去重与人数校验**：对主客队 `starters` 按 `playerId` 强制去重，若人数不等于 11 则标记 `lineupStatus = UNCONFIRMED` 并降级为文字名单视图，**彻底杜绝 v2.6.1 球场图重复渲染两排球员的故障**。
2. **教练与转会时效校验**：球队主教练字段优先读取本赛季最新完赛场次的 `coach` 实体，并对照 `zh_entities.json` 映射标准中文译名（根治「皇马主帅莫乌里恩霍」类历史脏数据）。
3. **标准中文译名与简写规范**：`zh_entities.json` 统一维护主流球队、球星标准中文名及**最大 6 汉字短名**（专用于通知栏与球场图，杜绝超长机械音译截断）。
4. **完赛态封存机制**：一旦比赛进入 `Finished` 且完赛超过 15 分钟，云端将其标记为 `isFinalized = true`，客户端永久走本地 Room 缓存，**彻底根治已完赛页面卡在「正在高速拉取实时事件」的 Bug**。

### 铁律三：客户端单一状态机（`MatchPhase`）+ Room SSOT
客户端定义唯一强类型状态机，日历卡片顶部徽章、底部入口、赛况详情页全部读取同一枚举，杜绝任何 UI 状态冲突：

```kotlin
sealed interface MatchPhase {
    data class Upcoming(val kickoffUtcMillis: Long) : MatchPhase
    data class Live(val elapsedMinute: Int, val extraMinute: Int? = null) : MatchPhase
    data object HalfTime : MatchPhase
    data class Finished(val isAfterExtraTime: Boolean = false, val isPenalties: Boolean = false) : MatchPhase
    data class Postponed(val reason: String? = null) : MatchPhase
}
```

- **单一真相源规则**：日历页、详情页、积分榜、球员榜全部通过 `matchDao.observeMatchesByDate(dateKey, isLotteryOnly)` 响应式订阅 Room 数据库。
- **「全部 / 体彩」瞬时切换**：顶部双态胶囊切换不触发网络请求，仅切换 Room 查询条件 `WHERE lotteryCode IS NOT NULL`，实现 0ms 瞬切。

### 铁律四：小米 HyperOS 个人专属「零丢包」实时推送方案
针对小米 HyperOS 杀后台特性，个人自用无需申请企业厂商通道，采用**三段式必达组合**：
1. **云端 15 秒事件差分 + VAR 10 秒防抖**：云端对比进行中比赛的前后快照，检测到「开赛、进球、点球、红牌、关注球员进球/助攻、半场、终场」且持续 10 秒未回滚后，通过模板合成自然流畅的中文图文事件播报，推送到免费开源通道 **[ntfy.sh](https://ntfy.sh/)**（独立私密 Topic，支持自托管或免费公共节点）或 FCM。
2. **小米 HyperOS 一次性个人设置**：在你自己的小米手机上为本 App 开启：
   - 应用信息 → 省电策略 → **无限制**
   - 应用信息 → **自启动**（开启）
   - 通知管理 → 允许锁屏通知、悬浮通知、声音与震动
3. **赛中前台常驻 Live Update 服务**：当收到「关注比赛开赛」信号（或你手动点击「盯盘/锁屏置顶」）时，App 拉起一个极轻量的 `ForegroundService`（`FOREGROUND_SERVICE_TYPE_DATA_SYNC` / `SHORT_SERVICE`），持续将最新比分、分钟数与进球图文节点覆写更新到同一个固定 `notificationId` 的 **Android 16/17 Live Update** 胶囊中；收到 `Finished` 终场事件并推送完赛果后自动停止服务，兼顾秒级实时与极低耗电。

---

## 3. GitHub 仓库推荐目录结构

```text
football-calendar/
├── DESIGN_SYSTEM.md              # 方案 C 视觉宪法（Agent 必读）
├── ARCHITECTURE.md               # 本内核架构契约（Agent 必读）
├── cloud-sync/                   # 云端 0 元聚合与推送脚本（Python / TypeScript）
│   ├── providers/                # football_data.py, api_football.py, sporttery.py
│   ├── validators/               # 首发去重、主帅校验、状态机归一化
│   ├── dictionary/
│   │   ├── zh_entities.json      # 标准中文球队/球员/主帅译名库
│   │   └── team_colors.json      # 方案 C 主流球队主色 Hex 库
│   └── push_dispatcher.py        # 15s 事件差分、VAR 防抖与 ntfy/FCM 推送
└── android-app/                  # Kotlin + Jetpack Compose 1.13 客户端
    ├── core/designsystem/        # ThemeTokens.kt, LiquidGlassBar.kt, TeamGradientHeader.kt
    ├── core/database/            # Room Entities, DAOs, Single Source of Truth
    ├── core/model/               # MatchPhase 强类型状态机与领域模型
    ├── feature/calendar/         # 日历首页 + 全部/体彩双态切换 + 观赛夜分组
    ├── feature/matchdetail/      # 动态双色光晕详情页 + 赛况/阵容/统计
    ├── feature/standings/        # 积分榜与球员榜
    └── feature/livepush/         # ntfy/FCM 接收器 + Android 16/17 Live Updates + Glance
```

---

## 4. 给 Antigravity 的分阶段施工指令（直接复制执行）

建议按以下 4 个阶段逐一交由 Antigravity 执行，**每完成一个阶段跑一次实机截图验收再进入下一阶段**：

1. **Phase 0（地基止血）**：将 `DESIGN_SYSTEM.md` 与 `ARCHITECTURE.md` 放入仓库根目录。让 Antigravity 建立 `MatchPhase` 状态机与 Room SSOT，彻底修复卡片状态冲突、首发阵容重复渲染、完赛态无限同步和皇马主帅脏数据。
2. **Phase 1（方案 C 视觉重塑）**：先实现 `ThemeTokens.kt`、`TeamColorRegistry`、`Haze 2` 悬浮液态玻璃导航与 `Compose 1.13` 动态双色渐变详情顶栏，全站统一字阶与 `tnum` 等宽数字。
3. **Phase 2（云端 0 元中台 + 全部/体彩切换）**：上线 `/cloud-sync` 定时聚合与 `sporttery` 竞彩对齐脚本，移除 APK 内所有硬编码 API 密钥，点亮首页顶部「全部 / 体彩」毫秒级切换。
4. **Phase 3（小米 HyperOS 实时图文推送 + 锁屏 Live Updates）**：接通云端 15 秒差分引擎与 `ntfy`/FCM，实现关注球队/球员实时图文推送、锁屏进度轴比分胶囊与 Glance 桌面小组件。
