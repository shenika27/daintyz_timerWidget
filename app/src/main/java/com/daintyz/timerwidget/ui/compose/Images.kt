package com.daintyz.timerwidget.ui.compose

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import coil.compose.AsyncImage
import com.daintyz.timerwidget.R

/**
 * 캐릭터 영역 폴백 placeholder. [R.color.placeholder_bg](기본 투명)로 칠한 단색 painter.
 *
 * 위젯 RemoteViews는 layer-list 드로어블([R.drawable.frame_placeholder])을 쓰지만,
 * Compose painterResource는 layer-list를 못 읽어 크래시한다 → Compose 쪽은 색 painter로 맞춘다.
 */
@Composable
private fun placeholderPainter() = ColorPainter(colorResource(R.color.placeholder_bg))

/**
 * 원격 URL 이미지를 표시한다(Coil). PNG/JPG뿐 아니라 GIF·애니 WebP도 자동으로 애니메이션 재생된다
 * (디코더는 [com.daintyz.timerwidget.TimerWidgetApp]의 전역 ImageLoader에 등록).
 * 로딩/실패 중에는 placeholder(투명)를 보여준다.
 */
@Composable
fun RemoteImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    val placeholder = placeholderPainter()
    AsyncImage(
        model = url,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        placeholder = placeholder,
        error = placeholder,
        fallback = placeholder,
    )
}

/** 비트맵(로컬 프레임 등)을 표시하되, null이면 placeholder(투명). */
@Composable
fun BitmapImage(
    bitmap: Bitmap?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
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
            painter = placeholderPainter(),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
        )
    }
}
