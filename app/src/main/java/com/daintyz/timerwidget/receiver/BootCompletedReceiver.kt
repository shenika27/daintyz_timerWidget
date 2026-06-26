package com.daintyz.timerwidget.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.daintyz.timerwidget.data.TimerPreferences
import com.daintyz.timerwidget.model.TimerState
import com.daintyz.timerwidget.widget.WidgetUpdater

/**
 * 재부팅 후 진행/완료 중이던 타이머 상태 복구 (설계 문서 4-1, 8장).
 *
 * 주의: elapsedRealtime()은 재부팅 시 0으로 리셋되므로, 재부팅을 가로지른 RUNNING의
 * 목표 종료 시각은 더 이상 유효하지 않다. 1차 버전에서는 안전하게 정지(Idle)로 복구하되
 * 직전 설정 시간(lastSetSeconds)은 유지한다. (완료 상태였다면 완료 표시 유지)
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val prefs = TimerPreferences.get(context)
        val data = prefs.load()
        if (data.state == TimerState.RUNNING || data.state == TimerState.PAUSED) {
            // 재부팅으로 elapsedRealtime 기준이 무효화됨 → 정지 상태로 안전 복구.
            prefs.save(
                data.copy(
                    state = TimerState.IDLE,
                    targetEndElapsed = 0L,
                    remainingMillisAtPause = 0L,
                    totalMillis = 0L
                )
            )
        }
        WidgetUpdater.updateAllWidgets(context)
    }
}
