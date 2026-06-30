# daintyz 결제 검증 Worker

유료 스킨 zip을 **공개 CDN에 두지 않고**, "구매한 사람에게만" 내려주는 Cloudflare Worker.
앱이 Google Play 영수증(purchaseToken)을 보내면, Worker가 Google에 진위를 물어보고 통과한 경우에만
비공개 R2 버킷의 zip을 스트리밍한다.

```
앱 ──POST {skinId, purchaseToken}──▶ Worker ──검증──▶ Google Play Developer API
                                       │ 통과
                                       ▼
                                   비공개 R2(zip) ──스트리밍──▶ 앱
```

권한 규칙(BM: 개별구매 + 평생이용권 + 평생이용권 기프트)
- 그 스킨의 `productId` 토큰이 **구매완료** → 통과
- 또는 (프리스티지가 아니면) **평생이용권**(`lifetime_pass`) 토큰이 구매완료 → 통과
- 또는 (프리스티지가 아니면) Worker가 발급한 **평생이용권 기프트 토큰**(`giftPassToken`) 서명이 유효 → 통과
- 무료 스킨은 검증 없이 통과

---

## 코드는 이미 작성됨 / 아래는 "로그인 후" 셋업

이 폴더의 코드(`src/index.js`)는 로그인 없이 바로 쓸 수 있게 다 짜여 있다.
아래 체크리스트는 **각 단계마다 필요한 로그인**을 표시한다. 로그인 후 순서대로 따라가면 된다.

| 단계 | 필요 로그인 |
|---|---|
| 1. R2 버킷 생성 + zip 업로드 | Cloudflare |
| 2. GCP 서비스계정 발급 + API 활성화 | Google Cloud |
| 3. 서비스계정을 Play에 연결 + 권한 부여 | **Play Console** |
| 4. Worker 설정·비밀 등록·배포 | Cloudflare |
| 5. 테스트 | Play Console(라이선스 테스터) |

---

## 사전 준비(로컬 도구)

```bash
cd infra/billing-worker
npm install          # wrangler 설치
npx wrangler login   # 브라우저로 Cloudflare 로그인
```

## 1. R2 버킷 (Cloudflare)

```bash
npx wrangler r2 bucket create daintyz-skins
```
- 버킷 **공개 접근은 끈 채로 둔다**(Public access OFF). Worker만 읽게 한다 — 그래야 보호된다.
- `wrangler.toml`의 `[[r2_buckets]] bucket_name` 과 이름이 일치해야 한다.

### 1-1. 유료 zip 업로드
zip 파일명은 **`{skinId}.zip`** (예: `cloud.zip`). Worker가 이 키로 찾는다.

```bash
npx wrangler r2 object put daintyz-skins/cloud.zip --file=./cloud.zip
```
- 무료 스킨은 기존 공개 CDN(jsDelivr)에 그대로 두면 된다. R2엔 **유료/프리스티지만** 올린다.
- 신규 유료 스킨 출시 자동화(디자인레포 `skin-deploy.yml`)가 **R2 업로드 + Play 인앱상품 등록까지 자동**으로 한다 —
  유료 zip 수동 업로드/SKU 수동 생성은 더 안 해도 된다(디자인레포 README "유료 스킨 → Play 인앱상품 자동 등록" 참고).

## 2. GCP 서비스계정 (Google Cloud)

1. https://console.cloud.google.com → 프로젝트 선택(또는 새로 생성)
2. **APIs & Services → Library → "Google Play Android Developer API"** 활성화(Enable)
3. **IAM & Admin → Service Accounts → Create service account**
   - 이름 예: `daintyz-billing-verifier`
   - 역할(role)은 일단 없이 생성해도 된다(실제 권한은 3단계에서 Play Console이 부여)
4. 만든 서비스계정 → **Keys → Add key → JSON** → 키 파일 다운로드
   - 이 JSON에 `client_email`, `private_key`가 들어있다. **절대 커밋 금지**(4단계에서 secret으로만 등록).

## 3. 서비스계정을 Play에 연결 (**Play Console**)

> 여기서부터 Play Console 로그인이 필요하다.

1. Play Console → **Setup → API access**(또는 좌측 설정 메뉴의 API 액세스)
2. 위 2단계의 **GCP 프로젝트를 연결(Link)** 한다.
3. 그 서비스계정에 권한 부여: **Users and permissions → Invite/Grant access** 로 서비스계정 이메일(`client_email`)을 추가하고, 최소 권한으로
   - **"View financial data, orders, and cancellation survey responses"** (구매 조회에 필요)
   - 앱 단위 권한으로 제한 가능(이 앱에만).
4. 권한 전파에 수 분~수십 분 걸릴 수 있다.

## 4. Worker 설정·비밀·배포 (Cloudflare)

1. `wrangler.toml` 확인: `PACKAGE_NAME`, `LIFETIME_PASS_PRODUCT_ID`, `CATALOG_URL` 이 앱과 일치하는지.
2. 서비스계정 JSON을 **secret**으로 등록(파일 내용 전체를 그대로):
   ```bash
   npx wrangler secret put GOOGLE_SERVICE_ACCOUNT_JSON < ./service-account.json
   ```
   (또는 인터랙티브로 붙여넣기. JSON 안의 `\n`은 그대로 둔다 — 코드가 파싱한다.)
3. 평생이용권 기프트 토큰 서명용 secret을 등록:
   ```bash
   npx wrangler secret put GIFT_PASS_SIGNING_SECRET
   ```
   충분히 긴 랜덤 문자열을 넣는다. 이 값이 바뀌면 기존 앱에 저장된 `giftPassToken`은 더 이상 보호 zip 다운로드에 쓸 수 없다.
4. 배포:
   ```bash
   npx wrangler deploy
   ```
   배포되면 `https://daintyz-billing.<계정>.workers.dev` 주소가 나온다. 이 주소를 앱에 넣는다(아래 계약 참고).

### 로컬 개발 시
`.dev.vars` 파일에 `GOOGLE_SERVICE_ACCOUNT_JSON='{...}'`, `GIFT_PASS_SIGNING_SECRET='...'` 를 넣고 `npx wrangler dev` (이 파일은 .gitignore 처리됨).

## 5. 테스트 (Play Console 라이선스 테스터)

- 실제 결제 검증은 **앱이 Play에 업로드돼 있고(내부테스트 트랙 등) 라이선스 테스터로 구매**해야 토큰이 생긴다.
- 헬스체크는 로그인 없이 가능:
  ```bash
  curl https://daintyz-billing.<계정>.workers.dev/healthz   # → ok
  ```

---

## API 계약 (앱 Phase 4 배선용)

### `POST /v1/redeem`
평생이용권 기프트코드를 서버에서 검증하고, 보호 zip 다운로드에 쓸 서명 토큰을 발급한다.

요청(JSON):
```json
{
  "code": "PASS-ABCD-1234"
}
```

검증:
- 코드는 앱/빌더와 동일하게 공백 제거 + 대문자 변환 후 SHA-256 해시로 바꾼다.
- `catalog.json` 최상위 `lifetimePassGiftCodes[].hash`와 대조한다.
- `expiresAt`은 Worker 서버 날짜(UTC yyyy-MM-dd) 기준으로 해당 날짜까지 허용한다.

응답:
- **200** `{ "type": "lifetime_pass", "giftPassToken": "..." }`
- **400** `{ "error": "bad_json" | "missing_code" | "invalid_code" }`
- **404** `{ "error": "invalid_code" }`
- **410** `{ "error": "expired_code" }`
- **500** `{ "error": "server_error" }`

`giftPassToken`은 앱에 저장했다가 보호 zip 다운로드 때 함께 보낸다. 계정 시스템이 없으므로 토큰 추출·공유까지 완전히 막지는 못하지만, 공개 catalog 해시만으로 보호 zip을 받을 수 없게 막는다.

### `POST /v1/skins/download`
요청(JSON):
```json
{
  "skinId": "cloud",
  "purchaseToken": "그 스킨 productId의 Play 영수증 토큰(있으면)",
  "passToken": "평생이용권 영수증 토큰(있으면)",
  "giftPassToken": "POST /v1/redeem으로 받은 평생이용권 기프트 토큰(있으면)"
}
```
- 앱은 `BillingManager.queryPurchases` 결과에서 토큰을 꺼내 보낸다.
- non-prestige 스킨은 `purchaseToken`(낱개구매), `passToken`(Play 이용권), `giftPassToken`(기프트 이용권) 중 하나만 있어도 된다.
- prestige 스킨은 `giftPassToken`/`passToken`으로는 통과하지 않는다. 개별 구매 토큰만 허용된다.

응답:
- **200** `application/zip` — zip 바이트 스트림(앱은 기존 `SkinDownloader`의 unzip 경로로 처리. 단, GET→POST로 바꾸고 토큰을 바디에 넣어야 함)
- **403** `{ "error": "not_entitled" }` — 구매 확인 실패
- **404** `{ "error": "unknown_skin" | "zip_not_found" }`
- **400** `{ "error": "bad_json" | "missing_skinId" }`
- **500** `{ "error": "server_auth_failed" | "gift_pass_auth_failed" | "server_error" }`

> ⚠️ 토큰은 URL이 아니라 **바디(POST)** 로 보낸다(로그 유출 방지). 현재 `SkinDownloader.download`는 GET이라,
> Phase 4에서 유료 zip은 이 엔드포인트로 POST하도록 분기해야 한다(무료는 기존 공개 CDN GET 유지).

---

## 아직 안 한 것(TODO)
- **기프트코드 수량제한/1회용 처리**: 현재 `/v1/redeem`은 catalog 해시+만료 검증과 서명 토큰 발급만 한다. 1회용/수량제한/회수가 필요하면 KV 또는 D1에 코드 상태를 저장해야 한다.
- **SKU 자동등록**(`inappproducts.insert`): 신규 유료 스킨 출시 시 Play 상품을 자동 생성하는 CI 단계(같은 서비스계정 사용).
- **R2 업로드 자동화**: 번들 자동화에 유료 zip의 R2 업로드를 연결.
