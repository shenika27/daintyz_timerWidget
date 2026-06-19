package com.daintyz.timerwidget.data

/**
 * SharedPreferences 기반 영속화 (설계 문서 4-3, 8장 참고).
 *
 * 저장 키 (TODO 1차 구현 시 확정):
 * - KEY_STATE (Idle/Running/Paused/Complete)
 * - KEY_TARGET_END_ELAPSED_REALTIME
 * - KEY_REMAINING_MILLIS_AT_PAUSE
 * - KEY_LAST_SET_MINUTES
 * - KEY_CURRENT_FRAME_INDEX
 * - KEY_LAYOUT_MODE ("top" | "bottom")
 * - KEY_SELECTED_SKIN_ID
 * - KEY_PURCHASED_SKIN_IDS (결제 대비 자리, 1차 버전에서는 항상 비어있음 - 설계 문서 6-4)
 */
class TimerPreferences
