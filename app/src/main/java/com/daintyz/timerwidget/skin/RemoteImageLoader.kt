package com.daintyz.timerwidget.skin

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * 미리보기 갤러리(prevNN) 해석기. **표시는 Coil([com.daintyz.timerwidget.ui.compose.RemoteImage])이 맡고**,
 * 여기서는 "어떤 prevNN 파일이 존재하는지"만 HEAD로 빠르게 탐침한다(본문 다운로드 없음).
 *
 * prevNN은 prev01부터 빈 번호 없이 연속이라는 디자인레포 규칙을 따른다(첫 결번에서 중단).
 * 칸마다 확장자가 다를 수 있어(.png/.gif 혼용) 인덱스별 후보 URL을 순서대로 확인한다.
 */
object RemoteImageLoader {

    private const val TAG = "RemoteImageLoader"

    private val executor = Executors.newFixedThreadPool(2)
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * 각 인덱스의 후보 URL([candidatesPerIndex])을 앞에서부터 HEAD로 존재 확인해, 처음 존재하는 URL을
     * 메인 스레드로 [onResolved] 콜백한다. 한 인덱스의 후보가 전부 없으면(결번) 거기서 중단한다.
     * [isCancelled]가 true면(예: 화면 이탈) 즉시 멈춘다.
     */
    fun resolveGallery(
        candidatesPerIndex: List<List<String>>,
        isCancelled: () -> Boolean,
        onResolved: (index: Int, url: String) -> Unit,
    ) {
        executor.execute {
            for ((i, candidates) in candidatesPerIndex.withIndex()) {
                if (isCancelled()) return@execute
                val url = candidates.firstOrNull { exists(it) } ?: return@execute // 결번 → 중단
                mainHandler.post { if (!isCancelled()) onResolved(i, url) }
            }
        }
    }

    /** HEAD 요청으로 URL 존재 여부 확인(2xx면 존재). 본문은 받지 않는다. */
    private fun exists(url: String): Boolean {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "HEAD"
            connectTimeout = 10_000
            readTimeout = 10_000
        }
        return try {
            conn.responseCode in 200..299
        } catch (e: Exception) {
            Log.w(TAG, "존재 확인 실패: $url", e)
            false
        } finally {
            conn.disconnect()
        }
    }
}
