package com.daintyz.timerwidget.data

import android.content.Context
import com.daintyz.timerwidget.model.LayoutMode
import com.daintyz.timerwidget.model.TimerData
import com.daintyz.timerwidget.model.TimerState

/**
 * SharedPreferences 기반 영속화 (설계 문서 4-3, 8장 참고).
 * 앱↔위젯↔서비스가 공유하는 타이머 상태의 단일 원천(single source of truth).
 */
class TimerPreferences private constructor(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ---- 읽기 ----

    fun load(): TimerData = TimerData(
        state = TimerState.fromName(prefs.getString(KEY_STATE, null)),
        targetEndElapsed = prefs.getLong(KEY_TARGET_END_ELAPSED, 0L),
        remainingMillisAtPause = prefs.getLong(KEY_REMAINING_AT_PAUSE, 0L),
        totalMillis = prefs.getLong(KEY_TOTAL_MILLIS, 0L),
        lastSetMinutes = prefs.getInt(KEY_LAST_SET_MINUTES, DEFAULT_MINUTES),
        stepMinutes = prefs.getInt(KEY_STEP_MINUTES, DEFAULT_STEP_MINUTES),
        layoutMode = LayoutMode.fromKey(prefs.getString(KEY_LAYOUT_MODE, null)),
        // 캐릭터/타이머 독립 선택. 구버전(단일 selected_skin_id)에서 올라온 경우 그 값을 양쪽에 복사해 마이그레이션.
        selectedCharacterSkinId = prefs.getString(KEY_SELECTED_CHARACTER_SKIN_ID, null)
            ?: prefs.getString(KEY_SELECTED_SKIN_ID, null) ?: DEFAULT_SKIN_ID,
        selectedTimerSkinId = prefs.getString(KEY_SELECTED_TIMER_SKIN_ID, null)
            ?: prefs.getString(KEY_SELECTED_SKIN_ID, null) ?: DEFAULT_SKIN_ID,
        purchasedSkinIds = prefs.getStringSet(KEY_PURCHASED_SKIN_IDS, emptySet())?.toSet() ?: emptySet(),
        hasLifetimePass = prefs.getBoolean(KEY_LIFETIME_PASS, false)
    )

    // ---- 쓰기 ----

    fun save(data: TimerData) {
        prefs.edit().apply {
            putString(KEY_STATE, data.state.name)
            putLong(KEY_TARGET_END_ELAPSED, data.targetEndElapsed)
            putLong(KEY_REMAINING_AT_PAUSE, data.remainingMillisAtPause)
            putLong(KEY_TOTAL_MILLIS, data.totalMillis)
            putInt(KEY_LAST_SET_MINUTES, data.lastSetMinutes)
            putInt(KEY_STEP_MINUTES, data.stepMinutes)
            putString(KEY_LAYOUT_MODE, data.layoutMode.key)
            putString(KEY_SELECTED_CHARACTER_SKIN_ID, data.selectedCharacterSkinId)
            putString(KEY_SELECTED_TIMER_SKIN_ID, data.selectedTimerSkinId)
            putStringSet(KEY_PURCHASED_SKIN_IDS, data.purchasedSkinIds)
            putBoolean(KEY_LIFETIME_PASS, data.hasLifetimePass)
        }.apply()
    }

    // ---- 즐겨찾기 (앱 전용 UI 상태 — 위젯 동기화와 무관하므로 TimerData에 넣지 않는다) ----

    fun loadFavoriteSkinIds(): Set<String> =
        prefs.getStringSet(KEY_FAVORITE_SKIN_IDS, emptySet())?.toSet() ?: emptySet()

    /** 즐겨찾기 토글. 변경 후의 보유 여부(true=즐겨찾기됨)를 반환한다. */
    fun setFavorite(skinId: String, favorite: Boolean) {
        val next = loadFavoriteSkinIds().toMutableSet()
        if (favorite) next.add(skinId) else next.remove(skinId)
        prefs.edit().putStringSet(KEY_FAVORITE_SKIN_IDS, next).apply()
    }

    /** [load] → 변형 → [save]를 한 번에. 변형 결과를 그대로 반환한다. */
    inline fun update(transform: (TimerData) -> TimerData): TimerData {
        val updated = transform(load())
        save(updated)
        return updated
    }

    companion object {
        private const val PREFS_NAME = "timer_widget_prefs"

        const val DEFAULT_MINUTES = 10
        const val DEFAULT_STEP_MINUTES = 1
        const val DEFAULT_SKIN_ID = "potato"

        private const val KEY_STATE = "state"
        private const val KEY_TARGET_END_ELAPSED = "target_end_elapsed"
        private const val KEY_REMAINING_AT_PAUSE = "remaining_at_pause"
        private const val KEY_TOTAL_MILLIS = "total_millis"
        private const val KEY_LAST_SET_MINUTES = "last_set_minutes"
        private const val KEY_STEP_MINUTES = "step_minutes"
        private const val KEY_LAYOUT_MODE = "layout_mode"
        /** 구버전 단일 선택 키. 읽기 전용(마이그레이션 폴백)으로만 남겨둔다. */
        private const val KEY_SELECTED_SKIN_ID = "selected_skin_id"
        private const val KEY_SELECTED_CHARACTER_SKIN_ID = "selected_character_skin_id"
        private const val KEY_SELECTED_TIMER_SKIN_ID = "selected_timer_skin_id"
        private const val KEY_PURCHASED_SKIN_IDS = "purchased_skin_ids"
        private const val KEY_LIFETIME_PASS = "has_lifetime_pass"
        private const val KEY_FAVORITE_SKIN_IDS = "favorite_skin_ids"

        @Volatile
        private var instance: TimerPreferences? = null

        fun get(context: Context): TimerPreferences =
            instance ?: synchronized(this) {
                instance ?: TimerPreferences(context).also { instance = it }
            }
    }
}
