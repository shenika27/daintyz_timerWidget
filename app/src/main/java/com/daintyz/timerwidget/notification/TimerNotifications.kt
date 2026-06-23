package com.daintyz.timerwidget.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.daintyz.timerwidget.R
import com.daintyz.timerwidget.ui.MainActivity

/**
 * 알림 채널 및 진행/완료 알림 생성 (설계 문서 3-3, 5장).
 */
object TimerNotifications {

    const val CHANNEL_PROGRESS = "timer_progress"
    const val CHANNEL_COMPLETE = "timer_complete"

    const val NOTIF_ID_PROGRESS = 1001
    const val NOTIF_ID_COMPLETE = 1002

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService<NotificationManager>() ?: return

        val progress = NotificationChannel(
            CHANNEL_PROGRESS,
            context.getString(R.string.notif_channel_progress_name),
            NotificationManager.IMPORTANCE_LOW // 진행 알림은 조용하게
        ).apply { description = context.getString(R.string.notif_channel_progress_desc) }

        val complete = NotificationChannel(
            CHANNEL_COMPLETE,
            context.getString(R.string.notif_channel_complete_name),
            NotificationManager.IMPORTANCE_HIGH // 완료는 눈에 띄게
        ).apply { description = context.getString(R.string.notif_channel_complete_desc) }

        manager.createNotificationChannel(progress)
        manager.createNotificationChannel(complete)
    }

    /** 포그라운드 서비스용 상시 진행 알림. */
    fun buildProgressNotification(context: Context, contentText: String): Notification {
        ensureChannels(context)
        return NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(context.getString(R.string.notif_progress_title))
            .setContentText(contentText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent(context))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun notifyComplete(context: Context) {
        ensureChannels(context)
        val manager = context.getSystemService<NotificationManager>() ?: return
        val notification = NotificationCompat.Builder(context, CHANNEL_COMPLETE)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(context.getString(R.string.notif_complete_title))
            .setContentText(context.getString(R.string.notif_complete_text))
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        manager.notify(NOTIF_ID_COMPLETE, notification)
    }

    fun cancelComplete(context: Context) {
        context.getSystemService<NotificationManager>()?.cancel(NOTIF_ID_COMPLETE)
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
