package com.daintyz.timerwidget.model

/**
 * 위젯 4상태 (설계 문서 3-1 참고).
 */
enum class TimerState {
    IDLE,
    RUNNING,
    PAUSED,
    COMPLETE;

    companion object {
        fun fromName(name: String?): TimerState =
            entries.firstOrNull { it.name == name } ?: IDLE
    }
}

/**
 * 레이아웃 배치 모드 (설계 문서 2-1 참고).
 * top = 타이머 위 / bottom = 타이머 아래.
 */
enum class LayoutMode(val key: String) {
    TOP("top"),
    BOTTOM("bottom");

    companion object {
        fun fromKey(key: String?): LayoutMode =
            entries.firstOrNull { it.key == key } ?: TOP
    }
}

/**
 * 타이머의 현재 스냅샷. SharedPreferences에서 읽어 복원되며, 앱↔위젯 동기화의 단일 원천이다.
 *
 * @param state 현재 상태 (Idle/Running/Paused/Complete)
 * @param targetEndElapsed RUNNING일 때 SystemClock.elapsedRealtime() 기준 목표 종료 시각(ms). 절전 모드에도 오차 없음.
 * @param remainingMillisAtPause PAUSED일 때 멈춘 남은 시간(ms).
 * @param totalMillis 현재 타이머의 전체 길이(ms). 진행률 계산용.
 * @param lastSetMinutes 완료/리셋 후 복귀 시 유지할 직전 설정 시간(분).
 * @param stepMinutes 위젯 +/- 증감 단위(분). 앱에서 자유롭게 변경 (설계 문서 3-2).
 * @param layoutMode 위젯 배치 모드.
 * @param selectedCharacterSkinId 캐릭터 영역에 적용된 스킨(테마) id.
 * @param selectedTimerSkinId 타이머 영역에 적용된 스킨(테마) id. 캐릭터와 독립적으로 선택된다.
 * @param purchasedSkinIds 구매된 스킨(테마) id 집합. 구매는 테마 단위이므로 해금되면 캐릭터/타이머 둘 다 사용 가능.
 */
data class TimerData(
    val state: TimerState,
    val targetEndElapsed: Long,
    val remainingMillisAtPause: Long,
    val totalMillis: Long,
    val lastSetMinutes: Int,
    val stepMinutes: Int,
    val layoutMode: LayoutMode,
    val selectedCharacterSkinId: String,
    val selectedTimerSkinId: String,
    val purchasedSkinIds: Set<String>
) {
    /**
     * 지정한 기준 시각에서의 남은 시간(ms). 상태에 무관하게 일관된 값을 돌려준다.
     */
    fun remainingMillis(nowElapsed: Long): Long = when (state) {
        TimerState.RUNNING -> (targetEndElapsed - nowElapsed).coerceAtLeast(0L)
        TimerState.PAUSED -> remainingMillisAtPause.coerceAtLeast(0L)
        TimerState.IDLE -> lastSetMinutes * 60_000L
        TimerState.COMPLETE -> 0L
    }

    /** RUNNING 상태에서 남은 시간이 0에 도달했는지. */
    fun isExpired(nowElapsed: Long): Boolean =
        state == TimerState.RUNNING && nowElapsed >= targetEndElapsed
}
