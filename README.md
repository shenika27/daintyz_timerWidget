# daintyz_timerWidget — 캐릭터 타이머 위젯 앱

안드로이드 홈 화면 위젯 형태의 "캐릭터 타이머" 앱.
타이머가 진행되는 동안 위젯 안의 캐릭터가 런닝머신 위에서 뛰는 애니메이션을 보여준다.

> 설계 상세는 [`docs/캐릭터타이머_위젯_구현프롬프트_v2.2.md`](docs/캐릭터타이머_위젯_구현프롬프트_v2.2.md) 참고.

## 현재 상태

**기초 폴더 구조만 잡힌 단계입니다. 실제 기능 구현은 아직 시작되지 않았습니다.**
대부분의 파일은 TODO 주석과 설계 의도만 적힌 placeholder입니다.

## 기술 스택 (확정)

- 언어: Kotlin
- 위젯 방식: `AppWidgetProvider` + `RemoteViews` (1fps 프레임 교체, Jetpack Glance 미사용)
- minSdk 26 / targetSdk 35
- 상태 저장: `SharedPreferences`

## 프로젝트 구조

```
app/
├── src/main/
│   ├── java/com/daintyz/timerwidget/
│   │   ├── widget/      # AppWidgetProvider, RemoteViews 갱신
│   │   ├── service/     # 포그라운드 서비스 (1초 틱, 시간 추적)
│   │   ├── receiver/    # 화면 ON/OFF, 부팅 복구, 위젯 버튼 액션
│   │   ├── skin/        # 스킨 로딩/가용성 판단/프레임 애니메이션
│   │   ├── data/        # SharedPreferences 영속화
│   │   ├── model/       # 상태/스킨 데이터 모델
│   │   └── ui/          # 메인 화면, 스킨 선택 화면
│   ├── res/
│   │   ├── layout/      # widget_timer_top / widget_timer_bottom 등
│   │   └── xml/         # widget_info.xml (AppWidgetProviderInfo)
│   └── assets/skins/    # 스킨 세트 (potato=기본무료, space=예시)
└── build.gradle.kts
docs/                    # 설계 문서 원본
```

## 핵심 설계 메모

- **레이아웃 분리**: 타이머 위/아래 배치를 `widget_timer_top.xml` / `widget_timer_bottom.xml`
  두 벌로 분리. 두 레이아웃은 동일한 view id를 공유해야 한다.
- **프레임 갱신**: 포그라운드 서비스 내부 Handler/코루틴 1초 틱. `AlarmManager`의
  정밀 알람은 프레임 갱신용으로 사용하지 않는다 (시스템 throttle 문제).
- **스킨 시스템**: `assets/skins/<skinId>/skin.json` + PNG 프레임. 신규 스킨은
  코드 수정 없이 폴더 추가만으로 확장 가능하도록 설계.
- **결제 자리 마련**: `SkinAvailabilityChecker` 한 곳에서만 가용성 판단 로직을
  바꾸면 결제 연동이 끝나도록 미리 분리.

## 1차 버전 개발 범위

설계 문서 7장 참고. 결제 로직, 컨디션 분기, 추가 유료 스킨, 다중 위젯 운영,
리사이즈 대응은 1차 버전에서 제외.
