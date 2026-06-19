package com.daintyz.timerwidget.receiver

/**
 * 위젯 버튼(+/-/▶/||/ㅁ)에서 발송하는 PendingIntent(getBroadcast) 액션 수신.
 *
 * TODO(1차 구현):
 * - CharacterTimerWidgetProvider.onReceive에서 위임받거나 별도 등록
 * - 액션별 상태 전환: Idle -> Running -> (Paused <-> Running) -> Complete -> Idle
 * - 상태 변경 후 TimerForegroundService 시작/정지 + WidgetUpdater로 즉시 갱신
 */
class TimerActionReceiver
