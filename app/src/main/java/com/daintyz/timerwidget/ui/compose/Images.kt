package com.daintyz.timerwidget.ui.compose

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import com.daintyz.timerwidget.R

/**
 * 원격 URL 이미지를 표시한다(Coil). PNG/JPG뿐 아니라 GIF·애니 WebP도 자동으로 애니메이션 재생된다
 * (디코더는 [com.daintyz.timerwidget.TimerWidgetApp]의 전역 ImageLoader에 등록).
 * 로딩/실패 중에는 [placeholder] 드로어블을 보여준다.
 */
@Composable
fun RemoteImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    placeholder: Int = R.drawable.frame_placeholder,
) {
    AsyncImage(
        model = url,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        placeholder = painterResource(placeholder),
        error = painterResource(placeholder),
        fallback = painterResource(placeholder),
    )
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
