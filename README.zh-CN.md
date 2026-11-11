# QuotaRabbit

<p align="center"><img src="./docs/assets/readme/hero.png" alt="QuotaRabbit 应用图标以及首页、账户页面的界面预览" width="100%"></p>

<p align="center"><a href="./README.md">한국어</a> · <a href="./README.en.md">English</a> · <a href="./README.zh-CN.md">简体中文</a></p>

<p align="center"><a href="./LICENSE"><img src="https://img.shields.io/badge/License-MIT-222222" alt="MIT License"></a> <img src="https://img.shields.io/badge/Android-12%2B-3DDC84" alt="Android 12 or newer"></p>

QuotaRabbit 是一款**用来在 Android 手机上查看 AI 服务额度和余额的应用**。添加 Codex 等 11 个服务的账号后，就能集中查看剩余额度、重置倒计时和刷新状态。桌面小组件和通知栏也可以显示常用信息。

[APK 发布页面](https://github.com/S-Laplace/QuotaRabbit/releases) · [源代码](https://github.com/S-Laplace/QuotaRabbit)

## 界面预览

<p align="center"><img src="./docs/assets/readme/dashboard.png" alt="QuotaRabbit 首页与账户页面：剩余额度、重置时间和多账号管理" width="100%"></p>

*图片中的账号与数值仅供演示，并非实时用量数据。*

## 主要功能

### 账号与额度

首页按服务商显示已添加的账号。展开账号卡片后，可以查看近期用量和活动记录。根据服务商返回的信息，页面会显示额度百分比、距离重置的时间或账户余额。

在账户页面，你可以为同一个服务商添加多个账号，并修改名称、强调色和通知设置。每个服务商还可以指定当前账号。刷新失败时，应用会保留上一次成功获取的数据，同时显示刷新状态。

### Codex 用量分析

Codex 账号可以查看最近 7 天或 30 天的模型用量、功能用量和相关 Token 数据。接口未返回的日期或数值不会用估算数据补齐。其他服务商不一定支持这项详细分析。

<p align="center"><img src="./docs/assets/readme/analytics.png" alt="Codex 用量分析示例：功能用量以及模型用量柱状图" width="100%"></p>

*分析图中的数据同样仅供演示。*

### 小组件与通知

桌面小组件可以调整大小，也能选择要显示的账号和额度字段。常驻状态通知和额度预警可在设置中管理。

应用默认使用 WorkManager 大约每 15 分钟安排一次后台刷新。Android 的省电限制可能导致任务延迟。需要最新数据时，可以在应用内手动刷新。

## 支持的服务商

| 服务商 | 连接方式 |
| --- | --- |
| Codex | 在外部浏览器中使用设备码授权 |
| Claude | OAuth |
| Antigravity | Google OAuth |
| DeepSeek | API 密钥 |
| z.ai Coding Plan | API 密钥 |
| z.ai API | API 密钥 |
| MiniMax | API 密钥 |
| Cursor | 应用内登录会话 |
| Kimi | 应用内登录会话 |
| Kimi Code API | API 密钥 |
| Grok | 在外部浏览器中使用设备码授权 |

各服务商的登录方式和可用数据并不相同。登录过期或 API 变化时，部分信息可能无法显示。QuotaRabbit 不会自行估算 Token 用量或费用。

## 安装

需要 **Android 12 或更高版本**。

1. 从 [GitHub Releases](https://github.com/S-Laplace/QuotaRabbit/releases) 下载已签名的 APK。
2. 在手机上打开 APK。如有提示，请允许浏览器或文件管理器安装来自此来源的应用。
3. 启动 QuotaRabbit 并连接账号。

如果新建的仓库尚未发布 APK，可以按照下方步骤自行构建。升级已有安装时，新 APK 必须使用与旧版本相同的签名密钥。

## 从源码构建

需要 Android Studio 提供的 JDK 环境、Android SDK 37 和仓库自带的 Gradle Wrapper。在 Windows 上，进入包含 `settings.gradle.kts` 的目录并运行：

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
```

Debug APK 会生成在 `app/build/outputs/apk/debug/`。运行 `.\gradlew.bat :app:assembleRelease` 可以构建 Release，但分发前必须完成签名。签名配置可使用 `RELEASE_KEYSTORE_PATH`、`RELEASE_STORE_PASSWORD`、`RELEASE_KEY_ALIAS` 和 `RELEASE_KEY_PASSWORD` 环境变量。请勿将密钥库或密码提交到仓库。

Android 应用包名为 `com.alice.quotarabbit`。

## 数据与隐私

账号会话通过 Android Keystore 支持的 AES-GCM 加密后保存在设备上。用量历史和设置也存放在本地。应用不包含云同步、广告 SDK 或远程分析收集功能。

登录和查询额度时，应用需要与对应服务商联网通信。接口变化、登录状态及 Android 后台运行限制都可能影响显示结果。Codex 详细分析使用的部分接口并非稳定的公开 API，将来可能发生变化。

## 许可证与致谢

QuotaRabbit 基于 [CodexMeter](https://github.com/KyoMio/CodexMeter) 修改和扩展了界面及功能。原作者的版权声明和 MIT 许可证保留在 [LICENSE](LICENSE) 中。部分服务商的响应处理参考了 [CodexBar](https://github.com/steipete/CodexBar)。

QuotaRabbit 不是这些 AI 服务的官方应用。
