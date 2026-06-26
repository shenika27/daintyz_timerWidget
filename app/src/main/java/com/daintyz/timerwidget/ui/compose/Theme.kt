package com.daintyz.timerwidget.ui.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import com.daintyz.timerwidget.R

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

private val GmarketSans = FontFamily(
    Font(R.font.gmarket_sans_medium, FontWeight.Medium),
    Font(R.font.gmarket_sans_bold, FontWeight.Bold),
)

private val Jua = FontFamily(Font(R.font.jua_regular, FontWeight.Normal))

private val BaseTypography = Typography()

/** 없는 굵기를 시스템이 인위적으로 만들어 획이 뭉개지는 것을 막는다. */
private fun TextStyle.appFont(family: FontFamily, weight: FontWeight) = copy(
    fontFamily = family,
    fontWeight = weight,
    fontSynthesis = FontSynthesis.None,
)

/** 앱 전체 타이포그래피 역할: 본문은 Gmarket Sans, 제목은 Jua. */
val AppTypography = BaseTypography.copy(
    displayLarge = BaseTypography.displayLarge.appFont(Jua, FontWeight.Normal),
    displayMedium = BaseTypography.displayMedium.appFont(Jua, FontWeight.Normal),
    displaySmall = BaseTypography.displaySmall.appFont(Jua, FontWeight.Normal),
    headlineLarge = BaseTypography.headlineLarge.appFont(Jua, FontWeight.Normal),
    headlineMedium = BaseTypography.headlineMedium.appFont(Jua, FontWeight.Normal),
    headlineSmall = BaseTypography.headlineSmall.appFont(Jua, FontWeight.Normal),
    titleLarge = BaseTypography.titleLarge.appFont(Jua, FontWeight.Normal),
    titleMedium = BaseTypography.titleMedium.appFont(Jua, FontWeight.Normal),
    titleSmall = BaseTypography.titleSmall.appFont(Jua, FontWeight.Normal),
    bodyLarge = BaseTypography.bodyLarge.appFont(GmarketSans, FontWeight.Medium),
    bodyMedium = BaseTypography.bodyMedium.appFont(GmarketSans, FontWeight.Medium),
    bodySmall = BaseTypography.bodySmall.appFont(GmarketSans, FontWeight.Medium),
    labelLarge = BaseTypography.labelLarge.appFont(GmarketSans, FontWeight.Bold),
    labelMedium = BaseTypography.labelMedium.appFont(GmarketSans, FontWeight.Medium),
    labelSmall = BaseTypography.labelSmall.appFont(GmarketSans, FontWeight.Medium),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightScheme, typography = AppTypography) {
        // 일반 Text는 본문 역할의 Gmarket Sans를 자동으로 상속한다.
        CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.bodyLarge) {
            content()
        }
    }
}
