package com.daintyz.timerwidget

import android.app.Application
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import com.daintyz.timerwidget.billing.EntitlementSyncWorker

/**
 * Coil 전역 [ImageLoader] 제공자. GIF/애니 WebP 디코더를 등록해 미리보기 등에서 애니메이션 이미지를 표시한다.
 * - API 28+ : [ImageDecoderDecoder](AnimatedImageDrawable) — 더 효율적.
 * - API 26~27: [GifDecoder] 폴백.
 * Coil 싱글톤이 [ImageLoaderFactory]를 자동으로 사용한다(AsyncImage 등이 이 로더를 공유).
 */
class TimerWidgetApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        EntitlementSyncWorker.enqueue(this)
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
}
