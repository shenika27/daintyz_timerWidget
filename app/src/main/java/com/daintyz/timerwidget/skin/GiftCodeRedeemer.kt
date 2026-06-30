package com.daintyz.timerwidget.skin

import android.content.Context
import com.daintyz.timerwidget.billing.BillingConfig
import com.daintyz.timerwidget.data.TimerPreferences
import com.daintyz.timerwidget.model.LifetimePassGiftCode
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.CountDownLatch

/**
 * 기프트코드 해금 단일 지점.
 *
 * 동작: 입력 코드를 정규화(공백제거+대문자) → SHA-256(hex) → catalog의 [com.daintyz.timerwidget.model.RemoteSkinEntry.giftCodeHashes]
 * 와 대조 → 일치 스킨을 다운로드하고 기프트 해금목록(giftUnlockedSkinIds)에 추가한다. 백엔드가 없어 검증은 클라이언트에서
 * 일어나므로 catalog엔 평문이 아닌 해시만 둔다(빌더가 해시만 출력). 정규화 규칙은 빌더와 반드시 동일해야 한다.
 *
 * [redeem]은 네트워크(카탈로그 fetch + zip 다운로드)를 블로킹하므로 반드시 백그라운드 스레드에서 호출한다.
 */
object GiftCodeRedeemer {

    sealed interface Result {
        /** 해금 성공(다운로드까지 완료). */
        data class Success(val skinId: String, val name: String) : Result
        /** 평생이용권 해금 성공. */
        object LifetimePassSuccess : Result
        /** 일치하는 코드가 있으나 입력 가능 기한이 지남. */
        object Expired : Result
        /** 일치하는 코드 없음(오타/만료/잘못된 코드). */
        object Invalid : Result
        /** 1회 소진 코드가 이미 사용됨. */
        object Used : Result
        /** 이미 보유한 스킨/이용권의 코드. */
        data class AlreadyOwned(val name: String) : Result
        /** 네트워크/다운로드 실패. */
        object Error : Result
    }

    /** 빌더와 동일한 정규화: 앞뒤 공백 제거 + 내부 공백 제거 + 대문자. */
    fun normalize(code: String): String = code.trim().replace(Regex("\\s+"), "").uppercase()

    fun sha256Hex(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    fun redeem(context: Context, rawCode: String): Result {
        val code = normalize(rawCode)
        if (code.isEmpty()) return Result.Invalid

        val hash = sha256Hex(code)
        val catalog = runCatching { SkinDownloader.fetchCatalogWithMeta(SkinRepoUrls.CATALOG_URL) }.getOrNull()
            ?: return Result.Error

        val prefs = TimerPreferences.get(context)
        val data = prefs.load()
        val passCode = catalog.lifetimePassGiftCodes.firstOrNull { it.hash == hash }
        if (passCode != null) {
            if (passCode.isExpired()) return Result.Expired
            if (data.hasEffectiveLifetimePass) return Result.AlreadyOwned("평생이용권")
            return redeemLifetimePassWithWorker(code, prefs)
        }

        val entry = catalog.skins.firstOrNull { hash in it.giftCodeHashes } ?: return Result.Invalid

        // Play 구매분/기프트 해금분 어느 쪽으로든 이미 보유하면 중복 처리.
        if (entry.skinId in data.purchasedSkinIds || entry.skinId in data.giftUnlockedSkinIds) {
            return Result.AlreadyOwned(entry.name)
        }

        if (!downloadBlocking(context.applicationContext, entry)) return Result.Error
        prefs.addGiftUnlockedSkinId(entry.skinId)
        SkinRepository.clearCache() // 새로 받은 스킨이 즉시 목록/창고에 반영되도록 캐시 무효화
        return Result.Success(entry.skinId, entry.name)
    }

    private fun LifetimePassGiftCode.isExpired(
        today: LocalDate = LocalDate.now(ZoneId.systemDefault()),
    ): Boolean {
        val expires = expiresAt?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return false
        return today.isAfter(expires)
    }

    private fun redeemLifetimePassWithWorker(
        code: String,
        prefs: TimerPreferences,
    ): Result {
        if (!BillingConfig.isConfigured) return Result.Error
        val response = runCatching {
            val body = JSONObject().put("code", code).toString()
            val conn = (URL(BillingConfig.redeemUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }
            try {
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val text = if (conn.responseCode in 200..299) {
                    conn.inputStream.bufferedReader().use { it.readText() }
                } else {
                    conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                }
                conn.responseCode to text
            } finally {
                conn.disconnect()
            }
        }.getOrNull() ?: return Result.Error

        val (status, text) = response
        return when (status) {
            HttpURLConnection.HTTP_OK -> {
                val token = runCatching { JSONObject(text).optString("giftPassToken") }.getOrNull()
                if (token.isNullOrBlank()) {
                    Result.Error
                } else {
                    prefs.grantGiftLifetimePass(token)
                    SkinRepository.clearCache()
                    Result.LifetimePassSuccess
                }
            }
            HttpURLConnection.HTTP_CONFLICT -> Result.Used
            HttpURLConnection.HTTP_GONE -> Result.Expired
            HttpURLConnection.HTTP_NOT_FOUND,
            HttpURLConnection.HTTP_BAD_REQUEST -> Result.Invalid
            else -> Result.Error
        }
    }

    /** 비동기 [SkinDownloader.download]를 래치로 감싸 동기 대기한다(이미 백그라운드 스레드에서 호출됨). */
    private fun downloadBlocking(
        context: Context,
        entry: com.daintyz.timerwidget.model.RemoteSkinEntry,
    ): Boolean {
        val latch = CountDownLatch(1)
        var ok = false
        SkinDownloader.download(
            context = context,
            entry = entry,
            onProgress = {},
            onComplete = { ok = it; latch.countDown() },
        )
        latch.await()
        return ok
    }
}
