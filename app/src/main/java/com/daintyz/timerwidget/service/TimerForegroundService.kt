package com.daintyz.timerwidget.service

/**
 * 타이머 진행/일시정지 중 시간 추적 + 1초 틱 프레임 갱신을 담당하는 포그라운드 서비스.
 *
 * TODO(1차 구현):
 * - Service 상속, 상시 알림(Notification) 노출
 * - Handler(postDelayed) 또는 코루틴 기반 1초 틱
 *   ⚠️ AlarmManager.setExactAndAllowWhileIdle를 프레임 갱신에 사용하지 않는다 (설계 문서 4-1)
 * - SystemClock.elapsedRealtime() 기준 목표 종료 시각 저장/계산 (절전 모드 오차 방지)
 * - ScreenStateReceiver와 연동하여 화면 꺼짐 시 프레임 갱신 중단, 남은 시간만 추적
 * - 타이머 완료 시 Notification 발송 + 위젯에 승리 포즈 반영
 */
class TimerForegroundService
