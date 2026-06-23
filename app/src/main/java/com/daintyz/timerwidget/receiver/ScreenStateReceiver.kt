package com.daintyz.timerwidget.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.daintyz.timerwidget.service.TimerForegroundService

/**
 * ACTION_SCREEN_ON / ACTION_SCREEN_OFF 감지용 동적 등록 BroadcastReceiver (설계 문서 4-2).
 *
 * SCREEN_ON/OFF는 매니페스트 정적 등록이 불가하므로 [TimerForegroundService]가 onCreate에서 동적 등록한다.
 * 수신 시 서비스에 화면 상태를 전달 → 화면 ON일 때만 1초 틱(애니메이션) 수행.
 */
class ScreenStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SCREEN_ON -> TimerForegroundService.notifyScreen(context, on = true)
            Intent.ACTION_SCREEN_OFF -> TimerForegroundService.notifyScreen(context, on = false)
        }
    }
}
