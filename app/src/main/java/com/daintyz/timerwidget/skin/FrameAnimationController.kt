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
        val totalMs: Long,
        /** 현재 상태로 진입한 elapsedRealtime 시각. 일회성(IDLE/COMPLETE) 재생의 시작 기준점. 0이면 미상. */
        val stateEnteredElapsed: Long = 0L
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
     * 일회성(루프 없이 마지막 프레임에서 정지) 재생을 쓰는 상태.
     * - COMPLETE(완료) / IDLE(중지) / PAUSED(일시정지): 01→…→마지막 프레임까지 한 번만 재생하고 마지막에서 멈춘다.
     * - RUNNING: 기존대로 순환(루프) 재생.
     */
    fun isOneShot(state: TimerState): Boolean =
        state == TimerState.IDLE || state == TimerState.COMPLETE || state == TimerState.PAUSED

    /**
     * 현재 표시할 프레임 파일명. 항상 [resolveFrames]를 거친다.
     *
     * 프레임 인덱스는 elapsedRealtime 절대 시각으로 계산하므로, 화면이 꺼졌다 켜져도
     * 시간 기준으로 자연스럽게 이어진다 (설계 문서 8장 — 시간 기준 프레임 재계산).
     *
     * - 루프 상태(RUNNING/PAUSED): `(nowElapsed / dur) % size`로 순환.
     * - 일회성 상태(IDLE/COMPLETE): 상태 진입 시각([FrameContext.stateEnteredElapsed]) 기준 경과로
     *   `0 → size-1`까지 한 번만 진행하고 마지막 프레임에서 정지. 진입 시각 미상(0)이면 휴식 상태로 보고 마지막 프레임.
     */
    fun currentFrameFile(skin: Skin, state: TimerState, ctx: FrameContext, nowElapsed: Long): String {
        val frameSet = resolveFrames(skin, state, ctx)
        val frames = frameSet.frames
        if (frames.size == 1) return frames[0]
        val dur = frameSet.frameDurationMs.coerceAtLeast(1L)
        if (isOneShot(state)) {
            val anchor = ctx.stateEnteredElapsed
            if (anchor <= 0L) return frames.last() // 시작 시점 미상 → 마지막 프레임에서 정지(휴식)
            val index = ((nowElapsed - anchor).coerceAtLeast(0L) / dur).toInt()
                .coerceAtMost(frames.size - 1)
            return frames[index]
        }
        val index = ((nowElapsed / dur) % frames.size).toInt()
        return frames[index]
    }

    /**
     * 일회성 상태(IDLE/COMPLETE) 애니메이션이 마지막 프레임에 도달했는지.
     * 서비스가 IDLE 정지 애니메이션을 다 재생한 뒤 틱을 멈출 시점을 판단하는 데 쓴다.
     * 루프 상태에서는 항상 false(끝이 없음).
     */
    fun isOneShotFinished(skin: Skin, state: TimerState, stateEnteredElapsed: Long, nowElapsed: Long): Boolean {
        if (!isOneShot(state)) return false
        if (stateEnteredElapsed <= 0L) return true // 시작 시점 미상 → 이미 휴식(마지막 프레임)으로 간주
        val frameSet = when (state) {
            TimerState.IDLE -> skin.character.stop
            TimerState.COMPLETE -> skin.character.complete
            TimerState.PAUSED -> skin.character.pause ?: skin.character.stop
            else -> return true
        }
        val frames = frameSet.frames
        if (frames.size <= 1) return true
        val dur = frameSet.frameDurationMs.coerceAtLeast(1L)
        return (nowElapsed - stateEnteredElapsed) / dur >= frames.size - 1
    }
}
