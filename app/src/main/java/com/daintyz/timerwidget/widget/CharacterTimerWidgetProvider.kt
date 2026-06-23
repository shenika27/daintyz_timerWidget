package com.daintyz.timerwidget.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import com.daintyz.timerwidget.data.TimerPreferences
import com.daintyz.timerwidget.model.TimerState
import com.daintyz.timerwidget.service.TimerForegroundService

/**
 * 위젯 UI 렌더링 담당 (설계 문서 4-3).
 *
 * 버튼 액션은 [com.daintyz.timerwidget.receiver.TimerActionReceiver]가 받아 상태를 전환하고,
 * 갱신은 [WidgetUpdater]를 통해 일원화된다. Provider는 시스템의 onUpdate/배치 시점 렌더링만 책임진다.
 */
class CharacterTimerWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // 위젯이 (재)배치되거나 시스템이 갱신을 요청할 때: 저장된 상태로 정확히 복구 렌더링 (설계 문서 8장).
        WidgetUpdater.updateAllWidgets(context)

        // 위젯 재배치 시점에 진행/일시정지 중이던 타이머가 있으면 서비스 복구.
        val data = TimerPreferences.get(context).load()
        if (data.state == TimerState.RUNNING || data.state == TimerState.COMPLETE) {
            TimerForegroundService.ensureRunning(context)
        }
    }

    override fun onEnabled(context: Context) {
        // 첫 위젯 배치 시 1회. 초기 렌더링 보장.
        WidgetUpdater.updateAllWidgets(context)
    }

    override fun onDisabled(context: Context) {
        // 마지막 위젯 제거 시: 서비스 정리.
        TimerForegroundService.stop(context)
    }
}
