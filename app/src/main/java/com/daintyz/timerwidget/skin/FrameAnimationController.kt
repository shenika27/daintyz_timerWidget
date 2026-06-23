package com.daintyz.timerwidget.skin

import com.daintyz.timerwidget.model.FrameSet
import com.daintyz.timerwidget.model.Skin
import com.daintyz.timerwidget.model.TimerState

/**
 * 상태별 프레임 진행을 계산하는 컨트롤러 (설계 문서 4-1, 8장).
 *
 * 핵심: **프레임 선택 일원화**. 위젯 갱신은 항상 [resolveFrames]를 거쳐 표시할 프레임 배열을 얻는다.
 * 컨디션 분기(지친/가뿐 등) 로직은 오직 [resolveFrames] 내부에만 추가하면 되고, 호출부는 바뀌지 않는다.
 */
object FrameAnimationController {

    /**
     * 프레임 선택에 쓰일 컨텍스트 (남은 시간/경과/진행률).
     * 1차 버전에서는 분기에 사용하지 않지만, 컨디션 분기를 [resolveFrames] 내부에만 추가하면 되도록 미리 받아둔다.
     */
    data class FrameContext(
        val remainingMs: Long,
        val elapsedMs: Long,
        val totalMs: Long
    ) {
        /** 0.0(시작) ~ 1.0(완료). totalMs가 0이면 0. */
        val progress: Float
            get() = if (totalMs <= 0L) 0f else (elapsedMs.toFloat() / totalMs).coerceIn(0f, 1f)
    }

    /**
     * 표시할 프레임셋을 반환하는 **단일 진입점**.
     *
     * 1차 버전: running일 때 무조건 running.default 반환.
     * 향후 컨디션 분기: running.conditional 중 [ctx] 조건을 만족하는 첫 프레임셋을 고르고,
     * 없으면 running.default로 폴백 (설계 문서 6-2 필수 폴백 규칙). → 이 함수 내부에만 추가한다.
     */
    fun resolveFrames(skin: Skin, state: TimerState, ctx: FrameContext): FrameSet = when (state) {
        TimerState.IDLE -> skin.character.stop
        TimerState.PAUSED -> skin.character.pause ?: skin.character.stop  // pause 미지정 시 stop 폴백
        TimerState.COMPLETE -> skin.character.complete
        TimerState.RUNNING -> skin.character.running.default  // 1차: 항상 default. (분기 추가 시 여기서만 처리)
    }

    /**
     * 현재 표시할 프레임 파일명. 항상 [resolveFrames]를 거친다.
     *
     * 프레임 인덱스는 elapsedRealtime 절대 시각으로 계산하므로, 화면이 꺼졌다 켜져도
     * 시간 기준으로 자연스럽게 이어진다 (설계 문서 8장 — 시간 기준 프레임 재계산).
     */
    fun currentFrameFile(skin: Skin, state: TimerState, ctx: FrameContext, nowElapsed: Long): String {
        val frameSet = resolveFrames(skin, state, ctx)
        val frames = frameSet.frames
        if (frames.size == 1) return frames[0]
        val dur = frameSet.frameDurationMs.coerceAtLeast(1L)
        val index = ((nowElapsed / dur) % frames.size).toInt()
        return frames[index]
    }
}
