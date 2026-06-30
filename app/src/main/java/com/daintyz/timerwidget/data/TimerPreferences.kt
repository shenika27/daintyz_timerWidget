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
        stateEnteredElapsed = prefs.getLong(KEY_STATE_ENTERED_ELAPSED, 0L),
        remainingMillisAtPause = prefs.getLong(KEY_REMAINING_AT_PAUSE, 0L),
        totalMillis = prefs.getLong(KEY_TOTAL_MILLIS, 0L),
        // 초 단위 키가 없는 기존 설치본은 기존 분 단위 값을 초로 변환해 유지한다.
        lastSetSeconds = if (prefs.contains(KEY_LAST_SET_SECONDS)) {
            prefs.getInt(KEY_LAST_SET_SECONDS, DEFAULT_SECONDS)
        } else {
            prefs.getInt(KEY_LAST_SET_MINUTES, DEFAULT_MINUTES) * SECONDS_PER_MINUTE
        },
        // 초 단위 키가 없는 기존 설치본은 기존 분 단위 step을 초로 변환해 유지한다.
        stepSeconds = if (prefs.contains(KEY_STEP_SECONDS)) {
            prefs.getInt(KEY_STEP_SECONDS, DEFAULT_STEP_SECONDS)
        } else {
            prefs.getInt(KEY_STEP_MINUTES, DEFAULT_STEP_MINUTES) * SECONDS_PER_MINUTE
        },
        layoutMode = LayoutMode.fromKey(prefs.getString(KEY_LAYOUT_MODE, null)),
        // 캐릭터/타이머 독립 선택. 구버전(단일 selected_skin_id)에서 올라온 경우 그 값을 양쪽에 복사해 마이그레이션.
        selectedCharacterSkinId = prefs.getString(KEY_SELECTED_CHARACTER_SKIN_ID, null)
            ?: prefs.getString(KEY_SELECTED_SKIN_ID, null) ?: DEFAULT_SKIN_ID,
        selectedTimerSkinId = prefs.getString(KEY_SELECTED_TIMER_SKIN_ID, null)
            ?: prefs.getString(KEY_SELECTED_SKIN_ID, null) ?: DEFAULT_SKIN_ID,
        purchasedSkinIds = prefs.getStringSet(KEY_PURCHASED_SKIN_IDS, emptySet())?.toSet() ?: emptySet(),
        hasLifetimePass = prefs.getBoolean(KEY_LIFETIME_PASS, false),
        hasGiftLifetimePass = prefs.getBoolean(KEY_GIFT_LIFETIME_PASS, false),
        giftUnlockedSkinIds = prefs.getStringSet(KEY_GIFT_UNLOCKED_SKIN_IDS, emptySet())?.toSet() ?: emptySet()
    )

    // ---- 쓰기 ----

    fun save(data: TimerData) {
        prefs.edit().apply {
            putString(KEY_STATE, data.state.name)
            putLong(KEY_TARGET_END_ELAPSED, data.targetEndElapsed)
            putLong(KEY_STATE_ENTERED_ELAPSED, data.stateEnteredElapsed)
            putLong(KEY_REMAINING_AT_PAUSE, data.remainingMillisAtPause)
            putLong(KEY_TOTAL_MILLIS, data.totalMillis)
            putInt(KEY_LAST_SET_SECONDS, data.lastSetSeconds)
            putInt(KEY_STEP_SECONDS, data.stepSeconds)
            putString(KEY_LAYOUT_MODE, data.layoutMode.key)
            putString(KEY_SELECTED_CHARACTER_SKIN_ID, data.selectedCharacterSkinId)
            putString(KEY_SELECTED_TIMER_SKIN_ID, data.selectedTimerSkinId)
            putStringSet(KEY_PURCHASED_SKIN_IDS, data.purchasedSkinIds)
            putBoolean(KEY_LIFETIME_PASS, data.hasLifetimePass)
            putBoolean(KEY_GIFT_LIFETIME_PASS, data.hasGiftLifetimePass)
            putStringSet(KEY_GIFT_UNLOCKED_SKIN_IDS, data.giftUnlockedSkinIds)
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

    // ---- 앱 전용 환경설정 (위젯 상태 동기화와 무관 — TimerData에 넣지 않는다) ----

    /** 완료 시 앱이 알림음을 직접 재생할지. 기본 켜짐. */
    fun isCompleteSoundEnabled(): Boolean = prefs.getBoolean(KEY_COMPLETE_SOUND, true)
    fun setCompleteSoundEnabled(on: Boolean) = prefs.edit().putBoolean(KEY_COMPLETE_SOUND, on).apply()

    /** 완료 시 앱이 진동할지. 기본 켜짐. */
    fun isVibrateEnabled(): Boolean = prefs.getBoolean(KEY_VIBRATE, true)
    fun setVibrateEnabled(on: Boolean) = prefs.edit().putBoolean(KEY_VIBRATE, on).apply()

    /** 앱 UI에 시스템(폰) 글꼴을 쓸지. 기본 꺼짐(=내장 Gmarket/Jua). */
    fun isUseSystemFont(): Boolean = prefs.getBoolean(KEY_USE_SYSTEM_FONT, false)
    fun setUseSystemFont(on: Boolean) = prefs.edit().putBoolean(KEY_USE_SYSTEM_FONT, on).apply()

    /** Google Play 결제 성공 시 구매 스킨을 추가한다(중복은 Set이 흡수). 결제 연동(Phase 4)에서 사용. */
    fun addPurchasedSkinId(skinId: String) {
        update { it.copy(purchasedSkinIds = it.purchasedSkinIds + skinId) }
    }

    /** 기프트코드 해금으로 보유 스킨을 추가한다(중복은 Set이 흡수). Play 구매와 출처를 분리해 동기화 회수 대상에서 제외한다. */
    fun addGiftUnlockedSkinId(skinId: String) {
        update { it.copy(giftUnlockedSkinIds = it.giftUnlockedSkinIds + skinId) }
    }

    /** 기프트코드로 평생이용권을 지급한다. Play 결제 평생이용권과 분리 보관해 구매 동기화 회수 대상에서 제외한다. */
    fun grantGiftLifetimePass(giftPassToken: String? = null) {
        update { it.copy(hasGiftLifetimePass = true) }
        if (!giftPassToken.isNullOrBlank()) {
            prefs.edit().putString(KEY_GIFT_LIFETIME_PASS_TOKEN, giftPassToken).apply()
        }
    }

    fun giftLifetimePassToken(): String? =
        prefs.getString(KEY_GIFT_LIFETIME_PASS_TOKEN, null)?.takeIf { it.isNotBlank() }

    /** [load] → 변형 → [save]를 한 번에. 변형 결과를 그대로 반환한다. */
    inline fun update(transform: (TimerData) -> TimerData): TimerData {
        val updated = transform(load())
        save(updated)
        return updated
    }

    companion object {
        private const val PREFS_NAME = "timer_widget_prefs"

        private const val SECONDS_PER_MINUTE = 60

        const val DEFAULT_MINUTES = 10
        const val DEFAULT_SECONDS = DEFAULT_MINUTES * SECONDS_PER_MINUTE
        const val DEFAULT_STEP_MINUTES = 1
        const val DEFAULT_STEP_SECONDS = DEFAULT_STEP_MINUTES * SECONDS_PER_MINUTE
        const val DEFAULT_SKIN_ID = "cha01"

        private const val KEY_STATE = "state"
        private const val KEY_TARGET_END_ELAPSED = "target_end_elapsed"
        private const val KEY_STATE_ENTERED_ELAPSED = "state_entered_elapsed"
        private const val KEY_REMAINING_AT_PAUSE = "remaining_at_pause"
        private const val KEY_TOTAL_MILLIS = "total_millis"
        private const val KEY_LAST_SET_MINUTES = "last_set_minutes"
        private const val KEY_LAST_SET_SECONDS = "last_set_seconds"
        /** 구버전 분 단위 step 키. 읽기 전용(마이그레이션 폴백)으로만 남겨둔다. */
        private const val KEY_STEP_MINUTES = "step_minutes"
        private const val KEY_STEP_SECONDS = "step_seconds"
        private const val KEY_LAYOUT_MODE = "layout_mode"
        /** 구버전 단일 선택 키. 읽기 전용(마이그레이션 폴백)으로만 남겨둔다. */
        private const val KEY_SELECTED_SKIN_ID = "selected_skin_id"
        private const val KEY_SELECTED_CHARACTER_SKIN_ID = "selected_character_skin_id"
        private const val KEY_SELECTED_TIMER_SKIN_ID = "selected_timer_skin_id"
        private const val KEY_PURCHASED_SKIN_IDS = "purchased_skin_ids"
        private const val KEY_LIFETIME_PASS = "has_lifetime_pass"
        private const val KEY_GIFT_LIFETIME_PASS = "has_gift_lifetime_pass"
        private const val KEY_GIFT_LIFETIME_PASS_TOKEN = "gift_lifetime_pass_token"
        private const val KEY_GIFT_UNLOCKED_SKIN_IDS = "gift_unlocked_skin_ids"
        private const val KEY_FAVORITE_SKIN_IDS = "favorite_skin_ids"
        private const val KEY_COMPLETE_SOUND = "complete_sound_enabled"
        private const val KEY_VIBRATE = "vibrate_enabled"
        private const val KEY_USE_SYSTEM_FONT = "use_system_font"

        @Volatile
        private var instance: TimerPreferences? = null

        fun get(context: Context): TimerPreferences =
            instance ?: synchronized(this) {
                instance ?: TimerPreferences(context).also { instance = it }
            }
    }
}
