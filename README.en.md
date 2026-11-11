# QuotaRabbit

<p align="center"><img src="./docs/assets/readme/hero.png" alt="QuotaRabbit app icon with previews of the Home and Accounts screens" width="100%"></p>

<p align="center"><a href="./README.md">한국어</a> · <a href="./README.en.md">English</a> · <a href="./README.zh-CN.md">简体中文</a></p>

<p align="center"><a href="./LICENSE"><img src="https://img.shields.io/badge/License-MIT-222222" alt="MIT License"></a> <img src="https://img.shields.io/badge/Android-12%2B-3DDC84" alt="Android 12 or newer"></p>

QuotaRabbit is **an Android app for checking your AI service quotas and balances**. Add accounts from Codex and 10 other services to see remaining quotas, reset countdowns and refresh status in one place. You can also check important numbers in a home-screen widget or notification.

[APK releases](https://github.com/S-Laplace/QuotaRabbit/releases) · [Source code](https://github.com/S-Laplace/QuotaRabbit)

## Screenshots

<p align="center"><img src="./docs/assets/readme/dashboard.png" alt="QuotaRabbit Home and Accounts previews showing available quota, reset countdowns and account management" width="100%"></p>

*The accounts and figures in the images are examples, not live usage data.*

## Features

### Accounts and quotas

The Home tab groups saved accounts by service. Expand an account card to see recent usage and activity. Depending on the provider, you may see a quota percentage, time until reset or account balance.

In the Accounts tab, you can add multiple accounts for the same service and manage names, accent colors and notification settings. You can also choose a current account for each provider. If a refresh fails, the app keeps the last successful reading and shows its refresh status.

### Codex usage analytics

Codex accounts can display model and feature usage over 7- or 30-day periods, along with token-related figures. Missing dates and values aren't filled in with estimates. This detailed view isn't available for every service.

<p align="center"><img src="./docs/assets/readme/analytics.png" alt="Example Codex analytics with feature usage and a model usage bar chart" width="100%"></p>

*The analytics image also uses sample data.*

### Widgets and notifications

The resizable home-screen widget lets you choose which accounts and quota fields to display. Persistent status notifications and quota alerts are available in Settings.

WorkManager schedules background refreshes roughly every 15 minutes by default. Android battery-saving restrictions may delay them. You can refresh manually whenever you need a newer reading.

## Supported services

| Service | Sign-in method |
| --- | --- |
| Codex | Device-code authorization in an external browser |
| Claude | OAuth |
| Antigravity | Google OAuth |
| DeepSeek | API key |
| z.ai Coding Plan | API key |
| z.ai API | API key |
| MiniMax | API key |
| Cursor | In-app sign-in session |
| Kimi | In-app sign-in session |
| Kimi Code API | API key |
| Grok | Device-code authorization in an external browser |

Login methods and available usage fields differ between services. Expired sessions or API changes can prevent some information from appearing. QuotaRabbit does not calculate estimated token usage or costs on its own.

## Installation

Requires **Android 12 or later**.

1. Download a signed APK from [GitHub Releases](https://github.com/S-Laplace/QuotaRabbit/releases).
2. Open the APK on your phone. If prompted, allow the browser or file manager to install apps from this source.
3. Launch QuotaRabbit and connect an account.

If the recreated repository does not have an APK release yet, you can build from source below. Updating an existing installation requires an APK signed with the same key as the previous version.

## Build from source

You'll need Android Studio's JDK environment, Android SDK 37 and the Gradle Wrapper included in this repository. On Windows, run these commands from the directory containing `settings.gradle.kts`:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
```

The debug APK is written to `app/build/outputs/apk/debug/`. Use `.\gradlew.bat :app:assembleRelease` for a release build, which must be signed before distribution. Signing can be configured using `RELEASE_KEYSTORE_PATH`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS` and `RELEASE_KEY_PASSWORD`. Never commit keystores or passwords.

The Android application ID is `com.alice.quotarabbit`.

## Data and privacy

Account sessions are encrypted on the device using Android Keystore-backed AES-GCM. Usage history and settings also stay on the device. The app does not include cloud sync, advertising SDKs or remote analytics collection.

Authentication and quota requests require a network connection to the relevant provider. API changes, session status and Android background restrictions can affect the readings. Some endpoints used for detailed Codex analytics are not part of a stable public API and may change.

## License and credits

QuotaRabbit builds on [CodexMeter](https://github.com/KyoMio/CodexMeter), with changes and additions to its interface and features. The original copyright notice and MIT license remain in [LICENSE](LICENSE). Some provider response handling draws on [CodexBar](https://github.com/steipete/CodexBar).

QuotaRabbit is not an official app of the supported AI services.
