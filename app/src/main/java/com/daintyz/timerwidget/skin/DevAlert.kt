package com.daintyz.timerwidget.skin

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 디자인레포 catalog(index)가 깨졌을 때 개발자 디스코드로 알리는 원격 리포터.
 *
 * 흐름: [유저 앱] --POST--> [Discord 웹훅 URL] --> [개발자 디스코드 채널]
 * 중계 서버 불필요. 디스코드 채널 설정 → 연동 → 웹훅 만들기로 URL을 발급받아 [WEBHOOK_URL]에 기입.
 *
 * 트리거(설계 확정):
 *   - 카탈로그 전체 실패: index 자체가 깨져 원격 테마가 전부 사라지는 경우(JSON 문법/ skins 누락 등).
 *     ※ 네트워크 오프라인/타임아웃은 JSONException이 아니므로 알림 대상이 아니다(개발자 문제 아님).
 *   - 항목 스킵: 특정 테마 항목 하나가 깨져 스킵된 경우(skinId/name 누락 등).
 *
 * 안전장치:
 *   - [WEBHOOK_URL]이 비어있으면 전부 no-op (웹훅 발급 전 안전).
 *   - 백그라운드 스레드 fire-and-forget, 실패해도 앱에 영향 없음.
 *   - 같은 서명(type+message)은 [DEDUP_WINDOW_MS] 동안 1회만 전송 — 다수 기기가 동일 오류를
 *     동시에 보내 채널이 도배되는 걸 방지(기기 단위 디듀프).
 */
object DevAlert {

    private const val TAG = "DevAlert"

    /** Discord 웹훅 URL. 비우면 리포팅 비활성(no-op). 채널 설정→연동→웹훅에서 발급 후 기입. */
    private const val WEBHOOK_URL =
        "https://discord.com/api/webhooks/1519873541854462002/uSMGJVnLOseOT8pBdpmGSUr49O7KzHymGtwlif6B1VTkgQPuny2Pb32NC3c4tuqDjjqO"

    /** 채널 멘션 — 알림이 떠도 푸시로 콕 찔러주게. 비우면 멘션 없이 일반 메시지. */
    private const val MENTION = "@everyone"

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
        send(signature = "catalog_total_failure|${cause.message}", text = text)
    }

    /** 카탈로그 항목 하나가 깨져 스킵된 경우. */
    fun reportEntryError(url: String, index: Int, cause: Throwable) {
        val text = buildString {
            append("⚠️ **카탈로그 항목 스킵** (index $index)\n")
            append("• url: `$url`\n")
            append("• ${cause.javaClass.simpleName}: ${cause.message ?: "-"}")
        }
        send(signature = "catalog_entry_skipped|$index|${cause.message}", text = text)
    }

    private fun send(signature: String, text: String) {
        if (WEBHOOK_URL.isBlank()) return

        val now = System.currentTimeMillis()
        val prev = lastSentAt[signature]
        if (prev != null && now - prev < DEDUP_WINDOW_MS) return
        lastSentAt[signature] = now

        val content = if (MENTION.isBlank()) text else "$MENTION $text"

        Thread {
            runCatching {
                // Discord 웹훅: { "content": "..." } (content 최대 2000자)
                // allowed_mentions로 @everyone 멘션을 명시 허용해야 푸시가 뜬다.
                val body = JSONObject().apply {
                    put("content", content.take(1900))
                    put("allowed_mentions", JSONObject().put(
                        "parse", org.json.JSONArray().put("everyone")
                    ))
                }.toString()

                val conn = (URL(WEBHOOK_URL).openConnection() as HttpURLConnection).apply {
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
