package com.daintyz.timerwidget.widget

/**
 * 위젯 UI 렌더링 + 버튼 액션 수신(onReceive) 담당.
 *
 * TODO(1차 구현):
 * - AppWidgetProvider 상속
 * - SharedPreferences에서 layoutMode("top"|"bottom") 읽어 RemoteViews 레이아웃 선택
 * - 4상태(Idle/Running/Paused/Complete)에 따른 RemoteViews 갱신
 * - PendingIntent(getBroadcast) 액션 정의: ACTION_PLUS, ACTION_MINUS, ACTION_START_PAUSE,
 *   ACTION_STOP_RESET, ACTION_TAP_COMPLETE
 * - onUpdate / onReceive 에서 TimerForegroundService와 상태 동기화
 *
 * 설계 문서 4-3. 권장 컴포넌트 구성 참고.
 */
class CharacterTimerWidgetProvider
