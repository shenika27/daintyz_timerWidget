package com.daintyz.timerwidget.widget

/**
 * RemoteViews 생성/갱신 로직을 모아두는 헬퍼.
 *
 * TODO(1차 구현):
 * - fun buildRemoteViews(state, layoutMode, currentFrame, skinId): RemoteViews
 * - top/bottom 레이아웃 모두 동일 id를 쓰므로 분기는 initialLayout 선택 시점에만 발생
 * - 화면 ON/OFF, 1초 틱에 따라 호출되는 단일 진입점 역할
 */
class WidgetUpdater
