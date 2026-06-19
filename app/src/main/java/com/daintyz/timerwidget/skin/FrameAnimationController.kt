package com.daintyz.timerwidget.skin

/**
 * 상태(idle/running/complete)별 프레임 진행을 계산하는 컨트롤러.
 *
 * TODO(1차 구현):
 * - fun currentFrame(skin: Skin, state: TimerState, elapsedMs: Long): String (프레임 파일명)
 * - 화면 켜짐 시 경과 시간 기준으로 프레임 인덱스 재계산 (설계 문서 8장 참고)
 */
class FrameAnimationController
