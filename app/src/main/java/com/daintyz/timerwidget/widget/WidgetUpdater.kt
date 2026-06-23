package com.daintyz.timerwidget.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
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
import com.daintyz.timerwidget.model.TimerSkin
import com.daintyz.timerwidget.model.TimerState
import com.daintyz.timerwidget.receiver.TimerActionReceiver
import com.daintyz.timerwidget.skin.FrameAnimationController
import com.daintyz.timerwidget.skin.SkinRepository
import com.daintyz.timerwidget.ui.MainActivity

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
        // 각 영역의 비트맵은 그 영역 스킨의 skinId 폴더에서 로드되므로, 타이머용 함수엔 timerSkin을,
        // 캐릭터용 함수엔 characterSkin을 넘긴다. 못 찾으면 첫 스킨으로 폴백.
        val fallback = SkinRepository.loadAllSkins(context).firstOrNull()
        val characterSkin = SkinRepository.findSkin(context, data.selectedCharacterSkinId) ?: fallback
        val timerSkin = SkinRepository.findSkin(context, data.selectedTimerSkinId) ?: fallback

        applyTimerText(views, data, nowElapsed, timerSkin?.timer)
        applyButtonVisibility(context, views, data.state, timerSkin)
        applyButtonGraphics(context, views, data.state, timerSkin)
        applyTimerChrome(context, views, timerSkin)
        applyCharacterFrame(context, views, characterSkin, data, nowElapsed)
        if (!forPreview) {
            applyButtonActions(context, views)
            applyCharacterClick(context, views, data.state)
        }

        return views
    }

    // ---- 시간 텍스트 ----

    private fun applyTimerText(views: RemoteViews, data: TimerData, nowElapsed: Long, timerSkin: TimerSkin?) {
        // 완료 상태에선 남은 시간이 0 → "00:00"으로 표시된다. ("완료" 안내는 버튼 행 자리의 tv_complete가 담당)
        val text = formatMillis(data.remainingMillis(nowElapsed))
        views.setTextViewText(R.id.tv_timer_value, text)

        // 스킨이 숫자 폰트를 지정하면 덮어쓴다. 미지정 항목은 레이아웃 기본값 유지
        // (RemoteViews는 매번 레이아웃에서 새로 만들어지므로 노스킨이면 자동으로 기본값으로 복원됨).
        // color/size는 RemoteViews 지원(@RemotableViewMethod). family는 setFontFamily가 비-remotable이라
        // 직접 적용 불가 → 커스텀 글꼴은 추후 비트맵 렌더링으로 확장 (font.family는 스키마 자리만 보존).
        val font = timerSkin?.font ?: return
        font.color
            ?.let { runCatching { Color.parseColor(it) }.getOrNull() }
            ?.let {
                views.setTextColor(R.id.tv_timer_value, it)
                // 완료 안내 텍스트도 동일한 스킨 글자색을 따른다 (크기는 버튼 행에 맞춘 자체 값 유지).
                views.setTextColor(R.id.tv_complete, it)
            }
        font.sizeSp?.let { views.setTextViewTextSize(R.id.tv_timer_value, TypedValue.COMPLEX_UNIT_SP, it) }
    }

    private fun formatMillis(millis: Long): String {
        val totalSeconds = (millis + 999) / 1000 // 올림: 표시상 0:00이 너무 빨리 뜨지 않게
        val m = totalSeconds / 60
        val s = totalSeconds % 60
        return "%02d:%02d".format(m, s)
    }

    // ---- 버튼 가시성 (상태별) ----

    private fun applyButtonVisibility(context: Context, views: RemoteViews, state: TimerState, skin: Skin?) {
        val timerSkin = skin?.timer
        // 어떤 버튼 '영역'이 현재 상태에서 활성인지 (그림 유무와 무관, 탭 영역 기준).
        val minusOn = state == TimerState.IDLE
        val plusOn = state == TimerState.IDLE
        val startPauseOn = state == TimerState.IDLE || state == TimerState.RUNNING || state == TimerState.PAUSED
        val stopOn = state == TimerState.RUNNING || state == TimerState.PAUSED

        // 완료: 버튼 행 전체를 "완료" 텍스트 하나로 대체. 탭하면 정지(IDLE) 복귀.
        val isComplete = state == TimerState.COMPLETE
        views.setViewVisibility(R.id.btn_row, vis(!isComplete))
        views.setViewVisibility(R.id.tv_complete, vis(isComplete))

        views.setViewVisibility(R.id.btn_minus, vis(minusOn))
        views.setViewVisibility(R.id.btn_plus, vis(plusOn))
        views.setViewVisibility(R.id.btn_start_pause, vis(startPauseOn))
        views.setViewVisibility(R.id.btn_stop_reset, vis(stopOn))

        // 세로 구분선: showDividers + 옆 버튼이 보일 때만.
        val dividersOn = timerSkin?.showDividers == true
        views.setViewVisibility(R.id.div_minus, vis(dividersOn && minusOn))
        views.setViewVisibility(R.id.div_plus, vis(dividersOn && plusOn))
        views.setViewVisibility(R.id.div_stop, vis(dividersOn && stopOn))

        applyDividerGraphics(context, views, skin)
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

    // ---- 세로 구분선 그래픽 ----

    private fun applyDividerGraphics(context: Context, views: RemoteViews, skin: Skin?) {
        val timerSkin = skin?.timer
        val divIds = listOf(R.id.div_minus, R.id.div_plus, R.id.div_stop)
        val imgFile = timerSkin?.dividersImage
        val bitmap = if (skin != null && imgFile != null) {
            SkinRepository.loadFrameBitmap(context, skin.skinId, imgFile)
        } else null

        if (bitmap != null) {
            // 이미지 모드: PNG를 그리고 배경은 투명.
            val widthDp = timerSkin?.dividersWidthDp ?: 1f
            divIds.forEach { id ->
                views.setImageViewBitmap(id, bitmap)
                views.setInt(id, "setBackgroundColor", android.graphics.Color.TRANSPARENT)
                views.setViewLayoutWidth(id, widthDp, android.util.TypedValue.COMPLEX_UNIT_DIP)
            }
        } else {
            // 색 모드: 이미지 없음, 배경색으로 단색 선.
            val color = timerSkin?.dividersColor
                ?.let { runCatching { Color.parseColor(it) }.getOrNull() }
                ?: Color.parseColor("#2B2B2B")
            divIds.forEach { id ->
                views.setImageViewResource(id, android.R.color.transparent)
                views.setInt(id, "setBackgroundColor", color)
                views.setViewLayoutWidth(id, 1f, android.util.TypedValue.COMPLEX_UNIT_DIP)
            }
        }
    }

    // ---- 박스 배경 / 가로 구분선 ----

    private fun applyTimerChrome(context: Context, views: RemoteViews, skin: Skin?) {
        val timerSkin = skin?.timer
        val transparent = android.R.color.transparent
        // 스킨이 그린 박스 배경 PNG가 있으면 그것을 깔고 내장 박스는 끈다.
        val bgFile = timerSkin?.background
        val bgBitmap = if (skin != null && bgFile != null) {
            SkinRepository.loadFrameBitmap(context, skin.skinId, bgFile)
        } else null
        if (bgBitmap != null) {
            views.setImageViewBitmap(R.id.iv_timer_bg, bgBitmap)
            views.setInt(R.id.timer_box, "setBackgroundResource", transparent)
        } else {
            views.setImageViewResource(R.id.iv_timer_bg, transparent)
            val boxBg = if (timerSkin?.showBox == true) R.drawable.timer_box_bg else transparent
            views.setInt(R.id.timer_box, "setBackgroundResource", boxBg)
        }
        val showH = timerSkin?.showDividerH == true
        views.setViewVisibility(R.id.div_h, vis(showH))
        if (showH) {
            val hImgFile = timerSkin?.dividerHImage
            val hBitmap = if (skin != null && hImgFile != null) {
                SkinRepository.loadFrameBitmap(context, skin.skinId, hImgFile)
            } else null
            if (hBitmap != null) {
                views.setImageViewBitmap(R.id.div_h, hBitmap)
                views.setInt(R.id.div_h, "setBackgroundColor", android.graphics.Color.TRANSPARENT)
                val heightDp = timerSkin?.dividerHHeightDp ?: 1f
                views.setViewLayoutHeight(R.id.div_h, heightDp, android.util.TypedValue.COMPLEX_UNIT_DIP)
            } else {
                views.setImageViewResource(R.id.div_h, transparent)
                views.setInt(R.id.div_h, "setBackgroundColor", Color.parseColor("#2B2B2B"))
                views.setViewLayoutHeight(R.id.div_h, 1f, android.util.TypedValue.COMPLEX_UNIT_DIP)
            }
        }
    }

    // ---- 버튼 액션 (PendingIntent) ----

    private fun applyButtonActions(context: Context, views: RemoteViews) {
        views.setOnClickPendingIntent(R.id.btn_minus, broadcast(context, TimerActionReceiver.ACTION_MINUS))
        views.setOnClickPendingIntent(R.id.btn_plus, broadcast(context, TimerActionReceiver.ACTION_PLUS))
        views.setOnClickPendingIntent(R.id.btn_start_pause, broadcast(context, TimerActionReceiver.ACTION_START_PAUSE))
        views.setOnClickPendingIntent(R.id.btn_stop_reset, broadcast(context, TimerActionReceiver.ACTION_STOP_RESET))
        // 완료 상태의 "완료" 텍스트 탭 → 정지(IDLE) 복귀 (캐릭터 탭과 동일 동작)
        views.setOnClickPendingIntent(R.id.tv_complete, broadcast(context, TimerActionReceiver.ACTION_TAP_COMPLETE))
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
            totalMs = data.totalMillis
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
        val pending = if (state == TimerState.COMPLETE) {
            // 완료 상태: 탭하면 정지(Idle)로 복귀 (직전 설정 시간 유지)
            broadcast(context, TimerActionReceiver.ACTION_TAP_COMPLETE)
        } else {
            // 그 외: 앱 열기
            val intent = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        views.setOnClickPendingIntent(R.id.iv_character, pending)
    }
}
