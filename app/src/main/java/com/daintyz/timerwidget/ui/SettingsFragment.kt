package com.daintyz.timerwidget.ui

import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.daintyz.timerwidget.R
import com.daintyz.timerwidget.controller.TimerController
import com.daintyz.timerwidget.data.TimerPreferences
import com.daintyz.timerwidget.model.LayoutMode
import com.daintyz.timerwidget.model.TimerState

/**
 * 설정 탭.
 *
 * - 증감 단위 자유 설정 (숫자패드 직접 입력)
 * - 현재 타이머 상태/남은 시간을 위젯과 동기화하여 표시
 * - 타이머 위/아래 배치 전환 토글 (변경 시 위젯 즉시 갱신)
 * (기존 MainActivity 설정부를 프래그먼트로 이식 — 재디자인은 추후)
 */
class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private lateinit var tvState: TextView
    private lateinit var tvRemaining: TextView
    private lateinit var etStep: EditText
    private lateinit var rgLayoutMode: RadioGroup

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        tvState = view.findViewById(R.id.tv_state)
        tvRemaining = view.findViewById(R.id.tv_remaining)
        etStep = view.findViewById(R.id.et_step)
        rgLayoutMode = view.findViewById(R.id.rg_layout_mode)

        view.findViewById<Button>(R.id.btn_save_step).setOnClickListener { saveStep() }
        rgLayoutMode.setOnCheckedChangeListener { _, checkedId ->
            val mode = if (checkedId == R.id.rb_bottom) LayoutMode.BOTTOM else LayoutMode.TOP
            TimerController.setLayoutMode(requireContext(), mode)
        }
    }

    override fun onResume() {
        super.onResume()
        syncFromState()
    }

    private fun syncFromState() {
        val data = TimerPreferences.get(requireContext()).load()
        tvState.text = stateLabel(data.state)
        tvRemaining.text = formatMillis(data.remainingMillis(SystemClock.elapsedRealtime()))

        if (etStep.text.isNullOrBlank()) {
            etStep.setText(data.stepMinutes.toString())
        }
        // 리스너 재진입 방지를 위해 일시적으로 해제 후 설정.
        rgLayoutMode.setOnCheckedChangeListener(null)
        rgLayoutMode.check(if (data.layoutMode == LayoutMode.BOTTOM) R.id.rb_bottom else R.id.rb_top)
        rgLayoutMode.setOnCheckedChangeListener { _, checkedId ->
            val mode = if (checkedId == R.id.rb_bottom) LayoutMode.BOTTOM else LayoutMode.TOP
            TimerController.setLayoutMode(requireContext(), mode)
        }
    }

    private fun saveStep() {
        val step = etStep.text.toString().toIntOrNull()
        if (step == null || step < 1) {
            Toast.makeText(requireContext(), "1 이상의 숫자를 입력하세요", Toast.LENGTH_SHORT).show()
            return
        }
        TimerController.setStepMinutes(requireContext(), step)
        Toast.makeText(requireContext(), "증감 단위 ${step}분으로 저장됨", Toast.LENGTH_SHORT).show()
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
}
