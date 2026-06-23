package com.daintyz.timerwidget.skin

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ImageView
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * 원격 썸네일 로더. 외부 이미지 라이브러리(Glide/Coil) 없이 동작하도록 최소 구현.
 *
 * - URL 단위 메모리 캐시 (renderList가 자주 재호출돼도 재다운로드 방지)
 * - ImageView에 url을 tag로 달아, 뷰 재사용/늦게 도착한 응답이 잘못된 이미지를 덮어쓰지 않게 함
 */
object RemoteImageLoader {

    private const val TAG = "RemoteImageLoader"

    private val cache = ConcurrentHashMap<String, Bitmap>()
    private val executor = Executors.newFixedThreadPool(2)
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * [url] 이미지를 [imageView]에 로드한다. 다운로드 동안/실패 시에는 [placeholderRes]를 표시한다.
     */
    fun load(imageView: ImageView, url: String, placeholderRes: Int) {
        imageView.tag = url
        cache[url]?.let { imageView.setImageBitmap(it); return }

        imageView.setImageResource(placeholderRes)
        executor.execute {
            val bitmap = runCatching { fetch(url) }
                .onFailure { Log.w(TAG, "썸네일 로드 실패: $url", it) }
                .getOrNull() ?: return@execute
            cache[url] = bitmap
            mainHandler.post {
                // 그 사이 같은 ImageView가 다른 URL로 재바인딩됐으면 무시
                if (imageView.tag == url) imageView.setImageBitmap(bitmap)
            }
        }
    }

    /**
     * 미리보기 갤러리용 순차 로더. [urls]를 앞에서부터 받아 성공할 때마다 [onImage]를 메인 스레드로 콜백하고,
     * 첫 실패(결번/404)에서 멈춘다 — 디자인레포 규칙상 prevNN은 prev01부터 빈 번호 없이 연속이므로.
     * [isCancelled]가 true를 반환하면(예: Activity 종료) 즉시 중단한다.
     */
    fun loadGallery(
        urls: List<String>,
        isCancelled: () -> Boolean,
        onImage: (index: Int, bitmap: Bitmap) -> Unit
    ) {
        executor.execute {
            for ((i, url) in urls.withIndex()) {
                if (isCancelled()) return@execute
                val bitmap = cache[url] ?: runCatching { fetch(url) }.getOrNull()?.also { cache[url] = it }
                ?: return@execute // 첫 결번에서 중단
                mainHandler.post { if (!isCancelled()) onImage(i, bitmap) }
            }
        }
    }

    private fun fetch(url: String): Bitmap? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
        }
        return try {
            conn.inputStream.use { BitmapFactory.decodeStream(it) }
        } finally {
            conn.disconnect()
        }
    }
}
