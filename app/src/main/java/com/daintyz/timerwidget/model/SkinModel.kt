package com.daintyz.timerwidget.model

/**
 * skin.json 스키마에 대응하는 데이터 클래스 (설계 문서 6-2 참고).
 *
 * TODO(1차 구현):
 * data class Skin(
 *     val skinId: String,
 *     val name: String,
 *     val isFree: Boolean,
 *     val states: SkinStates
 * )
 *
 * data class SkinStates(
 *     val idle: FrameSet,
 *     val running: RunningState,   // default 서브키로 한 단계 감싸 향후 컨디션 분기 대비
 *     val complete: FrameSet
 * )
 *
 * data class RunningState(
 *     val default: FrameSet
 *     // 1차 버전에서는 default만 사용. 향후 tired/fresh 등 조건부 키 추가 예정.
 * )
 *
 * data class FrameSet(
 *     val frames: List<String>,
 *     val frameDurationMs: Long
 * )
 */
