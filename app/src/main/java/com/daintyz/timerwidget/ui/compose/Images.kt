package com.daintyz.timerwidget.ui.compose

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.daintyz.timerwidget.R
import com.daintyz.timerwidget.skin.RemoteImageLoader

/**
 * 원격 URL 이미지를 표시한다. 로딩/실패 중에는 [placeholder] 드로어블을 보여준다.
 * 외부 이미지 라이브러리 없이 [RemoteImageLoader](메모리 캐시 + 백그라운드 fetch)를 사용한다.
 */
@Composable
fun RemoteImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    placeholder: Int = R.drawable.frame_placeholder,
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, url) {
        value = null
        RemoteImageLoader.request(url) { value = it }
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
        )
    } else {
        Image(
            painter = painterResource(placeholder),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
        )
    }
}

/** 비트맵(로컬 프레임 등)을 표시하되, null이면 placeholder. */
@Composable
fun BitmapImage(
    bitmap: Bitmap?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    placeholder: Int = R.drawable.frame_placeholder,
) {
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
        )
    } else {
        Image(
            painter = painterResource(placeholder),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
        )
    }
}
