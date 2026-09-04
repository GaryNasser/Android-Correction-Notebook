# BITStudy · Android 错题本与学习助手

[![Platform](https://img.shields.io/badge/platform-Android-3DDC84.svg)](https://www.android.com)
[![Min SDK](https://img.shields.io/badge/minSdk-24-blue.svg)](https://developer.android.com/about/versions)
[![Target SDK](https://img.shields.io/badge/targetSdk-35-blue.svg)](https://developer.android.com/about/versions)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.0--RC1-7F52FF.svg)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202024.10-4285F4.svg)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/license-MIT-green.svg)](#许可证)

> 北京理工大学（BIT）学生的一站式学习助手：错题整理 · 番茄钟 · 沉浸学习 · 课程回放 · AI 辅导 · 知识库。

BITStudy 是一款面向高校学生的 Android 学习类应用，集成了**课程表 / 错题本 / 番茄钟 / 沉浸白噪音 / 课程视频回放 / AI 辅导 / 个人知识库 / 学习统计**等多种能力，旨在帮助学生更高效地组织学习、复习错题、跟进课程进度。

---

## 📑 目录

- [✨ 功能特性](#-功能特性)
- [📸 应用截图](#-应用截图)
- [🏗️ 项目架构](#-项目架构)
- [🛠️ 技术栈](#-技术栈)
- [🚀 快速开始](#-快速开始)
  - [环境要求](#环境要求)
  - [构建与运行](#构建与运行)
- [📂 项目结构](#-项目结构)
- [🌐 后端服务](#-后端服务)
- [🤝 贡献指南](#-贡献指南)
- [📄 许可证](#-许可证)
- [👥 作者与致谢](#-作者与致谢)

---

## ✨ 功能特性

### 🏠 首页 Home
- **课程表**：按周展示课程安排，支持日期切换、教室地点展示、紧凑布局自适应。
- **学习计划 / 待办 (Planner & Todo)**：自定义待办、番茄钟倒计时、待办历史回顾。
- **文章推荐**：内嵌文章卡片，支持图文混排，详情页支持富文本（文本 / 图片 / 链接），无内容时回退原文链接。
- **沉浸学习模式**：内置 4 种白噪音（雨声 / 海浪 / 森林 / 咖啡馆），支持后台播放、独立媒体前台服务，Android 14+ 适配 `mediaPlayback` 前台服务类型。

### 🤖 AI 辅导 AI Tutor
- 多 AI 服务商配置（API Key 使用 `AndroidX Security Crypto` 加密本地存储）。
- 会话管理：基于 Room 数据库的聊天会话、消息、用户记忆。
- 工具型 AI（Function Calling / AI Action）：可在助手返回的操作建议中直接触发应用内行为（添加待办、生成学习计划等）。

### 📚 知识库 Knowledge Base
- 本地文件管理：支持文件夹 / 文件的增删改查、文件内部分块（Chunk）存储，便于后续检索。
- 文件查看器：在应用内打开常见文档类型。
- 学习集 (Study Set)：与记忆卡片、间隔复习结合。

### 🎬 课程回放 Yanhe（延河课堂）
- 课程列表 → 视频列表 → 视频播放器三级导航。
- 基于 **Media3 / ExoPlayer** 的播放内核，支持 HLS。
- 后台播放通过 `MediaSessionService` + 前台服务实现，支持通知栏控制。

### 🔐 登录与认证
- 用户名 / 密码登录。
- **统一身份认证 (CAS)** 接入，适配校园账号体系。
- Token 自动刷新：`Authenticator` + `Interceptor` 处理 401 自动续签。
- 安全存储：`EncryptedSharedPreferences` 加密保存凭据。

### 📊 学习统计 Statistics
- 学习时长、番茄钟数量、待办完成率等多维度数据可视化。
- 学习仪表盘与课程时间表解耦，便于回顾任意日期。

### 👤 个人中心 Profile
- 用户信息、应用设置、AI 服务商配置、版本更新检查。

### 🛡️ 其它工程能力
- 应用内更新 (`UpdateApiService`)：版本检查与下载。
- **BITShare 校内资源**：对接校内文件分享平台搜索 / 详情接口。
- 全量错误拦截：`AuthInterceptor` / `TokenAuthenticator` 统一处理鉴权。
- **16 KB Page Size** 对齐适配（`android.enableNativeHeapAlignmentFor16kbPageSize`）。

---

## 📸 应用截图

> 建议在 `docs/screenshots/` 目录下添加截图后在此引用。仓库目前为保持精简未附带图片占位。

---

## 🏗️ 项目架构

整体遵循 **MVVM + Repository + Hilt DI** 的经典分层架构，结合 Jetpack Compose 实现 UI：

```
┌──────────────────────────────────────────────────────────┐
│                    UI Layer (Compose)                    │
│   Screens / ViewModels / Navigation / Components        │
└──────────────────────┬───────────────────────────────────┘
                       │ StateFlow / collectAsStateWithLifecycle
┌──────────────────────▼───────────────────────────────────┐
│                    Domain Layer                          │
│            UseCase / Models (领域模型)                    │
└──────────────────────┬───────────────────────────────────┘
                       │
┌──────────────────────▼───────────────────────────────────┐
│                    Data Layer                            │
│  Repository · Remote (Retrofit/OkHttp) · Local (Room)    │
│  DataStore · SecurityCrypto · DataSource                 │
└──────────────────────────────────────────────────────────┘
```

关键模块包结构：

```
com.github.garynasser.correction_notebook
├── data
│   ├── datasource
│   ├── local          // Room (AI, KnowledgeBase), DataStore, TokenManager, AISettingsManager
│   ├── model          // auth, ai, course, home, yanhe, knowledgebase, studyset, ...
│   ├── remote         // Retrofit ApiServices, Interceptors, Authenticator
│   └── repository     // 各业务 Repository
├── di                 // Hilt Modules
├── domain / usecase
├── service            // VideoPlaybackService (MediaSessionService)
├── ui
│   ├── components     // 复用组件 (FreshCard, AuthFrame, ...)
│   ├── navigation
│   ├── screens        // home, aitutor, yanhe, knowledgebase, login, register, profile, statistics, main
│   ├── theme
│   └── update
├── utils
├── MainActivity
├── MainContainer
└── MyApplication      // @HiltAndroidApp
```

---

## 🛠️ 技术栈

| 类别 | 技术 | 版本 |
|------|------|------|
| 语言 | Kotlin | 2.0.0-RC1 |
| 构建 | Android Gradle Plugin | 8.13.2 |
| UI | Jetpack Compose (BOM) | 2024.10.01 |
| UI | Material 3 / Compose Material Icons Extended | 1.4.0 / 1.7.0 |
| 导航 | Navigation Compose | 2.8.3 |
| 异步 | Kotlinx Coroutines / Flow | – |
| DI | Hilt | 2.51.1 |
| 本地数据库 | Room | 2.6.1 |
| 网络 | Retrofit + OkHttp + Logging Interceptor | 2.11.0 / 4.12.0 |
| 序列化 | Kotlinx Serialization JSON / Gson Converter | 1.6.3 / – |
| 图片加载 | Coil Compose | 2.6.0 |
| 偏好 / 加密 | DataStore Preferences / AndroidX Security Crypto | 1.1.1 / 1.1.0-alpha06 |
| 媒体播放 | AndroidX Media3 (ExoPlayer + UI + Session + HLS) | 1.2.0 |
| 后台前台服务 | Foreground Service (`mediaPlayback`) | – |

> 所有版本均集中在 `gradle/libs.versions.toml` 中管理（Version Catalog）。

---

## 🚀 快速开始

### 环境要求

| 项目 | 要求 |
|------|------|
| Android Studio | Koala (2024.1.1) 或更新版本 |
| JDK | 17 (项目使用 `compileOptions` + `kotlinOptions` 锁定 17) |
| Android SDK Platform | API 35 (`compileSdk` / `targetSdk`) |
| 最低运行设备 | Android 7.0 / API 24 (`minSdk`) |
| Gradle | Wrapper 自带，无需手动安装 |

### 构建与运行

1. **克隆仓库**

   ```bash
   git clone https://github.com/GaryNasser/Android-Correction-Notebook.git
   cd Android-Correction-Notebook
   ```

2. **配置本地 SDK 路径**

   在项目根目录新建 `local.properties`，填入本机 SDK 路径（**该文件已被 `.gitignore` 忽略，不会被提交**）：

   ```properties
   sdk.dir=/Users/<your-name>/Library/Android/sdk
   # Windows 示例：sdk.dir=C\:\\Users\\<your-name>\\AppData\\Local\\Android\\Sdk
   ```

3. **使用 Gradle Wrapper 构建**

   ```bash
   # 调试包（直连本机默认后端地址，请按需调整 API Base URL）
   ./gradlew :app:assembleDebug

   # 安装到已连接设备 / 模拟器
   ./gradlew :app:installDebug
   ```

4. **在 Android Studio 中运行**

   - 打开项目根目录，等待 Gradle Sync 完成。
   - 选择 `app` 配置 → 选择设备 → 点击 ▶ Run。

### 配置后端地址

默认 API Base URL 配置在 `data/remote/network` 模块中。开发阶段如指向本地后端（Spring Cloud Gateway / Eureka 等），请按实际环境修改：

```kotlin
// 示例：build.gradle.kts 中 buildConfigField 或直接写在 Retrofit Provider
buildConfigField("String", "API_BASE_URL", "\"https://your.api.host/\"")
```

⚠️ 由于本项目对接校园服务（统一身份认证、课程平台等），**真实可用的后端仅对校内网络开放**，公网部署需自行搭建对应后端或修改为 Mock 实现。

---

## 📂 项目结构

```
Android-Correction-Notebook/
├── app/                          # 主模块 (Android Application)
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── ic_launcher-playstore.png
│       │   ├── java/com/github/garynasser/correction_notebook/
│       │   └── res/
│       │       ├── raw/          # 白噪音资源 (rain/ocean/forest/cafe)
│       │       └── ...
│       ├── androidTest/
│       └── test/
├── gradle/
│   └── libs.versions.toml        # Version Catalog
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradlew / gradlew.bat
├── .gitignore
└── README.md
```

> 📌 根目录中的 `docs/` 与 `spring-correction-book-master/` 已被 `.gitignore` 排除，分别用于本地存放接口文档与后端源码（Spring Cloud 工程：Discovery + Gateway + JWT Client + Media Process Client）。

---

## 🌐 后端服务

项目配套后端采用 Spring Cloud 微服务架构，目录结构（本地，未入库）：

```
spring-correction-book-master/
├── discovery-server      # Eureka 注册中心
├── gateway               # Spring Cloud Gateway 网关
├── jwt-client            # JWT 鉴权服务
└── media-process-client  # 媒体处理服务（视频转码 / HLS 等）
```

> 后端非开源仓库的一部分，仅作开发参考。

### 对接的外部平台

- **BITShare**：校内文件分享平台，详见 `docs/BITShare-API-接口文档.md`（本地文档，未入库）。
- **统一身份认证 (CAS)**：北京理工大学校园账号登录。
- **延河课堂 (Yanhe)**：课程回放视频源。

---

## 🤝 贡献指南

欢迎以 Issue / Pull Request 的形式贡献代码或建议 ✨

1. Fork 本仓库。
2. 创建特性分支：`git checkout -b feat/your-feature`
3. 提交代码：`git commit -m "feat: add your feature"`
4. 推送分支：`git push origin feat/your-feature`
5. 发起 Pull Request，并描述清楚改动点与测试情况。

### 代码规范

- Kotlin 官方代码风格（已在 `gradle.properties` 中配置 `kotlin.code.style=official`）。
- Compose 优先，新功能尽量以 Composable 形式实现。
- ViewModel 通过 Hilt 注入，UI 状态推荐使用 `StateFlow` + `collectAsStateWithLifecycle`。
- 数据库变更需提供 Room Migration；网络层变更需同步更新文档。

---

## 📄 许可证

本项目以 **MIT License** 开源发布。你可以自由使用、修改、分发，但需保留版权声明。

```
MIT License

Copyright (c) 2025 GaryNasser

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND...
```

> 如需采用其它许可证（如 Apache-2.0 / GPLv3），请在合并前通过 Issue 与维护者沟通。

---

## 👥 作者与致谢

- **作者 / 维护者**：[GaryNasser](https://github.com/GaryNasser)
- **项目地址**：[https://github.com/GaryNasser/Android-Correction-Notebook](https://github.com/GaryNasser/Android-Correction-Notebook)

特别感谢：

- Jetpack Compose / Hilt / Room / Media3 等开源库的作者与社区。
- 北京理工大学相关平台（统一身份认证、延河课堂、BITShare）的接口支持。
- 所有为本项目提交 Issue 与 PR 的贡献者。

---

## 📮 反馈

如遇到 Bug 或有功能建议，欢迎通过 [Issues](https://github.com/GaryNasser/Android-Correction-Notebook/issues) 反馈，**请勿在 Issue 中泄露个人敏感信息（账号、Token 等）**。

<div align="center">

如果这个项目对你有帮助，欢迎 ⭐ Star 支持一下！

Made with ❤️ for BIT Students.

</div>
