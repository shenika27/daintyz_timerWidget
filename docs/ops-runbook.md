# 운영 런북 (Ops Runbook)

캐릭터 타이머 위젯 — 출시 후 유지보수·릴리스·장애·재해복구 절차. 즉흥 대응 방지용.

관련 인프라 한눈에:
- **앱**: `com.daintyz.timerwidget` (앱 레포 `daintyz_timerWidget`)
- **결제 검증 Worker**: `https://daintyz-billing.xornexon.workers.dev` (Cloudflare)
- **유료 zip 저장소**: R2 버킷 `daintyz-skins`, 키 형식 `{skinId}.zip`
- **개발자 알림 릴레이**: `https://daintyz-alert.xornexon.workers.dev` → 디스코드 (DevAlert)
- **디자인/스킨 레포**: `daintyz_timer_characterList` (`_inbox` 업로드 → CI 자동배치)

---

## 1. 앱 릴리스 절차

새 버전 낼 때마다:
1. `app/build.gradle.kts`에서 **`versionCode` +1** (필수 — Play는 같은 versionCode 재업로드 거부), `versionName` 갱신
2. 서명 AAB 빌드:
   ```
   cd C:\Users\user5\Documents\daintyz\daintyz_timerWidget
   ./gradlew bundleRelease
   ```
   산출물: `app/build/outputs/bundle/release/app-release.aab` (업로드 키로 서명됨)
3. Play Console → 해당 트랙(내부/클로즈드/프로덕션)에 AAB 업로드 → 출시 노트 작성 → 검토 제출
4. 프로덕션은 단계적 출시(%) 권장 — 문제 시 롤아웃 정지 가능

> 서명: `keystore.properties` + `upload-keystore.jks`(레포 루트, **둘 다 gitignore**)가 있어야 서명됨.
> 없으면 미서명 AAB가 나와 업로드 거부됨.

## 2. 유지보수 캘린더 (구글이 매년/주기적으로 강제하는 것)

| 항목 | 주기 | 대응 |
|------|------|------|
| **targetSdk 상향** | 매년(보통 8월 마감) | 새 Android API 레벨로 `targetSdk` 올리고 회귀 테스트 |
| **Play Billing Library 버전 만료** | 릴리스 후 ~2년 | `billing-ktx` 최신으로 상향(breaking change 주의). 현재 7.1.1 |
| **개인정보/Data safety 정확성** | 데이터 흐름 변경 시 | SDK·외부전송 추가/제거 때마다 `docs/play-submission/` 갱신 |
| **민감 권한 재확인** | 정책 변경 시 | FGS specialUse 선언 유지(`docs/play-submission/01`) |

## 3. 유료 스킨 정합성 규칙 (⚠️ 깨지면 구매 불능)

유료 스킨 하나는 **3곳이 항상 일치**해야 한다:
1. **Play Console** 인앱상품 `skin_<skinId>` 등록 + "활성"
2. **catalog.json** 해당 항목에 `productId: "skin_<skinId>"` + `price > 0`
3. **R2** `daintyz-skins/{skinId}.zip` 존재 (공개 `character/zip/`에는 **없어야** 함)

하나라도 어긋나면:
- SKU 없음 → 구매 버튼이 "결제 준비 중" stub로 빠짐
- productId 없음 → 위와 동일 (앱이 구매 흐름 못 탐)
- R2 zip 없음 → 구매는 되는데 다운로드가 Worker에서 `zip_not_found`
- 공개 zip 잔존 → 유료인데 누구나 무료로 받음(보호 깨짐)

**정상 운영 흐름**: 빌더로 유료 스킨(가격 입력) → `_inbox` 업로드 → CI가 자동으로 R2 배치 + 공개 zip 제외 + catalog에 productId 기입. **남는 수동 작업은 Play Console SKU 등록뿐.**

> 점검 팁: 의심되면 catalog의 모든 `price>0` 항목에 대해 productId 유무 + R2 객체 존재(`wrangler r2 object get`)를 확인. (추후 자동 점검 스크립트화 가능)

## 4. 재해복구 (DR) — 잃으면 치명적인 것

| 자산 | 유일본 위치 | 백업처 |
|------|-------------|--------|
| **업로드 키스토어** | 레포 루트 `upload-keystore.jks` + 비번 | **비밀번호 관리자/오프라인** (분실 시 Play에 업로드키 재설정 요청 — 가능하나 번거로움) |
| **유료 스킨 zip** | **R2가 유일본**(공개 레포엔 없음) | 별도 사본 필수(로컬/개인 클라우드). R2 날아가면 상품 자체 소실 |
| **Worker 시크릿** | Cloudflare(GOOGLE_SERVICE_ACCOUNT_JSON) | GCP 서비스계정 JSON 원본 보관 |
| **스킨 원본 소스** | 디자이너 로컬 | 디자인 레포 또는 개인 백업 |

→ **유료 zip 백업이 가장 자주 잊는 구멍.** 빌더가 만든 번들 zip(또는 R2 업로드본)을 개인 저장소에 보관할 것.

## 5. 감시 계획 (결제 연결 시 추가)

모든 유료 다운로드·구매검증이 **Worker를 통과**하므로, 앱에 SDK 없이 Worker에서 감시 가능:
- **매출 신호**: 스킨별 다운로드/검증 카운트 로깅 (PII 없음)
- **장애 알림**: `server_auth_failed`(서비스계정 깨짐)·5xx·`zip_not_found` 급증 시 DevAlert 릴레이로 디스코드 알림
- **구현 시점**: 결제가 실제 연결된 뒤(트래픽 생긴 뒤). 지금은 측정 대상이 없어 보류.

기본 지표는 **Play Console(매출·환불·Android vitals 크래시/ANR)** 가 무료로 제공 → 분석/크래시 SDK 추가 불필요(개인정보 비용만 발생).

## 6. 환불·고객지원

- **환불 시 권한 회수**: 앱이 시작마다 `syncEntitlements`로 Play 기준 전체 재동기화 → 환불된 구매는 자동 회수됨. (선물 해금은 로컬이라 회수 안 됨 — 의도된 분리)
- **"샀는데 안 받아져요"**: 설정의 **구매 복원** 안내(= `queryPurchases` 재호출). 같은 구글 계정이면 복구됨.
- **지원 채널**: 스토어 등록의 지원 이메일. 자주 묻는 질문(복원/기기변경/환불)은 캔드 응답 준비 권장.

---

_최초 작성 2026-06-28. 인프라/정책 변경 시 갱신._
