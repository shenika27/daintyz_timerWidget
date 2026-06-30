package com.daintyz.timerwidget.controller

import android.content.Context
import android.os.SystemClock
import com.daintyz.timerwidget.data.TimerPreferences
import com.daintyz.timerwidget.model.LayoutMode
import com.daintyz.timerwidget.model.TimerData
import com.daintyz.timerwidget.model.TimerState
import com.daintyz.timerwidget.notification.CompletionFeedback
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

    private const val MAX_SECONDS = 999 * 60
    private const val MIN_SECONDS = 10
    private const val ONE_MINUTE_SECONDS = 60
    private const val SUB_MINUTE_STEP_SECONDS = 10
    private const val MIN_STEP_SECONDS = 5 // 증감 단위 최소값(초)

    // ---- 정지 상태에서 시간 증감 ----

    // 1분 미만은 10초 단위. 1분 이상은 "설정 배수의 격자"에 스냅한다.
    // 예) 배수 5분: … 50초 → 1:00 → 5:00 → 10:00 (6:00 같은 어중간한 값이 안 생김).
    fun increment(context: Context) = mutateIdleSeconds(context) { currentSeconds ->
        if (currentSeconds < ONE_MINUTE_SECONDS) {
            currentSeconds + SUB_MINUTE_STEP_SECONDS
        } else {
            val step = currentStepSeconds(context)
            ((currentSeconds / step) + 1) * step // 다음 배수로 올림 스냅
        }
    }

    fun decrement(context: Context) = mutateIdleSeconds(context) { currentSeconds ->
        if (currentSeconds <= ONE_MINUTE_SECONDS) {
            currentSeconds - SUB_MINUTE_STEP_SECONDS
        } else {
            val step = currentStepSeconds(context)
            val prev = ((currentSeconds - 1) / step) * step // 이전 배수로 내림 스냅
            if (prev < ONE_MINUTE_SECONDS) ONE_MINUTE_SECONDS else prev // 1분 미만으로는 1:00에 멈춤(그 아래는 10초 단위)
        }
    }

    private fun currentStepSeconds(context: Context): Int =
        TimerPreferences.get(context).load().stepSeconds.coerceAtLeast(MIN_STEP_SECONDS)

    private inline fun mutateIdleSeconds(context: Context, delta: (Int) -> Int) {
        val prefs = TimerPreferences.get(context)
        val data = prefs.load()
        if (data.state != TimerState.IDLE) return // 증감은 정지 상태에서만
        val next = delta(data.lastSetSeconds).coerceIn(MIN_SECONDS, MAX_SECONDS)
        prefs.save(data.copy(lastSetSeconds = next))
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
        val total = data.lastSetSeconds * 1_000L
        if (total <= 0L) return
        val now = SystemClock.elapsedRealtime()
        TimerPreferences.get(context).save(
            data.copy(
                state = TimerState.RUNNING,
                stateEnteredElapsed = now,
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
            data.copy(state = TimerState.PAUSED, stateEnteredElapsed = now, remainingMillisAtPause = remaining)
        )
        // 카운트다운은 멈추지만 일시정지 애니메이션을 한 번 재생해야 하므로 서비스를 바로 끄지 않고 재동기화한다.
        // 서비스는 애니메이션이 마지막 프레임에 도달하면 스스로 종료한다(TimerForegroundService.tick).
        TimerForegroundService.resync(context)
        WidgetUpdater.updateAllWidgets(context)
    }

    private fun resume(context: Context, data: TimerData) {
        val now = SystemClock.elapsedRealtime()
        TimerPreferences.get(context).save(
            data.copy(
                state = TimerState.RUNNING,
                stateEnteredElapsed = now,
                targetEndElapsed = now + data.remainingMillisAtPause,
                remainingMillisAtPause = 0L
            )
        )
        TimerForegroundService.ensureRunning(context)
        WidgetUpdater.updateAllWidgets(context)
    }

    // ---- 중단/리셋 ----

    /** 진행/일시정지 → 정지(Idle). 직전 설정 시간(lastSetSeconds)은 유지 (설계 문서 3-2). */
    fun stopReset(context: Context) {
        CompletionFeedback.stop()
        val data = TimerPreferences.get(context).load()
        TimerPreferences.get(context).save(
            data.copy(
                state = TimerState.IDLE,
                stateEnteredElapsed = SystemClock.elapsedRealtime(),
                targetEndElapsed = 0L,
                remainingMillisAtPause = 0L,
                totalMillis = 0L
            )
        )
        // 정지 직후 stop 애니메이션을 한 번 재생해야 하므로 서비스를 바로 끄지 않고 재동기화한다.
        // 서비스는 IDLE 애니메이션이 마지막 프레임에 도달하면 스스로 종료한다(TimerForegroundService.tick).
        TimerForegroundService.resync(context)
        WidgetUpdater.updateAllWidgets(context)
    }

    // ---- 완료 처리 (서비스의 틱에서 만료 감지 시 호출) ----

    fun complete(context: Context) {
        val data = TimerPreferences.get(context).load()
        if (data.state != TimerState.RUNNING) return
        TimerPreferences.get(context).save(
            data.copy(
                state = TimerState.COMPLETE,
                stateEnteredElapsed = SystemClock.elapsedRealtime(),
                targetEndElapsed = 0L,
                remainingMillisAtPause = 0L
            )
        )
        TimerNotifications.notifyComplete(context)
        CompletionFeedback.fire(context) // 완료음·진동은 설정 토글에 따라 앱이 직접 재생
        WidgetUpdater.updateAllWidgets(context)
        // 서비스는 완료 승리 애니메이션을 위해 계속 유지 (탭 시 종료).
    }

    /** 완료 상태에서 위젯 탭 → 정지(Idle) 복귀, 직전 설정 시간 유지 (설계 문서 3-3). */
    fun resetFromComplete(context: Context) {
        CompletionFeedback.stop()
        val data = TimerPreferences.get(context).load()
        if (data.state != TimerState.COMPLETE) return
        TimerPreferences.get(context).save(
            data.copy(state = TimerState.IDLE, stateEnteredElapsed = SystemClock.elapsedRealtime())
        )
        TimerNotifications.cancelComplete(context)
        // 완료 → 정지 복귀 시에도 stop 애니메이션을 한 번 재생(서비스가 끝나면 스스로 종료).
        TimerForegroundService.resync(context)
        WidgetUpdater.updateAllWidgets(context)
    }

    /** 완료 알림을 스와이프해 지웠을 때 소리·진동 반복만 멈추고 COMPLETE 상태는 유지한다. */
    fun dismissCompleteNotification(context: Context) {
        CompletionFeedback.stop()
    }

    // ---- 앱 화면 설정 변경 ----

    /** 증감 단위를 초 단위(분+초 환산값)로 설정. */
    fun setStepSeconds(context: Context, stepSeconds: Int) {
        val prefs = TimerPreferences.get(context)
        val s = stepSeconds.coerceIn(MIN_STEP_SECONDS, MAX_SECONDS)
        val data = prefs.load()
        // 배수 변경 시, 정지 상태면 타이머 시작시간을 해당 배수의 최소값(=1스텝)으로 맞춘다.
        // 예) 5분 배수로 바꾸면 위젯이 5:00으로 설정된다. (진행/일시정지 중엔 건드리지 않음)
        val nextSeconds = if (data.state == TimerState.IDLE) {
            s.coerceIn(MIN_SECONDS, MAX_SECONDS)
        } else {
            data.lastSetSeconds
        }
        prefs.save(data.copy(stepSeconds = s, lastSetSeconds = nextSeconds))
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
