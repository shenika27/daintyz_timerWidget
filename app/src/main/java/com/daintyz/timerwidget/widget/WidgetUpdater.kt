package com.daintyz.timerwidget.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.SystemClock
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.daintyz.timerwidget.R
import com.daintyz.timerwidget.data.TimerPreferences
import com.daintyz.timerwidget.model.ButtonStyle
import com.daintyz.timerwidget.model.LayoutMode
import com.daintyz.timerwidget.model.Skin
import com.daintyz.timerwidget.model.TimerData
import com.daintyz.timerwidget.model.TimerState
import com.daintyz.timerwidget.receiver.TimerActionReceiver
import com.daintyz.timerwidget.skin.FrameAnimationController
import com.daintyz.timerwidget.skin.SkinAvailabilityChecker
import com.daintyz.timerwidget.skin.SkinRepository

/**
 * RemoteViews 생성/갱신 로직의 단일 진입점 (설계 문서 2-1, 4-3).
 *
 * 화면 ON/OFF, 1초 틱, 상태 전환 등 모든 위젯 갱신은 [updateAllWidgets]를 거친다.
 * top/bottom 레이아웃은 동일 id를 쓰므로 분기는 initialLayout 선택 시점에만 발생한다.
 */
object WidgetUpdater {

    /** 모든 위젯 인스턴스를 현재 상태로 갱신. */
    fun updateAllWidgets(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val component = ComponentName(context, CharacterTimerWidgetProvider::class.java)
        val ids = manager.getAppWidgetIds(component)
        if (ids.isEmpty()) return
        val data = TimerPreferences.get(context).load()
        val now = SystemClock.elapsedRealtime()
        for (id in ids) {
            manager.updateAppWidget(id, buildRemoteViews(context, data, now))
        }
    }

    /**
     * @param forPreview 미리보기(앱 내 [RemoteViews.apply] 렌더)용이면 true.
     *   버튼/캐릭터 클릭 PendingIntent 바인딩을 건너뛴다 — 미리보기 탭이 실제 타이머 상태를 바꾸지 않도록.
     *
     * 이 함수는 1초 틱 경로에서도 호출된다. Billing/네트워크 동기화는 절대 여기서 하지 않고,
     * 이미 [TimerData]에 캐시된 권한만 사용해 렌더링 여부를 판단한다.
     */
    fun buildRemoteViews(
        context: Context,
        data: TimerData,
        nowElapsed: Long,
        forPreview: Boolean = false
    ): RemoteViews {
        val layoutRes = when (data.layoutMode) {
            LayoutMode.TOP -> R.layout.widget_timer_top
            LayoutMode.BOTTOM -> R.layout.widget_timer_bottom
        }
        val views = RemoteViews(context.packageName, layoutRes)

        // 캐릭터와 타이머는 서로 다른 스킨(테마)에서 올 수 있다 — 사용자가 독립 선택.
        // 로컬 파일이 남아 있어도 현재 Play/기프트 권한이 없으면 렌더링하지 않고 기본 스킨으로 폴백한다.
        val skins = SkinRepository.loadAllSkins(context)
        val bundledFallbackSkins = SkinRepository.loadBundledSkins(context)
        val characterSkin = resolveRenderableSkin(data.selectedCharacterSkinId, skins, bundledFallbackSkins, data)
        val timerSkin = resolveRenderableSkin(data.selectedTimerSkinId, skins, bundledFallbackSkins, data)

        applyTimerText(context, views, data, nowElapsed, timerSkin)
        applyButtonVisibility(views, data.state)
        applyButtonGraphics(context, views, data.state, timerSkin)
        applyTimerChrome(context, views, timerSkin)
        applyCharacterFrame(context, views, characterSkin, data, nowElapsed)
        if (!forPreview) {
            applyButtonActions(context, views, data.state)
            applyCharacterClick(context, views, data.state)
        }

        return views
    }

    internal fun resolveRenderableSkin(
        selectedSkinId: String,
        skins: List<Skin>,
        bundledFallbackSkins: List<Skin>,
        data: TimerData
    ): Skin? {
        fun Skin.available() = SkinAvailabilityChecker.isSkinAvailable(
            this,
            purchasedSkinIds = data.purchasedSkinIds,
            hasLifetimePass = data.hasEffectiveLifetimePass,
            giftUnlockedSkinIds = data.giftUnlockedSkinIds
        )

        val selected = skins.firstOrNull { it.skinId == selectedSkinId }
        if (selected != null && selected.available()) return selected

        val bundledFree = bundledFallbackSkins.filter { it.bundled && it.isFree }
        bundledFree.firstOrNull { it.skinId == TimerPreferences.DEFAULT_SKIN_ID }?.let { return it }

        return bundledFree.firstOrNull()
    }

    // ---- 시간 텍스트 ----

    private fun applyTimerText(context: Context, views: RemoteViews, data: TimerData, nowElapsed: Long, skin: Skin?) {
        // 완료 상태에선 남은 시간이 0 → "00:00"으로 표시된다. (완료 화면은 IDLE과 동일, 시간 탭만 초기화 동작)
        val text = formatMillis(data.remainingMillis(nowElapsed))
        val font = skin?.timer?.font
        val colorInt = font?.color?.let { runCatching { Color.parseColor(it) }.getOrNull() }

        // 커스텀 폰트(.ttf): RemoteViews는 setFontFamily가 비-remotable이라 TextView에 직접 못 먹인다.
        // → Typeface로 숫자를 비트맵 렌더링해 ImageView에 표시(매 틱 재생성, Typeface는 캐시).
        val typeface = if (skin != null && font?.file != null) {
            SkinRepository.loadTypeface(context, skin.skinId, font.file)
        } else null

        if (typeface != null) {
            val color = colorInt ?: context.getColor(R.color.timer_digit)
            views.setImageViewBitmap(R.id.iv_timer_value, renderTimeBitmap(text, typeface, color))
            views.setViewVisibility(R.id.iv_timer_value, View.VISIBLE)
            views.setViewVisibility(R.id.tv_timer_value, View.GONE)
            return
        }

        // 내장 폰트: TextView 경로. color/size는 RemoteViews 지원(@RemotableViewMethod).
        // family(setFontFamily)는 비-remotable이라 미적용 — 내장 패밀리는 레이아웃 기본(monospace) 유지.
        views.setViewVisibility(R.id.iv_timer_value, View.GONE)
        views.setViewVisibility(R.id.tv_timer_value, View.VISIBLE)
        views.setTextViewText(R.id.tv_timer_value, text)
        colorInt?.let { views.setTextColor(R.id.tv_timer_value, it) }
        font?.sizeSp?.let { views.setTextViewTextSize(R.id.tv_timer_value, TypedValue.COMPLEX_UNIT_SP, it) }
    }

    /**
     * 시간 문자열을 커스텀 Typeface로 그린 비트맵. fitCenter ImageView가 시간 영역에 맞춰 축소한다.
     *
     * 숫자의 "크기감"(흰 박스 안 여백)은 **비트맵에 비례(%)로 굽는 [PAD_FRAC]**으로 정한다.
     * iv_timer_value의 dp padding으로 정하면 영역이 작은 위젯과 큰 미리보기에서 padding 비율이 달라져
     * 숫자 크기가 어긋난다(절대 dp / 가변 영역). 비례 여백은 fitCenter가 영역에 맞춰 같이 스케일되므로
     * 위젯·미리보기에서 동일한 비율을 유지한다.
     */
    private fun renderTimeBitmap(text: String, typeface: Typeface, colorInt: Int): Bitmap {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = 120f // 고해상 렌더 후 fitCenter로 축소 → 선명도 확보
            color = colorInt
            textAlign = Paint.Align.LEFT
        }
        val bounds = Rect()
        paint.getTextBounds(text, 0, text.length, bounds)
        // 글자 변의 PAD_FRAC 만큼을 사방 투명 여백으로 → 채움 비율 = 1/(1+2*PAD_FRAC) (값↑ = 숫자 작아짐).
        val padX = (bounds.width() * PAD_FRAC).toInt()
        val padY = (bounds.height() * PAD_FRAC).toInt()
        val w = (bounds.width() + padX * 2).coerceAtLeast(1)
        val h = (bounds.height() + padY * 2).coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawText(text, (padX - bounds.left).toFloat(), (padY - bounds.top).toFloat(), paint)
        return bitmap
    }

    /** 커스텀 폰트 숫자의 사방 여백 비율(글자 변 대비). cha01 'comfort' 가이드 ≈ 채움 77%. 키우면 숫자가 작아진다. */
    private const val PAD_FRAC = 0.15f

    private fun formatMillis(millis: Long): String {
        val totalSeconds = (millis + 999) / 1000 // 올림: 표시상 0:00이 너무 빨리 뜨지 않게
        val m = totalSeconds / 60
        val s = totalSeconds % 60
        return "%02d:%02d".format(m, s)
    }

    // ---- 버튼 가시성 (상태별) ----

    private fun applyButtonVisibility(views: RemoteViews, state: TimerState) {
        // 항상 2버튼: 정지/완료 → [+][−], 진행/중지 → [일시정지·재생][정지]. (탭 영역 기준)
        val minusPlusOn = state == TimerState.IDLE || state == TimerState.COMPLETE
        val startStopOn = state == TimerState.RUNNING || state == TimerState.PAUSED

        views.setViewVisibility(R.id.btn_minus, vis(minusPlusOn))
        views.setViewVisibility(R.id.btn_plus, vis(minusPlusOn))
        views.setViewVisibility(R.id.btn_start_pause, vis(startStopOn))
        views.setViewVisibility(R.id.btn_stop_reset, vis(startStopOn))
    }

    private fun vis(on: Boolean) = if (on) View.VISIBLE else View.GONE

    // ---- 버튼 그림 (디폴트 기호 vs 노스킨 투명) ----

    private fun applyButtonGraphics(context: Context, views: RemoteViews, state: TimerState, skin: Skin?) {
        val style = skin?.timer?.buttonStyle ?: ButtonStyle.NONE
        val startPauseVector =
            if (state == TimerState.RUNNING) R.drawable.ic_btn_pause else R.drawable.ic_btn_play
        when (style) {
            // 디폴트: 내장 기호 벡터.
            ButtonStyle.DEFAULT -> {
                views.setImageViewResource(R.id.btn_minus, R.drawable.ic_btn_minus)
                views.setImageViewResource(R.id.btn_plus, R.drawable.ic_btn_plus)
                views.setImageViewResource(R.id.btn_stop_reset, R.drawable.ic_btn_stop)
                views.setImageViewResource(R.id.btn_start_pause, startPauseVector)
            }
            // 노스킨/NONE: 그림은 투명, 탭 영역(ImageView)은 그대로 유지.
            ButtonStyle.NONE -> {
                val transparent = android.R.color.transparent
                views.setImageViewResource(R.id.btn_minus, transparent)
                views.setImageViewResource(R.id.btn_plus, transparent)
                views.setImageViewResource(R.id.btn_stop_reset, transparent)
                views.setImageViewResource(R.id.btn_start_pause, transparent)
            }
            // 스킨 그림: 제공된 PNG, 누락 심볼은 내장 벡터로 폴백.
            ButtonStyle.SKIN -> {
                val b = skin?.timer?.buttons
                applySkinButton(context, views, R.id.btn_minus, skin, b?.minus, R.drawable.ic_btn_minus)
                applySkinButton(context, views, R.id.btn_plus, skin, b?.plus, R.drawable.ic_btn_plus)
                applySkinButton(context, views, R.id.btn_stop_reset, skin, b?.stop, R.drawable.ic_btn_stop)
                val startPauseFile = if (state == TimerState.RUNNING) b?.pause else b?.play
                applySkinButton(context, views, R.id.btn_start_pause, skin, startPauseFile, startPauseVector)
            }
        }
    }

    /** SKIN 버튼 한 칸: 파일명이 있으면 비트맵, 없거나 로드 실패하면 내장 벡터로 폴백. */
    private fun applySkinButton(
        context: Context,
        views: RemoteViews,
        viewId: Int,
        skin: Skin?,
        fileName: String?,
        fallbackVector: Int
    ) {
        val bitmap = if (skin != null && fileName != null) {
            SkinRepository.loadFrameBitmap(context, skin.skinId, fileName)
        } else null
        if (bitmap != null) {
            views.setImageViewBitmap(viewId, bitmap)
        } else {
            views.setImageViewResource(viewId, fallbackVector)
        }
    }

    // ---- 박스 배경 (선·박스는 스킨 timer_theme PNG에 포함) ----

    private fun applyTimerChrome(context: Context, views: RemoteViews, skin: Skin?) {
        val timerSkin = skin?.timer
        val transparent = android.R.color.transparent
        // 스킨이 그린 배경 PNG(박스·구분선 포함)가 있으면 그것을 깔고 내장 박스는 끈다.
        val bgFile = timerSkin?.background
        val bgBitmap = if (skin != null && bgFile != null) {
            SkinRepository.loadFrameBitmap(context, skin.skinId, bgFile)
        } else null
        if (bgBitmap != null) {
            views.setImageViewBitmap(R.id.iv_timer_bg, bgBitmap)
            views.setInt(R.id.timer_box, "setBackgroundResource", transparent)
        } else {
            // 무배경 폴백(주로 개발/내장 테스트 스킨): 내장 박스 모양만.
            views.setImageViewResource(R.id.iv_timer_bg, transparent)
            val boxBg = if (timerSkin?.showBox == true) R.drawable.timer_box_bg else transparent
            views.setInt(R.id.timer_box, "setBackgroundResource", boxBg)
        }
    }

    // ---- 버튼 액션 (PendingIntent) ----

    private fun applyButtonActions(context: Context, views: RemoteViews, state: TimerState) {
        views.setOnClickPendingIntent(R.id.btn_minus, broadcast(context, TimerActionReceiver.ACTION_MINUS))
        views.setOnClickPendingIntent(R.id.btn_plus, broadcast(context, TimerActionReceiver.ACTION_PLUS))
        views.setOnClickPendingIntent(R.id.btn_start_pause, broadcast(context, TimerActionReceiver.ACTION_START_PAUSE))
        views.setOnClickPendingIntent(R.id.btn_stop_reset, broadcast(context, TimerActionReceiver.ACTION_STOP_RESET))
        // 시간 영역 탭: 완료면 이전 설정시간으로 초기화(IDLE 복귀), 그 외엔 시작/일시정지/재개 토글.
        val timeAction = if (state == TimerState.COMPLETE) {
            TimerActionReceiver.ACTION_TAP_COMPLETE
        } else {
            TimerActionReceiver.ACTION_START_PAUSE
        }
        views.setOnClickPendingIntent(R.id.time_area, broadcast(context, timeAction))
    }

    private fun broadcast(context: Context, action: String): PendingIntent {
        val intent = Intent(context, TimerActionReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // ---- 캐릭터 프레임 ----

    private fun applyCharacterFrame(context: Context, views: RemoteViews, skin: Skin?, data: TimerData, nowElapsed: Long) {
        if (skin == null) {
            views.setImageViewResource(R.id.iv_character, R.drawable.frame_placeholder)
            return
        }
        val ctx = FrameAnimationController.FrameContext(
            remainingMs = data.remainingMillis(nowElapsed),
            elapsedMs = (data.totalMillis - data.remainingMillis(nowElapsed)).coerceAtLeast(0L),
            totalMs = data.totalMillis,
            stateEnteredElapsed = data.stateEnteredElapsed
        )
        val fileName = FrameAnimationController.currentFrameFile(skin, data.state, ctx, nowElapsed)
        val bitmap = SkinRepository.loadFrameBitmap(context, skin.skinId, fileName)
        if (bitmap != null) {
            views.setImageViewBitmap(R.id.iv_character, bitmap)
        } else {
            views.setImageViewResource(R.id.iv_character, R.drawable.frame_placeholder)
        }
    }

    // ---- 캐릭터 클릭 ----

    private fun applyCharacterClick(context: Context, views: RemoteViews, state: TimerState) {
        // 캐릭터 단일 탭: 완료면 정지(Idle) 복귀, 그 외엔 재생/일시정지 토글(시간 영역 탭과 동일).
        // 위젯(RemoteViews)은 더블탭/제스처를 지원하지 않아 단일 탭 동작만 둔다. 앱은 런처 아이콘으로 진입.
        val action = if (state == TimerState.COMPLETE) {
            TimerActionReceiver.ACTION_TAP_COMPLETE
        } else {
            TimerActionReceiver.ACTION_START_PAUSE
        }
        views.setOnClickPendingIntent(R.id.iv_character, broadcast(context, action))
    }
}
