/**
 * daintyz 결제 검증 Worker (Cloudflare Workers + R2)
 *
 * 목적: 유료 스킨 zip을 공개 CDN에 두지 않고, "구매한 사람에게만" 내려준다.
 * 흐름:
 *   앱 → POST /v1/skins/download { skinId, purchaseToken?, passToken? }
 *     1) catalog에서 skinId의 권위 정보(productId, prestige, 무료여부)를 읽고
 *     2) Google Play Developer API로 purchaseToken을 검증(구매 상태 확인)하고
 *     3) 통과하면 비공개 R2 버킷에서 zip을 스트리밍한다.
 *
 * 권한 규칙(BM: 개별구매 + 평생이용권):
 *   - 해당 스킨의 productId 토큰이 '구매완료'면 통과
 *   - 또는 (프리스티지가 아니면) 평생이용권(LIFETIME_PASS_PRODUCT_ID) 토큰이 '구매완료'면 통과
 *   - 무료 스킨은 검증 없이 통과(보호 불필요)
 *
 * 비밀/바인딩(wrangler.toml + secret):
 *   env.SKINS_BUCKET                 R2 버킷 바인딩(유료 zip 저장소, 공개 접근 OFF)
 *   env.PACKAGE_NAME                 앱 패키지명(com.daintyz.timerwidget)
 *   env.LIFETIME_PASS_PRODUCT_ID     평생이용권 인앱상품 ID(lifetime_pass)
 *   env.CATALOG_URL                  catalog.json URL(SkinRepoUrls.CATALOG_URL과 동일)
 *   env.GOOGLE_SERVICE_ACCOUNT_JSON  (secret) GCP 서비스계정 JSON 전체 — Play Developer API 호출용
 *
 * 토큰은 절대 로그에 남기지 않는다.
 */

const GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token";
const ANDROIDPUBLISHER = "https://androidpublisher.googleapis.com/androidpublisher/v3";
const SCOPE = "https://www.googleapis.com/auth/androidpublisher";

// 모듈 전역 캐시(같은 isolate 재사용 시 재발급/재조회 절약). 보장은 아니므로 만료를 반드시 확인.
let cachedAccessToken = null; // { token, expiresAt(sec) }
let cachedCatalog = null; // { at(ms), skins }

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (request.method === "GET" && url.pathname === "/healthz") {
      return new Response("ok", { headers: { "Content-Type": "text/plain" } });
    }
    if (request.method === "POST" && url.pathname === "/v1/skins/download") {
      return handleDownload(request, env).catch((e) => {
        // 상세 사유는 외부에 노출하지 않는다(토큰 유출 방지). 서버 로그에만.
        console.error("download error:", e && e.message);
        return json({ error: "server_error" }, 500);
      });
    }
    return json({ error: "not_found" }, 404);
  },
};

async function handleDownload(request, env) {
  let body;
  try {
    body = await request.json();
  } catch {
    return json({ error: "bad_json" }, 400);
  }

  const skinId = String(body.skinId || "").trim();
  const purchaseToken = body.purchaseToken ? String(body.purchaseToken) : null;
  const passToken = body.passToken ? String(body.passToken) : null;
  if (!skinId) return json({ error: "missing_skinId" }, 400);

  // 1) catalog에서 권위 정보 조회(앱이 보낸 값은 신뢰하지 않는다 — 싼 스킨 사고 비싼 스킨 받는 것 방지).
  const skin = await lookupSkin(env, skinId);
  if (!skin) return json({ error: "unknown_skin" }, 404);

  // 무료 스킨은 검증 없이 서빙(R2에 올려둔 경우). 보통 무료는 공개 CDN을 쓰므로 여기 안 올 수도 있다.
  if (skin.isFree) return serveSkinZip(env, skinId);

  // 2) 권한 검증.
  const access = await getAccessToken(env);
  if (!access) return json({ error: "server_auth_failed" }, 500);

  let authorized = false;
  if (purchaseToken && skin.productId) {
    authorized = await verifyPurchase(env, access, skin.productId, purchaseToken);
  }
  if (!authorized && passToken && !skin.prestige) {
    authorized = await verifyPurchase(env, access, env.LIFETIME_PASS_PRODUCT_ID, passToken);
  }
  if (!authorized) return json({ error: "not_entitled" }, 403);

  // 3) 비공개 R2에서 zip 스트리밍.
  return serveSkinZip(env, skinId);
}

/**
 * Play Developer API로 인앱상품 구매를 검증한다.
 * GET .../purchases/products/{productId}/tokens/{token}
 * purchaseState: 0=구매완료, 1=취소, 2=보류. 0만 통과.
 */
async function verifyPurchase(env, accessToken, productId, token) {
  const endpoint =
    `${ANDROIDPUBLISHER}/applications/${encodeURIComponent(env.PACKAGE_NAME)}` +
    `/purchases/products/${encodeURIComponent(productId)}/tokens/${encodeURIComponent(token)}`;
  const res = await fetch(endpoint, {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  if (!res.ok) return false;
  const data = await res.json();
  return data.purchaseState === 0;
}

/** 비공개 R2 버킷에서 {skinId}.zip을 스트리밍한다(버킷 자체는 공개 접근 OFF). */
async function serveSkinZip(env, skinId) {
  const key = `${skinId}.zip`;
  const obj = await env.SKINS_BUCKET.get(key);
  if (!obj) return json({ error: "zip_not_found" }, 404);
  return new Response(obj.body, {
    headers: {
      "Content-Type": "application/zip",
      "Content-Disposition": `attachment; filename="${skinId}.zip"`,
      // 캐시/저장 금지: 권한 검증을 거쳐야만 받도록.
      "Cache-Control": "private, no-store",
    },
  });
}

/** catalog에서 스킨의 권위 정보를 만든다. productId 미지정 시 skinId를 SKU로 폴백. */
async function lookupSkin(env, skinId) {
  const skins = await loadCatalog(env);
  const e = skins.find((s) => s && s.skinId === skinId);
  if (!e) return null;
  const price = Number(e.price || 0);
  return {
    skinId,
    productId: e.productId || skinId,
    prestige: !!e.prestige,
    isFree: !(price > 0),
  };
}

/** catalog.json을 가져와 skins 배열을 돌려준다(60초 메모리 캐시 + CDN 캐시). */
async function loadCatalog(env) {
  const now = Date.now();
  if (cachedCatalog && now - cachedCatalog.at < 60_000) return cachedCatalog.skins;
  const res = await fetch(env.CATALOG_URL, { cf: { cacheTtl: 60, cacheEverything: true } });
  if (!res.ok) throw new Error(`catalog fetch ${res.status}`);
  const data = await res.json();
  const skins = Array.isArray(data.skins) ? data.skins : [];
  cachedCatalog = { at: now, skins };
  return skins;
}

/** 서비스계정 JWT로 Google OAuth2 액세스 토큰을 발급(1시간 유효, 캐시). */
async function getAccessToken(env) {
  const nowSec = Math.floor(Date.now() / 1000);
  if (cachedAccessToken && cachedAccessToken.expiresAt > nowSec + 60) {
    return cachedAccessToken.token;
  }
  let sa;
  try {
    sa = JSON.parse(env.GOOGLE_SERVICE_ACCOUNT_JSON);
  } catch {
    throw new Error("GOOGLE_SERVICE_ACCOUNT_JSON parse failed");
  }
  const jwt = await makeServiceAccountJwt(sa, nowSec);
  const res = await fetch(GOOGLE_TOKEN_URL, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: jwt,
    }),
  });
  if (!res.ok) return null;
  const data = await res.json();
  if (!data.access_token) return null;
  cachedAccessToken = {
    token: data.access_token,
    expiresAt: nowSec + (Number(data.expires_in) || 3600),
  };
  return cachedAccessToken.token;
}

/** RS256 서명된 OAuth2 서비스계정 JWT를 만든다(Web Crypto). */
async function makeServiceAccountJwt(sa, iatSec) {
  const header = { alg: "RS256", typ: "JWT" };
  const claim = {
    iss: sa.client_email,
    scope: SCOPE,
    aud: GOOGLE_TOKEN_URL,
    iat: iatSec,
    exp: iatSec + 3600,
  };
  const encode = (obj) => b64url(new TextEncoder().encode(JSON.stringify(obj)));
  const unsigned = `${encode(header)}.${encode(claim)}`;
  const key = await importPrivateKey(sa.private_key);
  const sig = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    key,
    new TextEncoder().encode(unsigned),
  );
  return `${unsigned}.${b64url(new Uint8Array(sig))}`;
}

/** 서비스계정 PEM(PKCS8) 개인키를 Web Crypto 키로 가져온다. */
async function importPrivateKey(pem) {
  const b64 = pem
    .replace(/-----BEGIN PRIVATE KEY-----/, "")
    .replace(/-----END PRIVATE KEY-----/, "")
    .replace(/\s+/g, "");
  const der = Uint8Array.from(atob(b64), (c) => c.charCodeAt(0));
  return crypto.subtle.importKey(
    "pkcs8",
    der.buffer,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
}

/** 바이트 배열 → base64url(패딩 제거). */
function b64url(bytes) {
  let bin = "";
  for (let i = 0; i < bytes.length; i++) bin += String.fromCharCode(bytes[i]);
  return btoa(bin).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function json(obj, status = 200) {
  return new Response(JSON.stringify(obj), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}
