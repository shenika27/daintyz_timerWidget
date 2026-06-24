package com.daintyz.timerwidget.ui

import android.view.View
import androidx.viewpager2.widget.ViewPager2
import kotlin.math.abs

/**
 * 커버플로우 효과: 중앙(포커스) 페이지는 100% 선명, 좌/우로 갈수록 작아지고(스케일) 흐려진다(알파).
 *
 * ViewPager2의 paddingHorizontal + clipToPadding=false 와 함께 써서 양옆 카드가 빼꼼 보이게 한다.
 */
class CoverFlowTransformer(
    private val minScale: Float = 0.86f,
    private val minAlpha: Float = 0.62f,
) : ViewPager2.PageTransformer {

    override fun transformPage(page: View, position: Float) {
        // position: 중앙 0, 왼쪽 음수, 오른쪽 양수.
        val clamped = abs(position).coerceAtMost(1f)
        val scale = 1f - (1f - minScale) * clamped
        page.scaleX = scale
        page.scaleY = scale
        page.alpha = 1f - (1f - minAlpha) * clamped
    }
}
