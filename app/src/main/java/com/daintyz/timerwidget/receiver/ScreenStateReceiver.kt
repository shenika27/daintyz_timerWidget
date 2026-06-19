package com.daintyz.timerwidget.receiver

/**
 * ACTION_SCREEN_ON / ACTION_SCREEN_OFF 감지용 동적 등록 BroadcastReceiver.
 *
 * TODO(1차 구현):
 * - TimerForegroundService 내부에서 동적 등록/해제 (매니페스트 정적 등록 불가 - 시스템 제약)
 * - 화면 ON: 남은 시간 기준 프레임 재계산 후 1초 틱 애니메이션 재개
 * - 화면 OFF: 프레임 갱신 중단, 시간 추적만 계속 (설계 문서 4-2)
 */
class ScreenStateReceiver
