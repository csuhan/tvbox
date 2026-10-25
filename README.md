# 聚影 · TVBox 手机版

> **本项目 100% 由 AI（KIMI-K3）编写完成。**
> **无广告、无需注册登录，只有最纯粹的视频观看体验。**

一款参考 TVBox（影视仓）实现的 Android 手机端影视聚合播放器。给定 TVBox 接口（数据源）地址后，自动加载、解析配置，支持点播与直播播放。

![Android](https://img.shields.io/badge/Android-7.0%2B-green) ![Kotlin](https://img.shields.io/badge/Kotlin-2.0-blue) ![Compose](https://img.shields.io/badge/Jetpack-Compose-blueviolet) ![APK](https://img.shields.io/badge/APK-~2.5MB-orange)

## ✨ 特性

- **纯粹体验**：无广告、无账号体系、无埋点统计，打开就看
- **极致小巧**：Release APK 仅约 2.5 MB（R8 全量压缩）
- **小窗 ⇄ 大窗无缝续播**：详情页内嵌小窗与全屏播放共用同一播放器实例，切换零加载、进度零中断；大窗内换集，返回小窗自动同步
- **播放中屏幕常亮**：暂停自动恢复息屏，省电贴心
- **明暗双主题**：跟随系统自动切换

## 📱 截图

| 首页 | 详情（小窗播放） | 搜索 |
|---|---|---|
| ![首页](docs/screenshots/01-home.jpg) | ![详情](docs/screenshots/02-detail.jpg) | ![搜索](docs/screenshots/05-search.jpg) |

| 全屏播放 | 直播 | 我的 |
|---|---|---|
| ![播放](docs/screenshots/03-player.jpg) | ![直播](docs/screenshots/04-live.jpg) | ![我的](docs/screenshots/06-mine.jpg) |

## 📦 下载安装

前往 [**Releases**](https://github.com/csuhan/tvbox/releases) 下载最新 APK（`JuyingTV-vX.X.X.apk`），传到手机点击安装即可。

- 系统要求：Android 7.0（API 24）及以上
- 安装时如提示"未知来源"，请按系统指引允许本次安装
- 应用仅请求**网络权限**，不索取通讯录/定位/存储等任何敏感权限

## 🚀 使用教程

### 第一步：配置数据源（只需一次）

App 本身不含任何影视内容，需要配置一个 TVBox 接口地址：

1. 打开 App → 底部「**我的**」→「**设置与数据源**」
2. 方式一：在**推荐接口**列表中任选一个，点「使用」即可（推荐新手）
3. 方式二：点「**添加接口**」，粘贴你自己的 TVBox 接口地址（http(s)://…json）
4. 返回首页，内容自动加载

> 支持标准 TVBox 接口 JSON 配置（`sites` / `lives` / `parses`），兼容整体 Base64 编码、BOM、相对地址自动补全；支持苹果 CMS 采集 API（type 0 XML / type 1 JSON）站点。type 3 爬虫源（Spider JAR/JS）会在列表中标注"暂不支持"。

### 点播

- **首页**：推荐 + 分类滑动切换 + 触底自动加载更多
- **搜索**：首页右上角 🔍，输入防抖实时搜索 + 热门搜索词
- **详情页**：进页即播（小窗），支持多线路切换、数字选集、倒序、收藏
- **续播**：观看历史自动记忆进度，详情页/「我的」页一键续播

### 播放器手势（全屏）

| 手势 | 功能 |
|---|---|
| 单击 | 显示/隐藏控制栏 |
| 双击左/右半屏 | 快退/快进 10 秒 |
| 水平拖动 | 拖动进度 |
| 左半屏上下滑 | 调节亮度 |
| 右半屏上下滑 | 调节音量 |

控制栏还支持：选集、倍速（0.5x–3x）、画面比例（适应/填充/裁剪）、上一集/下一集。非直连播放页会自动**网页嗅探**真实视频地址，失败时自动尝试接口内置解析 API。

### 直播

底部「直播」→ 选择直播源 → 分组侧边栏 + 频道网格。支持 m3u / txt 两种列表格式（GBK 编码自动识别），信号不佳自动切换备用源。

### 小窗 ⇄ 大窗

详情页顶部小窗播放时，点右下角全屏按钮（或控制条上的全屏图标）进入大窗——**同一播放器实例直接续播，不重新加载**；大窗返回，小窗接着播，画面不黑、进度不断。

## ❓ 常见问题

**Q: 首页空白/提示没有可用接口？**
A: 接口地址失效或网络不通，换一个推荐接口或添加新接口。

**Q: 某些站点显示"暂不支持"？**
A: 该站点是 type 3 爬虫源（需要 Spider JAR/JS 环境），手机版暂不支持，请在站点列表中切换其他站点。

**Q: 播放失败？**
A: 依次尝试：切换剧集线路 → 切换站点 → 更换接口。部分地址有防盗链，App 已自动携带 UA/Referer 并支持嗅探解析。

## 🛠 自行构建

```bash
# 需要 JDK 17+ 与 Android SDK（local.properties 中配置 sdk.dir）
./gradlew assembleRelease
# 产物：app/build/outputs/apk/release/app-release-unsigned.apk（需自行签名）
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk（可直接安装）
```

Release 默认开启 R8 压缩混淆 + 资源裁剪（`isMinifyEnabled` / `isShrinkResources` / 仅保留中英语言），如需关闭请在 `app/build.gradle.kts` 中调整。

## 🧱 技术栈

| 层 | 技术 |
|---|---|
| UI | Jetpack Compose + Material 3（明暗双主题） |
| 播放 | Media3 ExoPlayer（HLS/DASH/OkHttp DataSource），全局共享播放器实例 |
| 网络 | OkHttp + Gson |
| 图片 | Coil |
| 存储 | Room（历史/收藏）+ DataStore（配置/源列表） |
| 架构 | MVVM（ViewModel + StateFlow） |

```
app/src/main/java/com/juying/tv/
├── TvApp.kt                  # Application（Coil/源管理初始化）
├── MainActivity.kt           # 导航 + 底部 Tab
├── data/
│   ├── model/                # TVBox 配置 / 点播 / 直播数据模型
│   ├── remote/Http.kt        # OkHttp 封装
│   ├── source/               # 配置解析、苹果CMS API、直播列表解析
│   └── local/                # Room（历史/收藏）
├── player/
│   ├── PlayerActivity.kt     # 全屏播放器（手势/选集/倍速/比例/连播）
│   ├── PlayerHolder.kt       # 全局共享播放器（小窗⇄大窗无缝交接）
│   └── WebSniffer.kt         # WebView 视频地址嗅探
└── ui/                       # 首页 / 详情 / 搜索 / 直播 / 我的 / 设置
```

## ⚠️ 免责声明

1. 本项目为**纯技术学习项目**，由 AI 生成代码，仅供学习交流 Android/Compose/ExoPlayer 开发技术使用。
2. 本应用**不包含、不存储、不分发任何影视内容**，所有内容均来自用户自行配置的第三方公开接口，与本项目无关。
3. 请于下载后 24 小时内删除本软件；使用本软件产生的任何法律后果由使用者自行承担。
4. 请勿将本项目用于任何商业用途或非法用途。

## 🌐 项目主页

[csuhan.github.io/tvbox](https://csuhan.github.io/tvbox/)
