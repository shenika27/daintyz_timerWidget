# daintyz_timerWidget — 캐릭터 타이머 위젯 앱!!

안드로이드 홈 화면 위젯 형태의 "캐릭터 타이머" 앱.
타이머가 진행되는 동안 위젯 안의 캐릭터가 런닝머신 위에서 뛰는 애니메이션을 보여준다.

> 설계 상세는 [`docs/캐릭터타이머_위젯_구현프롬프트_v2.2.md`](docs/캐릭터타이머_위젯_구현프롬프트_v2.2.md) 참고.

## 현재 상태

위젯 동작·스킨 시스템·테마 상점/미리보기·앱 셸이 구현된 단계입니다. (결제 연동 제외)

- **타이머 영역(가로 배치)**: 왼쪽 시간(~80%) + 오른쪽 버튼 2개 세로 컬럼(~20%). 시간 영역 탭 =
  시작/일시정지/재개. 버튼은 항상 2개(정지/완료=`+`/`−`, 진행/일시정지=재생·일시정지/정지).
- **위젯 크기**: 2x2 한 칸 안에서 타이머 ~30% / 캐릭터 ~70%.
- **스킨 테마**: 박스·구분선은 스킨의 `timer_theme` 배경 PNG에 포함, 버튼 5종 + 상황별 캐릭터로 구성.
  렌더링/스킨 계약 상세는 [`docs/타이머영역_렌더링_제약.md`](docs/타이머영역_렌더링_제약.md) 참고.
- **앱 화면(Compose)**: 하단 탭 셸(보유 / 상점 / 설정). 카드 탭 → 상세/미리보기 화면.
  - **보유(창고)**: 보유·미보유 테마 커버플로우 캐러셀. 적용=캐릭터+타이머 동시 적용.
  - **상점**: 디자인 레포 카탈로그 기반 히어로 카드 리스트(내장 기본 에셋은 제외). NEW·프리스티지 배지,
    찜(위시리스트), 한정판매 기간(미출시/판매중/판매종료).
  - **상세/미리보기**: 보유 테마는 '실제 위젯'을 그대로 띄운 인터랙티브 미리보기(샌드박스),
    미보유 테마는 prevNN 홍보 이미지 캐러셀.
  - **설정**: 한 번에 조절(증감) 단위, 위젯 배치, 완료음/진동, 시스템 알림 설정 딥링크,
    앱 글꼴(기본/시스템), 위젯 새로고침, 기프트코드 해금, 앱 버전.

## 기술 스택 (확정)

- 언어: Kotlin
- 앱 화면: **Jetpack Compose** (Material3) — 하단 탭 셸 + 상세 화면. 위젯은 별개(아래).
- 위젯 방식: `AppWidgetProvider` + `RemoteViews` (1fps 프레임 교체, Jetpack Glance 미사용)
- 이미지 로딩: Coil (원격 썸네일/미리보기)
- minSdk 26 / targetSdk 35 / compileSdk 36
- 상태 저장: `SharedPreferences`
- 스킨 배포: 정적 CDN (GitHub raw catalog + jsDelivr 에셋). 자체 서버/DB 없음.

## 프로젝트 구조

```
app/
├── src/main/
│   ├── java/com/daintyz/timerwidget/
│   │   ├── controller/  # TimerController (모든 타이머 상태 전환 단일 출처)
│   │   ├── widget/      # AppWidgetProvider, RemoteViews 갱신
│   │   ├── service/     # 포그라운드 서비스 (1초 틱, 시간 추적)
│   │   ├── receiver/    # 화면 ON/OFF, 부팅 복구, 위젯 버튼 액션
│   │   ├── notification/# 알림 채널 + 완료음·진동 피드백
│   │   ├── skin/        # 스킨 로딩/가용성/프레임 애니메이션, 카탈로그 다운로드, 기프트코드
│   │   ├── data/        # SharedPreferences 영속화
│   │   ├── model/       # 상태/스킨 데이터 모델
│   │   └── ui/          # 액티비티(Main/Detail/WidgetConfigure) + compose/ 화면들
│   ├── res/
│   │   ├── layout/      # widget_timer_top / widget_timer_bottom 등 (위젯 RemoteViews)
│   │   └── xml/         # widget_info.xml (AppWidgetProviderInfo)
│   └── assets/skins/    # 내장 기본 스킨 (cha01). 그 외 테마는 디자인 레포에서 다운로드.
└── build.gradle.kts
docs/                    # 설계 문서 원본
```

> 디자인/에셋은 별도 레포(`daintyz_timer_characterList`)에서 관리한다. catalog.json + 캐릭터 zip/
> 미리보기를 거기서 받아온다. 스킨 작업은 **앱·디자인 레포 양쪽을 함께** 봐야 한다.

## 핵심 설계 메모

- **상태 전환 단일화**: 위젯 버튼·앱 화면·서비스(만료 감지)가 모두 `TimerController`의 함수를 호출한다.
  각 전환은 (1) prefs 갱신 (2) 포그라운드 서비스 시작/정지 (3) 위젯 즉시 갱신을 일관되게 수행.
- **레이아웃 분리**: 타이머 위/아래 배치를 `widget_timer_top.xml` / `widget_timer_bottom.xml`
  두 벌로 분리. 두 레이아웃은 동일한 view id를 공유해야 한다.
- **프레임 갱신**: 포그라운드 서비스 내부 1초 틱. `AlarmManager`의 정밀 알람은 프레임 갱신용으로
  사용하지 않는다 (시스템 throttle 문제).
- **스킨 시스템**: `assets/skins/<skinId>/skin.json` + PNG 프레임. 신규 스킨은 코드 수정 없이
  폴더/카탈로그 추가만으로 확장 가능하도록 설계.
- **가용성 판단 단일 지점**: `SkinAvailabilityChecker` 한 곳에서만 가용성(보유/구매/평생권)을 판단해
  결제 연동이 끝나도 이 지점만 바꾸면 되도록 분리.
- **카탈로그 내성**: 항목 단위 격리 파싱 — 한 테마 항목이 깨져도 그것만 스킵하고 나머지는 노출.
  카탈로그가 깨지면 개발자에게 원격 알림(`DevAlert`). 웹훅을 APK에 박지 않도록 릴레이
  엔드포인트(Cloudflare Worker)로 분리 — 아래 [외부 서비스 / 인프라](#외부-서비스--인프라) 참고.

## 외부 서비스 / 인프라

> 계정/엔드포인트 참조용. **비밀번호·토큰·웹훅 URL 등 시크릿은 여기 적지 않는다**(각 서비스 대시보드/시크릿 저장소에만).

- **디자인/에셋 레포**: `github.com/shenika27/daintyz_timer_characterList`
  (catalog.json + 캐릭터 zip/미리보기, GitHub raw + jsDelivr CDN으로 배포)
- **DevAlert 릴레이 (Cloudflare Worker)**:
  - 계정: `xornexon@gmail.com`
  - 엔드포인트: `https://daintyz-alert.xornexon.workers.dev/`
  - 역할: 앱이 보낸 카탈로그 깨짐 알림을 디스코드로 전달. 실제 디스코드 웹훅은 워커
    환경변수(시크릿) `DISCORD_WEBHOOK`에만 있고 APK엔 위 릴레이 URL만 들어간다.
    `@everyone`/`@here` 무력화 + 레이트리밋(1분 20건) 처리.
  - 앱 연동: `skin/DevAlert.kt` → `{type, text}` JSON POST.

## 1차 버전 개발 범위

설계 문서 7장 참고. **결제 로직**, 컨디션 분기, 다중 위젯 운영, 리사이즈 대응은 1차 버전에서 제외.
(찜/한정판매/기프트코드/인기 정렬용 스키마는 결제 없이도 동작하도록 미리 들어가 있음.)

## 향후 작업 (TODO)

### 인기테마 산정 (테마 적용 횟수 기반)

"인기테마"를 **다운로드/구매 수가 아니라, 사용자가 실제로 타이머에 적용한 횟수**로
산정한다(더 정확한 engagement 신호).

- **계측 지점**: `TimerController.selectCharacterSkin` / `selectTimerSkin` 두 곳뿐.
  여기서 적용된 `skinId`를 누적하면 된다.
- **단계 전략 (출처만 바꾸고 화면은 고정)**:
  - **① 로컬 누적 — 백엔드 0, 지금 가능**: 적용 시 `skinId → count`를
    `SharedPreferences`에 ++. 이 기기 기준 "자주 쓴 테마" 정렬에 바로 사용.
    한계: 기기별 통계라 전역 "인기"는 안 됨.
  - **② 전역 집계 — 관리형 BaaS 필요**: 각 기기의 적용 이벤트를 한 곳에 모아야
    전체 사용자 기준 순위가 된다. ①의 누적 데이터가 그대로 서버로 flush할
    payload가 되도록 이벤트 스키마를 맞춰 둔다.
  - 화면(상점/적용 목록)은 **`popularity` 정렬 키 하나만** 바라보게 한다.
    데이터 출처가 로컬 → 전역으로 바뀌어도 store 코드는 불변
    (`price`/`prestige` 확장과 동일 패턴).
- **백엔드 = 자체 서버/DB 직접 운영이 아님**. 현재는 순수 정적 CDN 구조
  (GitHub raw catalog + jsDelivr 에셋, 쓰기 동작 0)라 전역 집계만 외부가 필요:
  - **Firebase Firestore/Realtime DB (권장)**: 구글 관리형, 서버·DB 운영 0,
    안드로이드 SDK 일급 지원, 무료 한도. 적용 시 `skins/{id}.count` 를
    `increment(1)` 한 줄. 현재 구조에 얹기 가장 자연스러움.
    상점은 집계 카운트를 읽어 인기순 정렬.
  - 대안: Cloudflare Workers + KV/D1(초경량 서버리스 엔드포인트), Supabase 등.
  - **불필요**: 자체 서버 + DB 직접 구축/운영.
- **고려사항**: ⓐ 사용 이벤트를 외부로 전송 → 개인정보/동의 처리,
  ⓑ 클라이언트가 카운트를 부풀릴 수 있음(완벽 방지는 어려움, 가벼운 앱엔 보통 감수).
- **엔드포인트 재사용**: 집계용 서버리스 엔드포인트는 이미 떠 있는 DevAlert 릴레이(Cloudflare
  Worker)와 같은 자리에 둘 수 있다([외부 서비스 / 인프라](#외부-서비스--인프라) 참고).
