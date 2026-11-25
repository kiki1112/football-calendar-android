# ⚽ 足球赛事日历 · 红白苹果风全功能版

基于 **Apple HIG（Apple Sports / Apple Calendar）** 设计语言的**红白配色**安卓足球伴侣 + **GitHub 全托管自动同步源**。

---

## 核心特性（v2.0）

- **Apple 红白视觉体系**：`#F2F2F7` 浅灰白画布 + 纯白浮层圆角卡片 + `#E11D48` 苹果红重点标识 + 等宽数字排版。
- **全量队徽 Logo**：覆盖英超/西甲/德甲/意甲/法甲/欧冠及国家队真实队徽（支持 PNG & SVG 加载与纯色首字圆标兜底）。
- **国家队赛事支持**：除五大联赛与欧冠外，支持 **世界杯 (`WC`)、欧洲杯 (`EC`)、国家队精选 (`INT`)** 赛程同步。
- **实时积分榜（Standings Tab）**：免费同步五大联赛与欧冠积分榜，含欧冠/欧战资格区指示条、近 5 场战绩圆点与主队行高亮。
- **球星系统（带真人头像）**：内置 16+ 顶级球星真人头像与资料卡，支持在线检索任意球星并关联查看其俱乐部与国家队近期赛程。
- **焦点战倒计时 & 看球助手**：顶部下一场焦点大战实时倒计时 Hero 卡、同日开球撞车提示、必看清单 (`♥`)、后台 `WorkManager` 自动检查改期、手机系统日历托管与 `.ics` 导出。

---

## GitHub 托管地址

- **项目仓库**：https://github.com/kiki1112/football-calendar-android
- **网页赛程与积分榜看板**：https://kiki1112.github.io/football-calendar-android/
- **App 订阅接口**：https://kiki1112.github.io/football-calendar-android/fixtures.json
- **通用 `.ics` 日历订阅**：https://kiki1112.github.io/football-calendar-android/football.ics

> 💡 **接入真实实时赛程与积分榜**：
> 在 [football-data.org](https://www.football-data.org/) 免费申请 API Token，并添加到仓库 **Settings → Secrets and variables → Actions → `FOOTBALL_DATA_API_KEY`**，每 6 小时自动同步真实数据。
