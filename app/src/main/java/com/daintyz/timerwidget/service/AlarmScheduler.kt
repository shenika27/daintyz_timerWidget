package com.daintyz.timerwidget.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.getSystemService
import com.daintyz.timerwidget.receiver.TimerActionReceiver

/**
 * 화면 꺼짐 중 완료 시점 깨우기 + 서비스 사망/재부팅 복구용 inexact 알람 (설계 문서 4-1, 4-2).
 *
 * ⚠️ setExactAndAllowWhileIdle는 프레임 갱신에 쓰지 않는다. 여기서는 완료/복구 트리거 용도로만,
 * 그것도 inexact [AlarmManager.set]만 사용하므로 SCHEDULE_EXACT_ALARM 권한이 필요 없다.
 */
object AlarmScheduler {

    private const val REQUEST_CODE = 9001

    /** [triggerAtElapsed]는 SystemClock.elapsedRealtime() 기준 목표 종료 시각(ms). */
    fun scheduleCompletion(context: Context, triggerAtElapsed: Long) {
        val alarm = context.getSystemService<AlarmManager>() ?: return
        alarm.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtElapsed, pendingIntent(context))
    }

    fun cancel(context: Context) {
        context.getSystemService<AlarmManager>()?.cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, TimerActionReceiver::class.java)
            .setAction(TimerActionReceiver.ACTION_ALARM_CHECK)
        return PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
