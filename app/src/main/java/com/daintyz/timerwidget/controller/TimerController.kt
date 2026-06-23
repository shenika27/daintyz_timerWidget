package com.daintyz.timerwidget.controller

import android.content.Context
import android.os.SystemClock
import com.daintyz.timerwidget.data.TimerPreferences
import com.daintyz.timerwidget.model.LayoutMode
import com.daintyz.timerwidget.model.TimerData
import com.daintyz.timerwidget.model.TimerState
import com.daintyz.timerwidget.notification.TimerNotifications
import com.daintyz.timerwidget.service.TimerForegroundService
import com.daintyz.timerwidget.widget.WidgetUpdater

/**
 * 모든 타이머 상태 전환을 한 곳에 모은 단일 컨트롤러.
 *
 * 위젯 버튼 액션, 앱 화면, 서비스(만료 감지)가 전부 이 함수들을 호출한다.
 * 각 전환은 (1) SharedPreferences 갱신 (2) 포그라운드 서비스 시작/정지 (3) 위젯 즉시 갱신을 일관되게 수행한다.
 */
object TimerController {

    private const val MAX_MINUTES = 999
    private const val MIN_MINUTES = 1

    // ---- 정지 상태에서 시간 증감 ----

    fun increment(context: Context) = mutateIdleMinutes(context) { it + currentStep(context) }
    fun decrement(context: Context) = mutateIdleMinutes(context) { it - currentStep(context) }

    private fun currentStep(context: Context): Int =
        TimerPreferences.get(context).load().stepMinutes.coerceAtLeast(1)

    private inline fun mutateIdleMinutes(context: Context, delta: (Int) -> Int) {
        val prefs = TimerPreferences.get(context)
        val data = prefs.load()
        if (data.state != TimerState.IDLE) return // 증감은 정지 상태에서만
        val next = delta(data.lastSetMinutes).coerceIn(MIN_MINUTES, MAX_MINUTES)
        prefs.save(data.copy(lastSetMinutes = next))
        WidgetUpdater.updateAllWidgets(context)
    }

    // ---- 시작/일시정지/재생 토글 ----

    fun startOrPause(context: Context) {
        val data = TimerPreferences.get(context).load()
        when (data.state) {
            TimerState.IDLE -> start(context, data)
            TimerState.RUNNING -> pause(context, data)
            TimerState.PAUSED -> resume(context, data)
            TimerState.COMPLETE -> Unit
        }
    }

    private fun start(context: Context, data: TimerData) {
        val total = data.lastSetMinutes * 60_000L
        if (total <= 0L) return
        val now = SystemClock.elapsedRealtime()
        TimerPreferences.get(context).save(
            data.copy(
                state = TimerState.RUNNING,
                targetEndElapsed = now + total,
                totalMillis = total,
                remainingMillisAtPause = 0L
            )
        )
        TimerForegroundService.ensureRunning(context)
        WidgetUpdater.updateAllWidgets(context)
    }

    private fun pause(context: Context, data: TimerData) {
        val now = SystemClock.elapsedRealtime()
        val remaining = (data.targetEndElapsed - now).coerceAtLeast(0L)
        TimerPreferences.get(context).save(
            data.copy(state = TimerState.PAUSED, remainingMillisAtPause = remaining)
        )
        // 일시정지는 카운트다운이 멈추므로 서비스 종료(시간은 prefs로 보존). 재생 시 재시작.
        TimerForegroundService.stop(context)
        WidgetUpdater.updateAllWidgets(context)
    }

    private fun resume(context: Context, data: TimerData) {
        val now = SystemClock.elapsedRealtime()
        TimerPreferences.get(context).save(
            data.copy(
                state = TimerState.RUNNING,
                targetEndElapsed = now + data.remainingMillisAtPause,
                remainingMillisAtPause = 0L
            )
        )
        TimerForegroundService.ensureRunning(context)
        WidgetUpdater.updateAllWidgets(context)
    }

    // ---- 중단/리셋 ----

    /** 진행/일시정지 → 정지(Idle). 직전 설정 시간(lastSetMinutes)은 유지 (설계 문서 3-2). */
    fun stopReset(context: Context) {
        val data = TimerPreferences.get(context).load()
        TimerPreferences.get(context).save(
            data.copy(
                state = TimerState.IDLE,
                targetEndElapsed = 0L,
                remainingMillisAtPause = 0L,
                totalMillis = 0L
            )
        )
        TimerForegroundService.stop(context)
        WidgetUpdater.updateAllWidgets(context)
    }

    // ---- 완료 처리 (서비스의 틱에서 만료 감지 시 호출) ----

    fun complete(context: Context) {
        val data = TimerPreferences.get(context).load()
        if (data.state != TimerState.RUNNING) return
        TimerPreferences.get(context).save(
            data.copy(
                state = TimerState.COMPLETE,
                targetEndElapsed = 0L,
                remainingMillisAtPause = 0L
            )
        )
        TimerNotifications.notifyComplete(context)
        WidgetUpdater.updateAllWidgets(context)
        // 서비스는 완료 승리 애니메이션을 위해 계속 유지 (탭 시 종료).
    }

    /** 완료 상태에서 위젯 탭 → 정지(Idle) 복귀, 직전 설정 시간 유지 (설계 문서 3-3). */
    fun resetFromComplete(context: Context) {
        val data = TimerPreferences.get(context).load()
        if (data.state != TimerState.COMPLETE) return
        TimerPreferences.get(context).save(data.copy(state = TimerState.IDLE))
        TimerNotifications.cancelComplete(context)
        TimerForegroundService.stop(context)
        WidgetUpdater.updateAllWidgets(context)
    }

    // ---- 앱 화면 설정 변경 ----

    fun setStepMinutes(context: Context, step: Int) {
        val prefs = TimerPreferences.get(context)
        prefs.save(prefs.load().copy(stepMinutes = step.coerceAtLeast(1)))
        WidgetUpdater.updateAllWidgets(context)
    }

    fun setLayoutMode(context: Context, mode: LayoutMode) {
        val prefs = TimerPreferences.get(context)
        prefs.save(prefs.load().copy(layoutMode = mode))
        WidgetUpdater.updateAllWidgets(context) // 즉시 위젯 강제 갱신 (설계 문서 2-1)
    }

    /** 캐릭터 영역 스킨 적용 (타이머 선택은 그대로 유지). */
    fun selectCharacterSkin(context: Context, skinId: String) {
        val prefs = TimerPreferences.get(context)
        prefs.save(prefs.load().copy(selectedCharacterSkinId = skinId))
        WidgetUpdater.updateAllWidgets(context)
    }

    /** 타이머 영역 스킨 적용 (캐릭터 선택은 그대로 유지). */
    fun selectTimerSkin(context: Context, skinId: String) {
        val prefs = TimerPreferences.get(context)
        prefs.save(prefs.load().copy(selectedTimerSkinId = skinId))
        WidgetUpdater.updateAllWidgets(context)
    }
}
