package com.daintyz.timerwidget.skin

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.daintyz.timerwidget.billing.BillingConfig
import com.daintyz.timerwidget.model.LifetimePassGiftCode
import com.daintyz.timerwidget.model.RemoteCatalog
import com.daintyz.timerwidget.model.RemoteSkinEntry
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

object SkinDownloader {

    private const val TAG = "SkinDownloader"

    fun skinsDir(context: Context): File = File(context.filesDir, "skins")

    fun isDownloaded(context: Context, skinId: String): Boolean =
        File(skinsDir(context), "$skinId/skin.json").exists()

    fun deleteDownloaded(context: Context, skinId: String): Boolean {
        val root = skinsDir(context).canonicalFile
        val target = File(root, skinId).canonicalFile
        if (!target.path.startsWith(root.path + File.separator)) return false
        if (!target.exists()) return true
        val deleted = target.deleteRecursively()
        if (deleted) SkinRepository.clearCache()
        return deleted
    }

    /**
     * catalog.json을 가져온다. 블로킹 호출 — 반드시 백그라운드 스레드에서 실행.
     *
     * 디자인레포 폴더 규칙(skinId 기준 자동 유추 — SkinRepoUrls):
     *   {baseUrl}/character/zip/{skinId}.zip          ← 캐릭터+타이머 한 세트 zip
     *   {baseUrl}/character/preview/{skinId}/thumb.png ← 테마 썸네일(상점/타이머 탭 공용)
     *   {baseUrl}/character/preview/{skinId}/prev01.png … ← 미리보기 팝업(prev01,02,03… 가변)
     *
     * catalog.json 형식 (zipUrl/thumbnailUrl은 생략 가능, 생략 시 위 규칙으로 유추):
     * {
     *   "baseUrl": "https://cdn.jsdelivr.net/gh/shenika27/daintyz_timer_characterList@main",
     *   "skins": [
     *     { "skinId": "cha01", "name": "팡", "price": 0, "prestige": false, "version": 1 }
     *   ]
     * }
     * price: 가격(원). 0이거나 생략 시 무료. 모든 테마는 타이머 디자인을 포함한다(필수).
     * prestige: 희귀(프리스티지) 스킨이면 true(생략 시 false). 평생이용권으로도 해금 안 됨 → 항상 개별구매. 상점 별도 표시.
     *
     * baseUrl을 생략하면 catalog.json이 위치한 폴더를 baseUrl로 사용한다.
     */
    fun fetchCatalog(url: String): List<RemoteSkinEntry> = fetchCatalogWithMeta(url).skins

    fun fetchCatalogWithMeta(url: String): RemoteCatalog {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        return try {
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            // index 자체가 깨진 경우(JSON 문법 오류/skins 누락/비-JSON 응답)는 개발자 디스코드로 알린다.
            // 네트워크 오프라인/타임아웃은 JSONException이 아니라 위 readText에서 throw되므로 알림 대상이 아니다.
            val json = try {
                JSONObject(text)
            } catch (e: org.json.JSONException) {
                DevAlert.reportCatalogError(url, text, e)
                throw e
            }
            // baseUrl 미지정 시 catalog.json이 있는 폴더로 폴백 (.../@main/catalog.json → .../@main)
            val baseUrl = json.optString("baseUrl").ifBlank { url.substringBeforeLast('/') }
            val arr = try {
                json.getJSONArray("skins")
            } catch (e: org.json.JSONException) {
                DevAlert.reportCatalogError(url, text, e)
                throw e
            }
            // 항목 단위로 격리 파싱한다. 디자인레포에서 특정 테마 항목 하나가 깨져도
            // (필수 필드 누락/오타 등) 그 항목만 스킵하고 정상 항목은 그대로 노출 — 카탈로그
            // 전체가 증발하는 all-or-nothing 실패를 막는다.
            val skins = (0 until arr.length()).mapNotNull { i ->
                runCatching {
                    val obj = arr.getJSONObject(i)
                    val skinId = obj.getString("skinId")
                    // price를 단일 출처로 사용. 생략 시 0(무료). isFree는 여기서 도출.
                    val price = obj.optInt("price", 0).coerceAtLeast(0)
                    RemoteSkinEntry(
                        skinId = skinId,
                        name = obj.getString("name"),
                        price = price,
                        isFree = price <= 0,
                        productId = obj.optString("productId").ifBlank { null },
                        prestige = obj.optBoolean("prestige", false),
                        zipUrl = obj.optString("zipUrl").ifBlank { "$baseUrl/character/zip/$skinId.zip" },
                        thumbnailUrl = obj.optString("thumbnailUrl")
                            .ifBlank { SkinRepoUrls.themeThumb(skinId, baseUrl) },
                        baseUrl = baseUrl,
                        description = obj.optString("description").ifBlank { null },
                        createdAt = obj.optString("createdAt").ifBlank { null },
                        hidden = obj.optBoolean("hidden", false),
                        saleStart = obj.optString("saleStart").ifBlank { null },
                        saleEnd = obj.optString("saleEnd").ifBlank { null },
                        giftCodeHashes = obj.optJSONArray("giftCodeHashes")?.let { a ->
                            (0 until a.length()).mapNotNull { a.optString(it).ifBlank { null }?.lowercase() }
                        }.orEmpty()
                    )
                }.onFailure {
                    Log.e(TAG, "카탈로그 항목 파싱 실패(인덱스 $i) — 스킵", it)
                    DevAlert.reportEntryError(url, i, it)
                }.getOrNull()
            }
            RemoteCatalog(
                skins = skins,
                lifetimePassGiftCodes = parseLifetimePassGiftCodes(json),
            )
        } finally {
            conn.disconnect()
        }
    }

    private fun parseLifetimePassGiftCodes(json: JSONObject): List<LifetimePassGiftCode> {
        val arr = json.optJSONArray("lifetimePassGiftCodes") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val obj = arr.optJSONObject(i) ?: return@mapNotNull null
            val hash = obj.optString("hash").trim().lowercase()
            if (hash.isBlank()) return@mapNotNull null
            LifetimePassGiftCode(
                hash = hash,
                expiresAt = obj.optString("expiresAt").ifBlank { null },
                maxUses = obj.optInt("maxUses", 0).coerceAtLeast(0),
            )
        }
    }

    /**
     * 스킨 zip을 다운로드하고 내부 저장소(filesDir/skins/{skinId}/)에 압축 해제한다.
     * 네트워크 작업은 별도 스레드에서 실행하되, UI가 바로 상태를 갱신할 수 있도록
     * onProgress/onComplete는 메인 스레드에서 호출한다. onProgress의 -1은
     * 서버가 Content-Length를 보내지 않아 전체 크기를 알 수 없다는 뜻이다.
     */
    fun download(
        context: Context,
        entry: RemoteSkinEntry,
        onProgress: (percent: Int) -> Unit,
        onComplete: (success: Boolean) -> Unit
    ) {
        Thread {
            val mainHandler = Handler(Looper.getMainLooper())
            fun reportProgress(percent: Int) = mainHandler.post { onProgress(percent) }
            val success = runCatching {
                val destDir = File(skinsDir(context), entry.skinId).also { it.mkdirs() }
                val tempZip = File(context.cacheDir, "${entry.skinId}_dl.zip")

                val conn = (URL(entry.zipUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 30_000
                }
                conn.connect()
                streamWithProgress(conn, tempZip, ::reportProgress)
                conn.disconnect()
                extractZipFlat(tempZip, destDir)
            }.isSuccess

            if (!success) Log.e(TAG, "다운로드 실패: ${entry.skinId}")
            mainHandler.post { onComplete(success) }
        }.start()
    }

    /**
     * 유료(보호) 스킨을 결제 검증 Worker로부터 받는다([BillingConfig.downloadUrl]).
     * 앱이 가진 영수증 토큰을 POST 바디로 보내고, Worker가 Play로 검증해 통과하면 zip을 스트리밍한다.
     * (무료 스킨은 [download]의 공개 CDN 경로를 그대로 쓴다.)
     *
     * @param purchaseToken 그 스킨 productId의 Play 영수증(낱개구매). 없으면 null.
     * @param passToken      평생이용권 영수증. 없으면 null. (비프리스티지면 이용권 토큰만으로도 통과)
     */
    fun downloadFromWorker(
        context: Context,
        skinId: String,
        purchaseToken: String?,
        passToken: String?,
        giftPassToken: String? = null,
        onProgress: (percent: Int) -> Unit,
        onComplete: (success: Boolean) -> Unit
    ) {
        Thread {
            val mainHandler = Handler(Looper.getMainLooper())
            fun reportProgress(percent: Int) = mainHandler.post { onProgress(percent) }
            val success = runCatching {
                check(BillingConfig.isConfigured) { "Worker 주소(BillingConfig.WORKER_BASE_URL) 미설정" }
                val destDir = File(skinsDir(context), skinId).also { it.mkdirs() }
                val tempZip = File(context.cacheDir, "${skinId}_dl.zip")

                val body = JSONObject().apply {
                    put("skinId", skinId)
                    if (!purchaseToken.isNullOrBlank()) put("purchaseToken", purchaseToken)
                    if (!passToken.isNullOrBlank()) put("passToken", passToken)
                    if (!giftPassToken.isNullOrBlank()) put("giftPassToken", giftPassToken)
                }.toString()

                val conn = (URL(BillingConfig.downloadUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "application/zip")
                }
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                // 403(미보유)/404 등은 예외로 떨궈 실패 처리. 토큰/사유는 로그에 남기지 않는다.
                check(conn.responseCode == HttpURLConnection.HTTP_OK) {
                    "보호 다운로드 거부: ${conn.responseCode}"
                }
                streamWithProgress(conn, tempZip, ::reportProgress)
                conn.disconnect()
                extractZipFlat(tempZip, destDir)
            }.isSuccess

            if (!success) Log.e(TAG, "보호 다운로드 실패: $skinId")
            mainHandler.post { onComplete(success) }
        }.start()
    }

    /** 연결된 [conn]의 본문을 [tempZip]에 받으며 진행률을 보고한다(전체 크기 미상이면 -1 1회). */
    private fun streamWithProgress(
        conn: HttpURLConnection,
        tempZip: File,
        reportProgress: (Int) -> Unit,
    ) {
        val total = conn.contentLength.toLong()
        var received = 0L
        var lastReported = Int.MIN_VALUE
        if (total <= 0L) reportProgress(-1)
        conn.inputStream.use { input ->
            tempZip.outputStream().use { output ->
                val buf = ByteArray(8192)
                var n: Int
                while (input.read(buf).also { n = it } != -1) {
                    output.write(buf, 0, n)
                    received += n
                    if (total > 0L) {
                        val percent = (received * 100 / total).toInt().coerceIn(0, 100)
                        if (percent != lastReported) {
                            lastReported = percent
                            reportProgress(percent)
                        }
                    }
                }
            }
        }
    }

    /**
     * [tempZip]을 filesDir/skins/{skinId}/ 아래에 평탄하게 푼다(공통 래퍼 폴더 제거 + path traversal 차단).
     * 완료 후 tempZip을 지우고 스킨 캐시를 무효화한다.
     */
    private fun extractZipFlat(tempZip: File, destDir: File) {
        // zip이 폴더째(예: muk/skin.json) 압축된 경우 공통 래퍼 폴더를 벗겨서
        // filesDir/skins/{skinId}/skin.json 위치에 평탄하게 풀리도록 한다.
        val rootPrefix = detectCommonRoot(tempZip)

        // ZIP path traversal 방지
        val destCanonical = destDir.canonicalPath + File.separator
        ZipInputStream(tempZip.inputStream()).use { zip ->
            var ze = zip.nextEntry
            while (ze != null) {
                val relativeName =
                    if (rootPrefix != null) ze.name.removePrefix(rootPrefix) else ze.name
                if (relativeName.isEmpty()) { zip.closeEntry(); ze = zip.nextEntry; continue }
                val outFile = File(destDir, relativeName)
                check(outFile.canonicalPath.startsWith(destCanonical)) {
                    "ZIP path traversal 차단: ${ze.name}"
                }
                if (ze.isDirectory) outFile.mkdirs()
                else {
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { zip.copyTo(it) }
                }
                zip.closeEntry()
                ze = zip.nextEntry
            }
        }
        tempZip.delete()
        SkinRepository.clearCache()
    }

    /**
     * zip 안의 모든 엔트리가 동일한 단일 최상위 폴더(예: "muk/") 아래에 있으면
     * 그 접두사("muk/")를 반환한다. 루트에 파일이 있거나 최상위 폴더가 둘 이상이면 null.
     */
    private fun detectCommonRoot(zipFile: File): String? {
        var root: String? = null
        ZipInputStream(zipFile.inputStream()).use { zip ->
            var ze = zip.nextEntry
            while (ze != null) {
                val name = ze.name
                val slash = name.indexOf('/')
                if (slash <= 0) return null            // 루트에 파일 존재 → 래퍼 폴더 없음
                val first = name.substring(0, slash)
                if (root == null) root = first
                else if (root != first) return null    // 최상위 폴더가 둘 이상
                zip.closeEntry()
                ze = zip.nextEntry
            }
        }
        return root?.let { "$it/" }
    }
}
