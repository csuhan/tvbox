# 聚影 · TVBox 手机版

一款参考 TVBox（影视仓）实现的 Android 手机端影视聚合播放器。给定 TVBox 接口（数据源）地址后，自动加载、解析配置，支持点播与直播播放。

![Android](https://img.shields.io/badge/Android-7.0%2B-green) ![Kotlin](https://img.shields.io/badge/Kotlin-2.0-blue) ![Compose](https://img.shields.io/badge/Jetpack-Compose-blueviolet)

## 功能

### 数据源
- 支持标准 TVBox 接口 JSON 配置（`sites` / `lives` / `parses`），兼容整体 Base64 编码、BOM、相对地址自动补全
- 站点类型：**type 0（XML）/ type 1（JSON）苹果CMS 采集 API 全量支持**；type 3 爬虫源（Spider JAR/JS）在列表中明确标注"暂不支持"
- 内置多个推荐接口（来自聚玩盒子等公开列表），一键使用/收藏；支持手动添加、删除自有接口
- 配置本地缓存，冷启动秒开、后台静默刷新

### 点播
- 首页推荐 + 分类浏览 + 触底自动加载更多
- 搜索（输入防抖实时搜索 + 热门搜索词）
- 详情页：海报、年份/地区/类型、导演/主演/简介、多播放线路、选集、倒序
- **智能选线**：自动优先选择直连媒体（m3u8/mp4）线路
- 收藏、观看历史（进度记忆，详情页一键"续播"）

### 播放器（ExoPlayer / Media3）
- HLS(m3u8)/DASH/MP4 等格式，自动管理请求头（UA/Referer）
- **网页嗅探**：非直连地址自动在后台 WebView 中加载播放页、自动点击播放并拦截真实视频地址；失败时依次尝试接口内置的解析 API（jx）
- 手势：左半屏上下滑调亮度、右半屏调音量、水平拖动快进快退
- 倍速（0.5x–3x）、画面比例（适应/填充/裁剪）、选集抽屉、自动连播下一集

### 直播
- 多直播源切换，空源自动跳到下一个
- m3u / txt 两种列表格式，GBK 编码自动识别
- 分组侧边栏 + 频道网格

## 技术栈

| 层 | 技术 |
|---|---|
| UI | Jetpack Compose + Material 3（深色主题） |
| 播放 | Media3 ExoPlayer（HLS/DASH/OkHttp DataSource） |
| 网络 | OkHttp + Gson |
| 图片 | Coil |
| 存储 | Room（历史/收藏）+ DataStore（配置/源列表） |
| 架构 | MVVM（ViewModel + StateFlow） |

## 构建

```bash
# 需要 JDK 17+ 与 Android SDK（local.properties 中配置 sdk.dir）
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

## 目录结构

```
app/src/main/java/com/juying/tv/
├── TvApp.kt                  # Application（Coil/源管理初始化）
├── MainActivity.kt           # 导航 + 底部 Tab
├── data/
│   ├── model/                # TVBox 配置 / 点播 / 直播数据模型
│   ├── remote/Http.kt        # OkHttp 封装
│   ├── source/
│   │   ├── SourceManager.kt  # 接口加载/解析/缓存/切换（核心）
│   │   ├── MacCmsApi.kt      # 苹果CMS JSON+XML 解析（核心）
│   │   ├── LiveParser.kt     # m3u/txt 直播列表（GBK 自适应）
│   │   └── VodRepository.kt
│   └── local/Db.kt           # Room：观看历史 + 收藏
├── player/
│   ├── PlayerActivity.kt     # 播放器（手势/倍速/选集/连播）
│   └── WebSniffer.kt         # WebView 嗅探真实播放地址
└── ui/                       # 首页/搜索/详情/直播/我的/设置
```

## 声明

仅供学习交流使用，不存储任何视频内容，所有内容均来自用户自行配置的第三方公开接口。
