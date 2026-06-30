package com.daintyz.timerwidget.notification

import android.content.Context
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.getSystemService
import com.daintyz.timerwidget.data.TimerPreferences

/**
 * 타이머 완료 시의 소리·진동 피드백.
 *
 * Android 8+에선 알림음·진동이 알림 채널에 묶여 생성 후 앱이 끌 수 없으므로,
 * 완료 알림 채널은 무음·무진동으로 두고(이 신호는 [TimerNotifications] 채널 설정 참고)
 * 여기서 앱이 직접 재생한다 — 설정의 인앱 토글로 실제 on/off가 가능해진다.
 */
object CompletionFeedback {

    private const val REPEAT_INTERVAL_MS = 1_500L

    private val handler = Handler(Looper.getMainLooper())
    private var repeatRunnable: Runnable? = null
    private var stopRunnable: Runnable? = null
    private var currentRingtone: Ringtone? = null

    /** 설정 토글을 읽어 완료음·진동을 한 번 또는 설정 시간 동안 반복 재생한다. */
    fun fire(context: Context) {
        val appContext = context.applicationContext
        handler.post { fireOnMain(appContext) }
    }

    /** 완료 확인, 알림 제거, 설정 시간 만료 시 진행 중인 반복 피드백을 멈춘다. */
    fun stop() {
        handler.post { stopOnMain() }
    }

    private fun fireOnMain(context: Context) {
        stopOnMain()

        val prefs = TimerPreferences.get(context)
        val soundEnabled = prefs.isCompleteSoundEnabled()
        val vibrateEnabled = prefs.isVibrateEnabled()
        if (!soundEnabled && !vibrateEnabled) return

        if (!prefs.isCompleteRepeatEnabled()) {
            playOnce(context, soundEnabled, vibrateEnabled)
            return
        }

        val repeatMillis = prefs.completeRepeatSeconds() * 1_000L
        val endAt = SystemClock.elapsedRealtime() + repeatMillis
        val runnable = object : Runnable {
            override fun run() {
                if (SystemClock.elapsedRealtime() >= endAt) {
                    stopOnMain()
                    return
                }
                playOnce(context, soundEnabled, vibrateEnabled)
                handler.postDelayed(this, REPEAT_INTERVAL_MS)
            }
        }
        repeatRunnable = runnable
        stopRunnable = Runnable { stopOnMain() }.also {
            handler.postDelayed(it, repeatMillis)
        }
        runnable.run()
    }

    private fun stopOnMain() {
        repeatRunnable?.let { handler.removeCallbacks(it) }
        stopRunnable?.let { handler.removeCallbacks(it) }
        repeatRunnable = null
        stopRunnable = null
        runCatching { currentRingtone?.stop() }
        currentRingtone = null
    }

    private fun playOnce(context: Context, soundEnabled: Boolean, vibrateEnabled: Boolean) {
        if (soundEnabled) playSound(context)
        if (vibrateEnabled) vibrate(context)
    }

    /** 폰에 지정된 기본 알림음을 한 번 재생. 별도 음원 에셋 없이 시스템 알림음을 사용한다. */
    private fun playSound(context: Context) {
        runCatching {
            currentRingtone?.stop()
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            currentRingtone = RingtoneManager.getRingtone(context.applicationContext, uri)?.also { it.play() }
        }
    }

    private fun vibrate(context: Context) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService<VibratorManager>()?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService<Vibrator>()
        } ?: return
        if (!vibrator.hasVibrator()) return
        vibrator.vibrate(VibrationEffect.createOneShot(400L, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
