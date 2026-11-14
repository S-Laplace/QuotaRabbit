# QuotaRabbit

<p align="center"><img src="./docs/assets/readme/hero.png" alt="QuotaRabbit 홈 화면과 계정 화면을 보여주는 소개 이미지" width="100%"></p>

<p align="center"><a href="./README.md">한국어</a> · <a href="./README.en.md">English</a> · <a href="./README.zh-CN.md">简体中文</a></p>

<p align="center"><a href="./LICENSE"><img src="https://img.shields.io/badge/License-MIT-222222" alt="MIT 라이선스"></a> <img src="https://img.shields.io/badge/Android-12%2B-3DDC84" alt="Android 12 이상"></p>

QuotaRabbit은 Codex를 비롯한 11개 AI 서비스의 사용 한도와 잔액을 Android에서 확인하는 앱입니다. 계정을 연결하면 남은 한도와 재설정 시간을 한곳에서 볼 수 있습니다. 자주 확인하는 정보는 홈 화면 위젯이나 알림에도 표시할 수 있습니다.

**[APK 다운로드 (v1.0.0)](https://github.com/S-Laplace/QuotaRabbit/releases/download/v1.0.0/quotarabbit-1.0.0.apk)** · [릴리스 목록](https://github.com/S-Laplace/QuotaRabbit/releases)

## 화면

<p align="center"><img src="./docs/assets/readme/dashboard.png" alt="남은 한도와 계정별 상태를 보여주는 홈·계정 화면" width="100%"></p>

<p align="center"><img src="./docs/assets/readme/analytics.png" alt="Codex 기능별·모델별 사용량 분석 화면" width="100%"></p>

*이미지의 계정과 수치는 화면을 설명하기 위한 예시입니다.*

## 주요 기능

### 계정과 사용 한도

등록한 계정을 서비스별로 모아 보고, 계정 카드를 펼쳐 최근 사용 기록을 확인할 수 있습니다. 같은 서비스의 계정을 여러 개 등록하거나 현재 사용할 계정을 지정할 수도 있습니다. 서비스에 따라 남은 한도, 재설정 시간, 잔액 등 표시되는 정보는 다릅니다.

### 사용량 분석

Codex 계정에서는 최근 7일 또는 30일의 모델별·기능별 사용량을 볼 수 있습니다. 이 상세 분석은 현재 Codex에 한정되며, 서비스에서 제공하지 않는 수치는 임의로 채우지 않습니다.

### 위젯과 알림

크기를 조절할 수 있는 홈 화면 위젯에서 원하는 계정과 한도 항목을 선택할 수 있습니다. 한도가 부족해질 때 알림을 받거나 현재 상태를 알림 영역에 표시할 수도 있습니다.

## 지원 서비스

| 연결 방법 | 서비스 |
| --- | --- |
| 기기 코드 인증 | Codex, Grok |
| OAuth 로그인 | Claude, Antigravity(Google) |
| API 키 | DeepSeek, z.ai Coding Plan, z.ai API, MiniMax, Kimi Code API |
| 앱 내 로그인 | Cursor, Kimi |

서비스마다 로그인 방법과 확인할 수 있는 사용량 정보가 다릅니다. 인증이 만료되면 계정을 다시 연결해야 할 수 있습니다.

## 설치

Android 12 이상이 필요합니다.

1. [GitHub Releases](https://github.com/S-Laplace/QuotaRabbit/releases)에서 APK를 다운로드합니다.
2. 휴대전화에서 APK를 열어 설치합니다. Android에서 요청하면 브라우저나 파일 관리자의 앱 설치 권한을 허용합니다.
3. 앱을 실행하고 사용하는 서비스의 계정을 연결합니다.

이미 설치한 앱을 업데이트할 때는 이전 버전과 같은 서명 키로 만든 APK가 필요합니다.

## 알아둘 점

사용량 정보는 각 서비스에서 제공하는 데이터를 기준으로 표시됩니다. 약 15분 간격으로 새로고침을 예약하지만, Android 절전 기능에 따라 실제 갱신이 늦어질 수 있습니다. 필요하면 앱에서 직접 새로고침하세요. 서비스의 인증 방식이나 API가 바뀌면 일부 기능을 이용하지 못할 수도 있습니다.

## 개인정보

계정 연결 정보와 사용 기록은 기기에 보관하며, 계정 연결 정보는 암호화해 저장합니다. 클라우드 동기화, 광고 SDK, 원격 사용 분석 기능은 없습니다. 로그인과 사용량 조회를 위해서는 각 서비스와 통신해야 합니다.

## 라이선스와 출처

QuotaRabbit은 [CodexMeter](https://github.com/KyoMio/CodexMeter)를 바탕으로 수정·확장한 앱입니다. 원저작자의 저작권 고지와 MIT 라이선스는 [LICENSE](LICENSE)에 유지했습니다. 일부 서비스의 응답 처리에는 [CodexBar](https://github.com/steipete/CodexBar)를 참고했습니다.

QuotaRabbit은 지원 서비스의 공식 앱이 아닙니다.
