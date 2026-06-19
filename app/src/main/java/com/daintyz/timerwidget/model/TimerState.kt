package com.daintyz.timerwidget.model

/**
 * 위젯 4상태 (설계 문서 3-1 참고).
 *
 * TODO(1차 구현):
 * enum class TimerState { IDLE, RUNNING, PAUSED, COMPLETE }
 *
 * data class TimerData(
 *     val state: TimerState,
 *     val targetEndElapsedRealtime: Long?, // SystemClock.elapsedRealtime() 기준 목표 종료 시각
 *     val remainingMillisAtPause: Long?,   // 일시정지 시점 남은 시간
 *     val lastSetMinutes: Int,             // 완료 후 복귀 시 유지할 직전 설정 시간
 *     val currentFrameIndex: Int,
 *     val layoutMode: String,              // "top" | "bottom"
 *     val selectedSkinId: String
 * )
 */
