package com.daintyz.timerwidget.skin

import android.content.Context
import android.util.Log
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

    /**
     * catalog.json을 가져온다. 블로킹 호출 — 반드시 백그라운드 스레드에서 실행.
     *
     * 디자인레포 폴더 규칙(skinId 기준 자동 유추 — SkinRepoUrls):
     *   {baseUrl}/character_zip/{skinId}.zip       ← 캐릭터+타이머 한 세트 zip
     *   {baseUrl}/preview/{skinId}/thumb.png       ← 테마 썸네일(상점/타이머 탭 공용)
     *   {baseUrl}/preview/{skinId}/prev01.png …    ← 미리보기 팝업(prev01,02,03… 가변)
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
    fun fetchCatalog(url: String): List<RemoteSkinEntry> {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        return try {
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(text)
            // baseUrl 미지정 시 catalog.json이 있는 폴더로 폴백 (.../@main/catalog.json → .../@main)
            val baseUrl = json.optString("baseUrl").ifBlank { url.substringBeforeLast('/') }
            val arr = json.getJSONArray("skins")
            (0 until arr.length()).map { i ->
                arr.getJSONObject(i).let { obj ->
                    val skinId = obj.getString("skinId")
                    // price를 단일 출처로 사용. 생략 시 0(무료). isFree는 여기서 도출.
                    val price = obj.optInt("price", 0).coerceAtLeast(0)
                    RemoteSkinEntry(
                        skinId = skinId,
                        name = obj.getString("name"),
                        price = price,
                        isFree = price <= 0,
                        prestige = obj.optBoolean("prestige", false),
                        zipUrl = obj.optString("zipUrl").ifBlank { "$baseUrl/character_zip/$skinId.zip" },
                        thumbnailUrl = obj.optString("thumbnailUrl")
                            .ifBlank { SkinRepoUrls.themeThumb(skinId, baseUrl) },
                        baseUrl = baseUrl
                    )
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    /**
     * 스킨 zip을 다운로드하고 내부 저장소(filesDir/skins/{skinId}/)에 압축 해제한다.
     * 별도 스레드에서 실행되므로 onProgress/onComplete는 runOnUiThread로 감싸서 처리.
     */
    fun download(
        context: Context,
        entry: RemoteSkinEntry,
        onProgress: (percent: Int) -> Unit,
        onComplete: (success: Boolean) -> Unit
    ) {
        Thread {
            val success = runCatching {
                val destDir = File(skinsDir(context), entry.skinId).also { it.mkdirs() }
                val tempZip = File(context.cacheDir, "${entry.skinId}_dl.zip")

                val conn = URL(entry.zipUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                conn.connect()
                val total = conn.contentLength.toLong()
                var received = 0L
                conn.inputStream.use { input ->
                    tempZip.outputStream().use { output ->
                        val buf = ByteArray(8192)
                        var n: Int
                        while (input.read(buf).also { n = it } != -1) {
                            output.write(buf, 0, n)
                            received += n
                            if (total > 0) onProgress((received * 100 / total).toInt())
                        }
                    }
                }
                conn.disconnect()

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
            }.isSuccess

            if (!success) Log.e(TAG, "다운로드 실패: ${entry.skinId}")
            onComplete(success)
        }.start()
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
