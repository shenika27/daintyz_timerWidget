package com.daintyz.timerwidget.ui.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 앱(Compose) 색 팔레트 — 기존 res/values/colors.xml과 동일한 톤.
 * Material3 ColorScheme에 없는 커스텀 슬롯(크림 카드/브라운 보조색/외곽선)은 여기서 직접 참조한다.
 */
object AppColors {
    val Primary = Color(0xFFE89B3B)
    val OnPrimary = Color(0xFFFFFFFF)
    val Background = Color(0xFFFFFFFF)
    val Surface = Color(0xFFFFFFFF)
    val OnSurface = Color(0xFF212121)

    /** 카드 크림 배경 (widget_background). */
    val CardCream = Color(0xFFFFF7EC)
    /** 보조 텍스트/아이콘 브라운 (placeholder_fg). */
    val Brown = Color(0xFF7A5C34)
    /** 강조 텍스트 다크브라운 (widget_text_primary). */
    val TextPrimary = Color(0xFF3A2E1F)
    /** 카드/칩 외곽선 (vault_card_stroke). */
    val Stroke = Color(0x1F3A2E1F)
    /** 미보유 딤. */
    val Dim = Color(0xB3403A33)
}

private val LightScheme = lightColorScheme(
    primary = AppColors.Primary,
    onPrimary = AppColors.OnPrimary,
    background = AppColors.Background,
    onBackground = AppColors.TextPrimary,
    surface = AppColors.Surface,
    onSurface = AppColors.OnSurface,
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightScheme, content = content)
}
