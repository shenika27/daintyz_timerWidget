package com.daintyz.timerwidget.ui

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import com.daintyz.timerwidget.widget.WidgetUpdater

/**
 * 위젯 재구성(설정) 진입점.
 *
 * 위젯을 꾹 누르면 런처가 띄우는 관리 화면의 "설정/재구성" 버튼이 이 액티비티를 연다
 * (widget_info의 `reconfigurable`). 위젯 추가 시점엔 뜨지 않는다(`configuration_optional`).
 *
 * 별도 설정 UI를 두지 않고 앱의 설정 탭으로 보낸다.
 */
class WidgetConfigureActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        // 재구성/배치가 성공 처리되도록 결과를 먼저 OK로 돌려준다(없으면 위젯이 사라질 수 있음).
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            WidgetUpdater.updateAllWidgets(this)
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        }

        // 앱 설정 탭으로 이동.
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_NAV, MainActivity.NAV_SETTINGS)
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        )
        finish()
    }
}
