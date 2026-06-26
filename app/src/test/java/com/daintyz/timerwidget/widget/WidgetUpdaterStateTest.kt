package com.daintyz.timerwidget.widget

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.daintyz.timerwidget.R
import com.daintyz.timerwidget.model.LayoutMode
import com.daintyz.timerwidget.model.TimerData
import com.daintyz.timerwidget.model.TimerState
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WidgetUpdaterStateTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val now = 100_000L

    @Test
    fun idle_shows_adjust_buttons_and_hides_running_buttons() {
        val root = render(TimerState.IDLE)

        assertVisible(root, R.id.btn_plus, R.id.btn_minus)
        assertGone(root, R.id.btn_start_pause, R.id.btn_stop_reset)
    }

    @Test
    fun running_shows_pause_and_stop_buttons() {
        val root = render(TimerState.RUNNING)

        assertGone(root, R.id.btn_plus, R.id.btn_minus)
        assertVisible(root, R.id.btn_start_pause, R.id.btn_stop_reset)
    }

    @Test
    fun paused_shows_resume_and_stop_buttons() {
        val root = render(TimerState.PAUSED)

        assertGone(root, R.id.btn_plus, R.id.btn_minus)
        assertVisible(root, R.id.btn_start_pause, R.id.btn_stop_reset)
    }

    @Test
    fun complete_shows_adjust_buttons_and_hides_running_buttons() {
        val root = render(TimerState.COMPLETE)

        assertVisible(root, R.id.btn_plus, R.id.btn_minus)
        assertGone(root, R.id.btn_start_pause, R.id.btn_stop_reset)
    }

    private fun render(state: TimerState): View {
        val data = TimerData(
            state = state,
            targetEndElapsed = now + 10_000L,
            stateEnteredElapsed = now - 1_000L,
            remainingMillisAtPause = 6_000L,
            totalMillis = 10_000L,
            lastSetSeconds = 10,
            stepSeconds = 60,
            layoutMode = LayoutMode.TOP,
            selectedCharacterSkinId = "cha01",
            selectedTimerSkinId = "cha01",
            purchasedSkinIds = emptySet(),
        )
        val views = WidgetUpdater.buildRemoteViews(context, data, now)
        return views.apply(context, FrameLayout(context))
    }

    private fun assertVisible(root: View, vararg ids: Int) {
        ids.forEach { id -> assertEquals(View.VISIBLE, root.findViewById<View>(id).visibility) }
    }

    private fun assertGone(root: View, vararg ids: Int) {
        ids.forEach { id -> assertEquals(View.GONE, root.findViewById<View>(id).visibility) }
    }
}
