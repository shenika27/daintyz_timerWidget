package com.daintyz.timerwidget.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.daintyz.timerwidget.R
import com.daintyz.timerwidget.controller.TimerController
import com.daintyz.timerwidget.data.TimerPreferences
import com.daintyz.timerwidget.model.LayoutMode
import com.daintyz.timerwidget.model.TimerState

/**
 * 앱 메인 화면 (설계 문서 3-2, 2-1).
 *
 * - 증감 단위 자유 설정 (숫자패드 직접 입력)
 * - 현재 타이머 상태/남은 시간을 위젯과 동기화하여 표시
 * - 타이머 위/아래 배치 전환 토글 (변경 시 위젯 즉시 갱신)
 * - 스킨 선택 화면 진입점
 * - 첫 실행 시 알림 권한(POST_NOTIFICATIONS) 요청 (Android 13+)
 */
class MainActivity : AppCompatActivity() {

    private lateinit var tvState: TextView
    private lateinit var tvRemaining: TextView
    private lateinit var etStep: EditText
    private lateinit var rgLayoutMode: RadioGroup

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* 결과 무시: 거부해도 앱은 동작 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvState = findViewById(R.id.tv_state)
        tvRemaining = findViewById(R.id.tv_remaining)
        etStep = findViewById(R.id.et_step)
        rgLayoutMode = findViewById(R.id.rg_layout_mode)

        findViewById<Button>(R.id.btn_save_step).setOnClickListener { saveStep() }
        findViewById<Button>(R.id.btn_open_skins).setOnClickListener {
            startActivity(Intent(this, SkinSelectActivity::class.java))
        }
        findViewById<Button>(R.id.btn_open_store).setOnClickListener {
            startActivity(Intent(this, SkinStoreActivity::class.java))
        }
        rgLayoutMode.setOnCheckedChangeListener { _, checkedId ->
            val mode = if (checkedId == R.id.rb_bottom) LayoutMode.BOTTOM else LayoutMode.TOP
            TimerController.setLayoutMode(this, mode)
        }

        requestNotificationPermissionIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        syncFromState()
    }

    private fun syncFromState() {
        val data = TimerPreferences.get(this).load()
        tvState.text = stateLabel(data.state)
        tvRemaining.text = formatMillis(data.remainingMillis(SystemClock.elapsedRealtime()))

        if (etStep.text.isNullOrBlank()) {
            etStep.setText(data.stepMinutes.toString())
        }
        // 리스너 재진입 방지를 위해 일시적으로 해제 후 설정
        rgLayoutMode.setOnCheckedChangeListener(null)
        rgLayoutMode.check(if (data.layoutMode == LayoutMode.BOTTOM) R.id.rb_bottom else R.id.rb_top)
        rgLayoutMode.setOnCheckedChangeListener { _, checkedId ->
            val mode = if (checkedId == R.id.rb_bottom) LayoutMode.BOTTOM else LayoutMode.TOP
            TimerController.setLayoutMode(this, mode)
        }
    }

    private fun saveStep() {
        val step = etStep.text.toString().toIntOrNull()
        if (step == null || step < 1) {
            Toast.makeText(this, "1 이상의 숫자를 입력하세요", Toast.LENGTH_SHORT).show()
            return
        }
        TimerController.setStepMinutes(this, step)
        Toast.makeText(this, "증감 단위 ${step}분으로 저장됨", Toast.LENGTH_SHORT).show()
    }

    private fun stateLabel(state: TimerState): String = when (state) {
        TimerState.IDLE -> "정지"
        TimerState.RUNNING -> "진행 중"
        TimerState.PAUSED -> "일시정지"
        TimerState.COMPLETE -> "완료"
    }

    private fun formatMillis(millis: Long): String {
        val totalSeconds = (millis + 999) / 1000
        return "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
