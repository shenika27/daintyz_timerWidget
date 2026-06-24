# daintyz_timerWidget — 캐릭터 타이머 위젯 앱

안드로이드 홈 화면 위젯 형태의 "캐릭터 타이머" 앱.
타이머가 진행되는 동안 위젯 안의 캐릭터가 런닝머신 위에서 뛰는 애니메이션을 보여준다.

> 설계 상세는 [`docs/캐릭터타이머_위젯_구현프롬프트_v2.2.md`](docs/캐릭터타이머_위젯_구현프롬프트_v2.2.md) 참고.

## 현재 상태

위젯 동작·스킨 시스템·미리보기/상점이 구현된 단계입니다.

- **타이머 영역(가로 배치)**: 왼쪽 시간(~80%) + 오른쪽 버튼 2개 세로 컬럼(~20%). 시간 영역 탭 =
  시작/일시정지/재개. 버튼은 항상 2개(정지/완료=`+`/`−`, 진행/일시정지=재생·일시정지/정지).
- **위젯 크기**: 2x2 한 칸 안에서 타이머 ~30% / 캐릭터 ~70%.
- **스킨 테마**: 박스·구분선은 스킨의 `timer_theme` 배경 PNG에 포함, 버튼 5종 + 상황별 캐릭터로 구성.
  렌더링/스킨 계약 상세는 [`docs/타이머영역_렌더링_제약.md`](docs/타이머영역_렌더링_제약.md) 참고.

## 기술 스택 (확정)

- 언어: Kotlin
- 위젯 방식: `AppWidgetProvider` + `RemoteViews` (1fps 프레임 교체, Jetpack Glance 미사용)
- minSdk 26 / targetSdk 35 / compileSdk 36
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
