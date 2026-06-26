package com.daintyz.timerwidget.ui.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 큰 화면에서 좌측 내비게이션으로 전환할 기준 폭. */
const val EXPANDED_SCREEN_MIN_WIDTH_DP = 600

/** 큰 화면에서도 카드와 설정 행이 과도하게 늘어나지 않는 콘텐츠 최대 폭. */
val ExpandedContentMaxWidth: Dp = 720.dp

@Composable
fun isExpandedScreen(): Boolean =
    LocalConfiguration.current.screenWidthDp >= EXPANDED_SCREEN_MIN_WIDTH_DP

/**
 * 폰에서는 전체 폭을 유지하고, 폴드 펼침/태블릿에서는 중앙의 읽기 쉬운 폭으로 콘텐츠를 제한한다.
 * 화면별 UI는 이 컨테이너 안에서 기존처럼 fillMaxSize()를 사용해도 된다.
 */
@Composable
fun AdaptiveContent(
    modifier: Modifier = Modifier,
    maxWidth: Dp = ExpandedContentMaxWidth,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = maxWidth)
                .fillMaxWidth()
                .fillMaxHeight(),
        ) {
            content()
        }
    }
}
