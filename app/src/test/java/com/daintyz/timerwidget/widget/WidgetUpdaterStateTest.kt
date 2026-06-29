package com.daintyz.timerwidget.widget

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.daintyz.timerwidget.R
import com.daintyz.timerwidget.model.CharacterStates
import com.daintyz.timerwidget.model.FrameSet
import com.daintyz.timerwidget.model.LayoutMode
import com.daintyz.timerwidget.model.RunningState
import com.daintyz.timerwidget.model.Skin
import com.daintyz.timerwidget.model.TimerData
import com.daintyz.timerwidget.model.TimerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun refunded_local_paid_skin_falls_back_to_default_skin() {
        val data = timerData(
            selectedCharacterSkinId = "paid",
            selectedTimerSkinId = "paid",
            purchasedSkinIds = emptySet(),
        )
        val selected = WidgetUpdater.resolveRenderableSkin(
            selectedSkinId = "paid",
            skins = listOf(skin("paid", isFree = false), skin("cha01", isFree = true)),
            bundledFallbackSkins = listOf(skin("cha01", isFree = true, bundled = true)),
            data = data,
        )

        assertEquals("cha01", selected?.skinId)
    }

    @Test
    fun purchased_local_paid_skin_remains_renderable() {
        val data = timerData(
            selectedCharacterSkinId = "paid",
            selectedTimerSkinId = "paid",
            purchasedSkinIds = setOf("paid"),
        )
        val selected = WidgetUpdater.resolveRenderableSkin(
            selectedSkinId = "paid",
            skins = listOf(skin("paid", isFree = false), skin("cha01", isFree = true)),
            bundledFallbackSkins = listOf(skin("cha01", isFree = true, bundled = true)),
            data = data,
        )

        assertEquals("paid", selected?.skinId)
    }

    @Test
    fun fallback_uses_first_bundled_free_when_default_missing() {
        val selected = WidgetUpdater.resolveRenderableSkin(
            selectedSkinId = "paid",
            skins = listOf(skin("paid", isFree = false)),
            bundledFallbackSkins = listOf(skin("asset01", isFree = true, bundled = true)),
            data = timerData(selectedCharacterSkinId = "paid", selectedTimerSkinId = "paid"),
        )

        assertEquals("asset01", selected?.skinId)
    }

    @Test
    fun no_bundled_free_fallback_returns_null_instead_of_paid_skin() {
        val selected = WidgetUpdater.resolveRenderableSkin(
            selectedSkinId = "paid",
            skins = listOf(skin("paid", isFree = false)),
            bundledFallbackSkins = listOf(skin("asset_paid", isFree = false, bundled = true)),
            data = timerData(selectedCharacterSkinId = "paid", selectedTimerSkinId = "paid"),
        )

        assertNull(selected)
    }

    private fun render(state: TimerState): View {
        val data = timerData(state = state)
        val views = WidgetUpdater.buildRemoteViews(context, data, now)
        return views.apply(context, FrameLayout(context))
    }

    private fun timerData(
        state: TimerState = TimerState.IDLE,
        selectedCharacterSkinId: String = "cha01",
        selectedTimerSkinId: String = "cha01",
        purchasedSkinIds: Set<String> = emptySet(),
        hasLifetimePass: Boolean = false,
        giftUnlockedSkinIds: Set<String> = emptySet(),
    ) = TimerData(
            state = state,
            targetEndElapsed = now + 10_000L,
            stateEnteredElapsed = now - 1_000L,
            remainingMillisAtPause = 6_000L,
            totalMillis = 10_000L,
            lastSetSeconds = 10,
            stepSeconds = 60,
            layoutMode = LayoutMode.TOP,
            selectedCharacterSkinId = selectedCharacterSkinId,
            selectedTimerSkinId = selectedTimerSkinId,
            purchasedSkinIds = purchasedSkinIds,
            hasLifetimePass = hasLifetimePass,
            giftUnlockedSkinIds = giftUnlockedSkinIds,
        )

    private fun skin(skinId: String, isFree: Boolean, bundled: Boolean = false) = Skin(
        skinId = skinId,
        name = skinId,
        isFree = isFree,
        prestige = false,
        bundled = bundled,
        character = CharacterStates(
            stop = frame(),
            running = RunningState(default = frame()),
            pause = null,
            complete = frame(),
        ),
        timer = null,
    )

    private fun frame() = FrameSet(frames = listOf("f0.png"), frameDurationMs = 1000L)

    private fun assertVisible(root: View, vararg ids: Int) {
        ids.forEach { id -> assertEquals(View.VISIBLE, root.findViewById<View>(id).visibility) }
    }

    private fun assertGone(root: View, vararg ids: Int) {
        ids.forEach { id -> assertEquals(View.GONE, root.findViewById<View>(id).visibility) }
    }
}
