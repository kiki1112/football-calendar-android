# DESIGN_SYSTEM.md — 足球比赛日历 Android App 视觉宪法（方案 C · 动态球队光晕）

> **设计定位**：融合 [Apple Sports](https://builtformars.com/company/apple-sports) 动态球队色彩秩序与 [iOS 26 Liquid Glass](https://developer.apple.com/documentation/technologyoverviews/liquid-glass) 悬浮液态玻璃材质，基于 [Jetpack Compose 1.13](https://developer.android.com/blog/posts/jetpack-compose-november-2026-release) 与 [Material 3 Expressive](https://m3.material.io/blog/whats-new-at-io26) 原生构建。
> **强制约束**：Antigravity / AI Agent 在生成或修改任何 UI 代码前**必须完整读取本文档**。严禁在任意 `@Composable` 中硬编码未注册色值、圆角或间距。

---

## 1. 核心视觉哲学（三原则）

1. **列表极度克制，详情情绪绽放**：日历首页、积分榜、球员榜保持高对比度珍珠白画布，仅用极细 3dp 主队色竖条点睛；进入单场比赛或球队详情页时，顶部柔和铺开主客队双色动态渐变光晕。
2. **玻璃只给悬浮导航，绝不给高频数据卡**：遵循 [NN/G Liquid Glass 可用性警示](https://www.nngroup.com/articles/liquid-glass/)，液态毛玻璃仅用于顶部「全部 / 体彩」悬浮切换胶囊、底部 4-Tab 悬浮导航栏与锁屏 Live Updates 胶囊；比赛卡片与积分榜表格一律使用纯白高对比实体底色。
3. **苹果级等宽数字与弹簧物理手感**：所有比分、开球时间、比赛分钟数、积分与停售倒计时强制开启等宽数字（Tabular Figures），所有弹窗、切换与收藏统一使用非线性弹簧动效与轻触觉反馈。

---

## 2. Design Tokens（单一真相源 · `ThemeTokens.kt`）

### 2.1 基础色板（Daylight Pearl 主画布）
| Token 名称 | Hex 色值 | 用途规范 |
| :--- | :--- | :--- |
| `CanvasPearl` | `#F8F9FB` | 全局页面底色（日历、积分榜、球员榜、设置） |
| `SurfacePure` | `#FFFFFF` | 比赛卡片、积分榜容器、弹窗主体背景 |
| `SurfaceSubtle` | `#F1F5F9` | 次级胶囊背景、输入框、未选中筛选条 |
| `InkPrimary` | `#0A0D12` | 主标题、队名、大号比分（对比度 ≥ 15:1） |
| `InkSecondary` | `#475569` | 联赛名、副标题、英文队名、次要说明 |
| `InkMuted` | `#64748B` | 表头、时间戳、占位辅助文 |
| `Hairline` | `#E2E8F0` | 1dp 极细分割线（优先用留白代替边框） |
| `AccentFallback` | `#C8102E` | 默认品牌运动红主色（选中日期、交互高光与未收录球队兜底渐变色） |
| `LivePulse` | `#E11D48` | 进行中比赛脉冲圆点、直播分钟胶囊（`78'`） |
| `LotteryGold` | `#B45309` | 体彩模式专属竞彩编号与让球标示文字色（配 `#FEF3C7` 浅底） |
| `SuccessEmerald` | `#059669` | 欧冠直通区指示条、主队胜率/正净胜球高亮 |

### 2.2 动态球队主色系统（`TeamColorRegistry`）
- 维护本地 JSON 映射表（主流五大联赛、欧冠、中超、国家队约 120 支核心球队的官方主色 Hex，如阿森纳 `#991B1B`、曼城 `#0284C7`、皇马 `#1E3A8A`、巴萨 `#9F1239`、利物浦 `#B91C1C`、切尔西 `#1D4ED8`）。
- **兜底机制**：未收录球队使用队徽图像提取主色（`Palette` API 并限制亮度在 `0.25–0.45` 确保白色比分 AAA 对比度），若队徽加载失败则安全降级为 `AccentFallback (#C8102E)`。
- **双色渐变规则**：比赛详情页顶部 220dp 区域绘制主队色（左上）向客队色（右上）平滑过渡的 Mesh/Linear 渐变，底部 64dp 使用 [Compose 1.13 Progressive Blur](https://developer.android.com/blog/posts/jetpack-compose-november-2026-release)（`Modifier.blur` + `BlurRadiusSpec`）无缝羽化融入下方 `#FFFFFF` 内容区。

### 2.3 排版与等宽数字规范
- **字体栈**：中文跟随小米系统无衬线字体（MiSans / Noto Sans SC），英文与数字优先使用 `Plus Jakarta Sans` / 系统 Roboto 并强制启用 `fontFeatureSettings = "tnum"`。
- **字阶规范**（严禁低于 12sp）：
  - `ScoreHero`: `36.sp`, Bold (700), `tnum` — 详情页主比分
  - `PageTitle`: `28.sp`, ExtraBold (800), `-0.02.em` — 顶部大标题
  - `MatchScoreCard`: `20.sp`, Bold (700), `tnum` — 日历卡片比分/开球时间
  - `TeamName`: `15.sp`, SemiBold (600) — 单行省略，严禁换行撑爆卡片
  - `Body`: `14.sp`, Regular (400) — 图文直播与事件描述
  - `MetaCaption`: `12.sp`, Medium (500), `tnum` — 联赛名、竞彩编号、停售时间

### 2.4 圆角、间距与液态玻璃参数
- **圆角（Corner Radius）**：页面级主卡片 `20.dp`；顶部/底部悬浮液态玻璃胶囊 `999.dp`（全圆角）；状态微标签 `8.dp`；详情页 BottomSheet 顶角 `28.dp`。
- **间距（Spacing）**：严格遵循 4dp 网格（页面左右边距 `16.dp`、卡片垂直间距 `10.dp`、卡片内边距 `16.dp`）。
- **液态玻璃（Liquid Glass via [Haze 2](https://github.com/chrisbanes/haze)）**：
  - `blurRadius = 24.dp`，底色 `Color.White.copy(alpha = 0.72f)`，外圈叠加 `1.dp` 渐变高光描边（顶部 `White 0.85f` → 底部 `White 0.20f`），阴影 `elevation = 8.dp`（极柔和漫反射）。

### 2.5 弹簧动效与触感
- **全局弹簧曲线**：`spring(dampingRatio = 0.78f, stiffness = 400f)` 用于「全部 / 体彩」滑块切换、BottomSheet 展开、日期周历点选。
- **触觉反馈**：切换全部/体彩模式、点击收藏星标、下拉触发同步时调用 `HapticFeedbackType.TextHandleMove` / `LongPress`。

---

## 3. 五大核心界面布局契约

### 3.1 日历首页（Calendar Home）
1. **顶部悬浮双态胶囊**：居中放置「全部赛事 ｜ 体彩焦点」液态玻璃分段切换器，一键无感切换本地视图。
2. **紧凑周历条（支持下拉展开整月）**：默认展示本周 7 天横条，选中日（如 `27`）使用 `AccentFallback` 实心圆高亮；有关注球队比赛的日期下方点亮 4dp 脉冲小红点。保留「观赛夜分组（次日 00:00–05:59 归入前夜）」标注。
3. **无边框极简比赛卡**：
   - 左侧边缘嵌入 `3.dp` 主队色圆角竖条。
   - 中间横向三段式：`[圆形主队徽 + 中文队名]` — `[大号等宽比分或开球时间 + 单一状态微标签]` — `[中文队名 + 圆形客队徽]`。
   - **体彩模式专属变形**：切换到「体彩」时，卡片左上角显示 `周三001 · 让球(-1)`（`LotteryGold`），右上角显示 `停售 22:50`，彻底隐藏非体彩场次。
4. **底部悬浮液态玻璃 Dock**：悬浮于屏幕底部 `16.dp` 处，4 个图标（日历 / 积分榜 / 球员榜 / 设置），选中态带微弱高光药丸底色，列表滚动时从 Dock 下方流过。

### 3.2 比赛详情页（Match Detail）
- **动态双色渐变顶栏**：主客队双色平滑渐变 + `Compose 1.13` 渐进模糊过渡；展示主客队徽、36sp 等宽比分、`• 78'` 直播脉冲胶囊与收藏铃铛。
- **4 个子 Tab**：`赛况（事件轴 + 图文播报）`、`阵容（球场图 + 替补）`、`统计（双向对比条）`、`交锋积分`。
- **阵容球场图硬规范**：
  - 严格按阵型坐标渲染 **11 名去重首发球员**，严禁重复渲染同一行。
  - 若球员头像缺失或 404，自动渲染**「主队色圆形徽章 + 白色等宽球衣背号」**，下方单行显示精简中文姓氏（超过 5 个汉字自动缩写，严禁出现 15 字超长机械音译）。

### 3.3 积分榜与球员榜
- **积分榜**：取消厚重外框，用 `1.dp` 极细水平分割线 + 左侧 `3.dp` 资格区色条（欧冠绿 / 欧联蓝 / 降级红）+ 全部数值列 `tnum` 右对齐。
- **球员榜**：顶部统一使用单行可横向滚动的筛选胶囊（右侧留出渐隐遮罩提示可滑动，修复 v2.6.1 右侧文字硬截断问题）。

### 3.4 锁屏实时比分推送（Android 16/17 Live Updates）
- 赛中推送采用 [Android 16 `Notification.ProgressStyle`](https://developer.android.com/about/versions/16/features/progress-centric-notifications) 与 [Android 17 `Metric Style`](https://www.androidauthority.com/android-17-live-updates-metric-style-template-3669117/)，以磨砂胶囊样式常驻锁屏与状态栏：清晰呈现 `主队缩写 比分 客队缩写`、`当前分钟` 以及 `0'–90'` 进度轴上的进球节点。

---

## 4. Antigravity 防跑偏红线（发版自检清单）

每次 Antigravity 提交 UI 改动后，必须通过以下 5 项验收才允许合并：
1. **零硬编码色值**：全局搜索 `Color(0x` 除 `ThemeTokens.kt` 和 `TeamColorRegistry.kt` 外必须为 0 处。
2. **单一状态一致性**：任意比赛卡片上只允许渲染来自同一个 `MatchPhase` 的状态文本，严禁出现「右上未开赛 + 右下已结束」。
3. **首发人数硬断言**：球场图 Composable 入口强制断言 `homeStarters.distinctBy { it.id }.size <= 11`。
4. **超长名截断保护**：所有队名、球员名均配置 `maxLines = 1, overflow = TextOverflow.Ellipsis`。
5. **自动截图视觉回归**：运行实机截图脚本后，重点核查首页全部/体彩切换态、进行中/完赛详情页、缺失头像兜底态与悬浮玻璃底栏无遮挡。
