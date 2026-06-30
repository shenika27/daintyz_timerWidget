package com.daintyz.timerwidget.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.daintyz.timerwidget.controller.TimerController
import com.daintyz.timerwidget.service.TimerForegroundService

/**
 * 위젯 버튼(+/-/▶/||/■)과 알람 복구 트리거의 액션 수신 (설계 문서 3-2, 4-1).
 *
 * 모든 상태 전환은 [TimerController]에 위임하여 한 곳에서 처리한다.
 */
class TimerActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_PLUS -> TimerController.increment(context)
            ACTION_MINUS -> TimerController.decrement(context)
            ACTION_START_PAUSE -> TimerController.startOrPause(context)
            ACTION_STOP_RESET -> TimerController.stopReset(context)
            ACTION_TAP_COMPLETE -> TimerController.resetFromComplete(context)
            ACTION_DISMISS_COMPLETE -> TimerController.dismissCompleteNotification(context)
            ACTION_ALARM_CHECK -> {
                // 화면 꺼짐 중 완료 시점 깨어남(또는 사망 복구): 서비스가 평가하도록 재시작.
                TimerForegroundService.resync(context)
            }
        }
    }

    companion object {
        const val ACTION_PLUS = "com.daintyz.timerwidget.action.PLUS"
        const val ACTION_MINUS = "com.daintyz.timerwidget.action.MINUS"
        const val ACTION_START_PAUSE = "com.daintyz.timerwidget.action.START_PAUSE"
        const val ACTION_STOP_RESET = "com.daintyz.timerwidget.action.STOP_RESET"
        const val ACTION_TAP_COMPLETE = "com.daintyz.timerwidget.action.TAP_COMPLETE"
        const val ACTION_DISMISS_COMPLETE = "com.daintyz.timerwidget.action.DISMISS_COMPLETE"
        const val ACTION_ALARM_CHECK = "com.daintyz.timerwidget.action.ALARM_CHECK"
    }
}
