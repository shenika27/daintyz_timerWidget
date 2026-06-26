package com.daintyz.timerwidget.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerDataTest {

    @Test
    fun remainingMillis_follows_each_timer_state_rule() {
        val now = 10_000L

        assertEquals(600_000L, data(TimerState.IDLE).remainingMillis(now))
        assertEquals(4_000L, data(TimerState.RUNNING, targetEndElapsed = 14_000L).remainingMillis(now))
        assertEquals(0L, data(TimerState.RUNNING, targetEndElapsed = 9_000L).remainingMillis(now))
        assertEquals(7_000L, data(TimerState.PAUSED, remainingAtPause = 7_000L).remainingMillis(now))
        assertEquals(0L, data(TimerState.COMPLETE).remainingMillis(now))
    }

    @Test
    fun isExpired_is_only_true_for_running_timer_at_or_after_target() {
        val now = 10_000L

        assertTrue(data(TimerState.RUNNING, targetEndElapsed = now).isExpired(now))
        assertFalse(data(TimerState.RUNNING, targetEndElapsed = now + 1L).isExpired(now))
        assertFalse(data(TimerState.PAUSED, targetEndElapsed = now).isExpired(now))
        assertFalse(data(TimerState.COMPLETE, targetEndElapsed = now).isExpired(now))
    }

    private fun data(
        state: TimerState,
        targetEndElapsed: Long = 0L,
        remainingAtPause: Long = 0L,
    ) = TimerData(
        state = state,
        targetEndElapsed = targetEndElapsed,
        remainingMillisAtPause = remainingAtPause,
        totalMillis = 600_000L,
        lastSetSeconds = 600,
        stepSeconds = 60,
        layoutMode = LayoutMode.TOP,
        selectedCharacterSkinId = "cha01",
        selectedTimerSkinId = "cha01",
        purchasedSkinIds = emptySet(),
    )
}
