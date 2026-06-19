package com.daintyz.timerwidget.receiver

/**
 * 재부팅 시 진행 중이던 타이머 상태 복구용 리시버.
 *
 * TODO(1차 구현):
 * - BOOT_COMPLETED 수신 시 SharedPreferences에 저장된 목표 종료 시각 확인
 * - 타이머가 Running/Paused 상태였다면 TimerForegroundService 재시작
 * - AlarmManager(inexact)로도 동일 복구 트리거 보조 (서비스 사망 대비, 설계 문서 4-1)
 */
class BootCompletedReceiver
