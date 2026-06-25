package com.daintyz.timerwidget.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.daintyz.timerwidget.controller.TimerController
import com.daintyz.timerwidget.data.TimerPreferences
import com.daintyz.timerwidget.model.TimerState
import com.daintyz.timerwidget.notification.TimerNotifications
import com.daintyz.timerwidget.receiver.ScreenStateReceiver
import com.daintyz.timerwidget.skin.FrameAnimationController
import com.daintyz.timerwidget.skin.SkinRepository
import com.daintyz.timerwidget.widget.WidgetUpdater

/**
 * 타이머 진행/완료 중 시간 추적 + 1초 틱 프레임 갱신을 담당하는 포그라운드 서비스 (설계 문서 4-1~4-3).
 *
 * - Handler(postDelayed) 1초 틱으로 (a) 남은 시간 계산 (b) 위젯 프레임 교체.
 * - 시간 계산은 SystemClock.elapsedRealtime() 기준 목표 종료 시각으로 → 절전 모드 오차 없음.
 * - 화면 ON일 때만 1초 틱(애니메이션). 화면 OFF면 틱 중단, 완료 시점에 inexact 알람으로 깨움.
 */
class TimerForegroundService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var screenOn = true
    private var foregroundStarted = false

    // 위젯은 애니메이션을 위해 빠르게(ANIM_INTERVAL_MS) 갱신하지만, 알림 텍스트는 초 단위로만 갱신한다.
    private var lastShownSecond = -1L

    private val screenReceiver = ScreenStateReceiver()
    private val tickRunnable = Runnable { tick() }

    override fun onCreate() {
        super.onCreate()
        TimerNotifications.ensureChannels(this)
        registerReceiver(
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureForeground()
        when (intent?.action) {
            ACTION_STOP -> {
                stopCleanly()
                return START_NOT_STICKY
            }
            ACTION_SCREEN_ON -> {
                screenOn = true
                AlarmScheduler.cancel(this)
                resyncAndSchedule()
            }
            ACTION_SCREEN_OFF -> {
                screenOn = false
                onScreenOff()
            }
            else -> resyncAndSchedule() // ACTION_START, ACTION_RESYNC, 시스템 재시작 등
        }
        return START_STICKY
    }

    /** 즉시 1회 평가 후, 화면이 켜져 있으면 1초 틱을 이어간다. */
    private fun resyncAndSchedule() {
        handler.removeCallbacks(tickRunnable)
        tick()
    }

    private fun tick() {
        val data = TimerPreferences.get(this).load()
        val now = SystemClock.elapsedRealtime()

        when (data.state) {
            TimerState.RUNNING -> {
                if (now >= data.targetEndElapsed) {
                    TimerController.complete(this) // → COMPLETE 전환 + 알림 + 위젯 갱신
                    updateForegroundNotification(getString(com.daintyz.timerwidget.R.string.notif_complete_title))
                    scheduleNextIfScreenOn()
                } else {
                    val remaining = data.targetEndElapsed - now
                    WidgetUpdater.updateAllWidgets(this) // 매 틱(애니메이션 프레임) 갱신
                    val sec = (remaining + 999) / 1000
                    if (sec != lastShownSecond) {           // 알림은 초 단위로만
                        lastShownSecond = sec
                        updateForegroundNotification(formatRemaining(remaining))
                    }
                    scheduleNextIfScreenOn()
                }
            }
            TimerState.COMPLETE -> {
                WidgetUpdater.updateAllWidgets(this) // 승리 애니메이션 루프
                scheduleNextIfScreenOn()
            }
            // 중지(IDLE)/일시정지(PAUSED): 일회성 애니메이션을 한 번 재생해야 하므로, 마지막 프레임에
            // 도달할 때까지 틱을 이어가고 다 재생되면(또는 화면이 꺼지면) 종료한다. (루프 없음)
            TimerState.IDLE, TimerState.PAUSED -> {
                WidgetUpdater.updateAllWidgets(this)
                val skin = SkinRepository.findSkin(this, data.selectedCharacterSkinId)
                val finished = skin == null || FrameAnimationController.isOneShotFinished(
                    skin, data.state, data.stateEnteredElapsed, now
                )
                if (!screenOn || finished) stopCleanly() else scheduleNextIfScreenOn()
            }
        }
    }

    private fun scheduleNextIfScreenOn() {
        if (screenOn) {
            handler.postDelayed(tickRunnable, ANIM_INTERVAL_MS)
        }
    }

    /** 화면 꺼짐: 프레임 틱 중단. 진행 중이면 완료 시점에 깨우도록 inexact 알람 예약. */
    private fun onScreenOff() {
        handler.removeCallbacks(tickRunnable)
        val data = TimerPreferences.get(this).load()
        if (data.state == TimerState.RUNNING) {
            AlarmScheduler.scheduleCompletion(this, data.targetEndElapsed)
        }
    }

    private fun ensureForeground() {
        if (foregroundStarted) return
        val data = TimerPreferences.get(this).load()
        val now = SystemClock.elapsedRealtime()
        val text = if (data.state == TimerState.RUNNING) {
            formatRemaining(data.targetEndElapsed - now)
        } else {
            getString(com.daintyz.timerwidget.R.string.notif_progress_title)
        }
        val notification = TimerNotifications.buildProgressNotification(this, text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                TimerNotifications.NOTIF_ID_PROGRESS,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(TimerNotifications.NOTIF_ID_PROGRESS, notification)
        }
        foregroundStarted = true
    }

    private fun updateForegroundNotification(text: String) {
        val notification = TimerNotifications.buildProgressNotification(this, text)
        androidx.core.app.NotificationManagerCompat.from(this)
            .takeIf { it.areNotificationsEnabled() }
            ?.notify(TimerNotifications.NOTIF_ID_PROGRESS, notification)
    }

    private fun formatRemaining(millis: Long): String {
        val totalSeconds = (millis.coerceAtLeast(0L) + 999) / 1000
        return "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)
    }

    private fun stopCleanly() {
        handler.removeCallbacks(tickRunnable)
        AlarmScheduler.cancel(this)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        foregroundStarted = false
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(tickRunnable)
        runCatching { unregisterReceiver(screenReceiver) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.daintyz.timerwidget.service.START"
        const val ACTION_STOP = "com.daintyz.timerwidget.service.STOP"
        const val ACTION_SCREEN_ON = "com.daintyz.timerwidget.service.SCREEN_ON"
        const val ACTION_SCREEN_OFF = "com.daintyz.timerwidget.service.SCREEN_OFF"
        const val ACTION_RESYNC = "com.daintyz.timerwidget.service.RESYNC"

        // 화면 켜짐 시 애니메이션 틱 간격. 250ms = 4fps (화면 꺼지면 틱 자체가 멈춤 → 배터리 영향 없음).
        // skin.json의 frameDurationMs도 이 값에 맞춰야 프레임이 매 틱 자연스럽게 넘어간다.
        private const val ANIM_INTERVAL_MS = 250L

        /** 서비스 시작/보장 (포그라운드). 이미 떠 있으면 RESYNC로 재평가. */
        fun ensureRunning(context: Context) {
            val intent = Intent(context, TimerForegroundService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun resync(context: Context) {
            val intent = Intent(context, TimerForegroundService::class.java).setAction(ACTION_RESYNC)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, TimerForegroundService::class.java).setAction(ACTION_STOP)
            ContextCompat.startForegroundService(context, intent)
        }

        fun notifyScreen(context: Context, on: Boolean) {
            val intent = Intent(context, TimerForegroundService::class.java)
                .setAction(if (on) ACTION_SCREEN_ON else ACTION_SCREEN_OFF)
            // 서비스가 떠 있을 때만 의미 있음. 떠 있지 않으면 무시되도록 startService 사용.
            runCatching { context.startService(intent) }
        }
    }
}
