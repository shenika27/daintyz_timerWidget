package com.daintyz.timerwidget.skin

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 디자인레포 catalog(index)가 깨졌을 때 개발자에게 알리는 원격 리포터.
 *
 * 흐름: [유저 앱] --POST {type,text}--> [릴레이(Cloudflare Worker)] --> [개발자 디스코드 채널]
 * 실제 디스코드 웹훅 URL은 릴레이의 환경변수(시크릿)에만 있고, APK에는 공개돼도 무방한
 * 릴레이 URL([RELAY_URL])만 들어간다 — 웹훅을 APK에 평문으로 두면 디컴파일로 노출돼
 * 채널이 도배될 위험이 있어 릴레이로 분리했다. @everyone/@here 무력화·레이트리밋은 릴레이가 처리.
 *
 * 트리거(설계 확정):
 *   - 카탈로그 전체 실패: index 자체가 깨져 원격 테마가 전부 사라지는 경우(JSON 문법/ skins 누락 등).
 *     ※ 네트워크 오프라인/타임아웃은 JSONException이 아니므로 알림 대상이 아니다(개발자 문제 아님).
 *   - 항목 스킵: 특정 테마 항목 하나가 깨져 스킵된 경우(skinId/name 누락 등).
 *
 * 안전장치:
 *   - [RELAY_URL]이 비어있으면 전부 no-op (릴레이 배포 전 안전).
 *   - 백그라운드 스레드 fire-and-forget, 실패해도 앱에 영향 없음.
 *   - 같은 서명(type+message)은 [DEDUP_WINDOW_MS] 동안 1회만 전송 — 다수 기기가 동일 오류를
 *     동시에 보내 채널이 도배되는 걸 방지(기기 단위 디듀프).
 */
object DevAlert {

    private const val TAG = "DevAlert"

    /** 릴레이(Cloudflare Worker) URL. 공개돼도 무방(실제 웹훅은 릴레이 시크릿). 비우면 리포팅 비활성(no-op). */
    private const val RELAY_URL = "https://daintyz-alert.xornexon.workers.dev/"

    /** 릴레이가 허용하는 알림 타입(워커의 ALLOWED_TYPES와 일치해야 한다). */
    private const val TYPE_CATALOG_TOTAL = "catalog_total_failure"
    private const val TYPE_ENTRY_SKIPPED = "catalog_entry_skipped"

    private const val DEDUP_WINDOW_MS = 6 * 60 * 60 * 1000L // 6시간

    private val lastSentAt = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /** index 자체가 깨져 카탈로그 전체가 실패한 경우. [sample]은 응답 앞부분(원인 파악용). */
    fun reportCatalogError(url: String, sample: String, cause: Throwable) {
        val text = buildString {
            append("🛑 **카탈로그 전체 실패** — 원격 테마 전부 미표시\n")
            append("• url: `$url`\n")
            append("• ${cause.javaClass.simpleName}: ${cause.message ?: "-"}\n")
            append("• 응답 앞부분: ```${sample.take(300)}```")
        }
        send(TYPE_CATALOG_TOTAL, signature = "catalog_total_failure|${cause.message}", text = text)
    }

    /** 카탈로그 항목 하나가 깨져 스킵된 경우. */
    fun reportEntryError(url: String, index: Int, cause: Throwable) {
        val text = buildString {
            append("⚠️ **카탈로그 항목 스킵** (index $index)\n")
            append("• url: `$url`\n")
            append("• ${cause.javaClass.simpleName}: ${cause.message ?: "-"}")
        }
        send(TYPE_ENTRY_SKIPPED, signature = "catalog_entry_skipped|$index|${cause.message}", text = text)
    }

    private fun send(type: String, signature: String, text: String) {
        if (RELAY_URL.isBlank()) return

        val now = System.currentTimeMillis()
        val prev = lastSentAt[signature]
        if (prev != null && now - prev < DEDUP_WINDOW_MS) return
        lastSentAt[signature] = now

        Thread {
            runCatching {
                // 릴레이가 기대하는 형식: { "type": "...", "text": "..." }.
                // 릴레이가 디스코드 content로 변환하면서 멘션 무력화/길이 캡을 처리한다.
                val body = JSONObject().apply {
                    put("type", type)
                    put("text", text.take(1500))
                }.toString()

                val conn = (URL(RELAY_URL).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 10_000
                    readTimeout = 10_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
                conn.outputStream.use { it.write(body.toByteArray()) }
                conn.responseCode // 전송 확정
                conn.disconnect()
            }.onFailure { Log.w(TAG, "개발자 알림 전송 실패(무시): $signature", it) }
        }.start()
    }
}
