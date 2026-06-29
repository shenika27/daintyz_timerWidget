package com.daintyz.timerwidget.billing

import com.daintyz.timerwidget.model.CharacterStates
import com.daintyz.timerwidget.model.FrameSet
import com.daintyz.timerwidget.model.LayoutMode
import com.daintyz.timerwidget.model.RemoteSkinEntry
import com.daintyz.timerwidget.model.RunningState
import com.daintyz.timerwidget.model.Skin
import com.daintyz.timerwidget.model.TimerData
import com.daintyz.timerwidget.model.TimerState
import org.junit.Assert.assertEquals
import org.junit.Test

class EntitlementCleanupTest {

    @Test
    fun refunded_downloaded_paid_skin_is_revoked_and_selection_falls_back() {
        val data = timerData(selectedCharacterSkinId = "paid", selectedTimerSkinId = "paid")
        val localSkins = listOf(
            skin("paid", isFree = false),
            skin("cha01", isFree = true, bundled = true),
        )
        val catalog = mapOf("paid" to entry("paid", price = 1000))

        assertEquals(
            setOf("paid"),
            EntitlementCleanup.revokedDownloadedSkinIds(localSkins.filterNot { it.bundled }, catalog, data),
        )
        assertEquals(
            timerData(selectedCharacterSkinId = "cha01", selectedTimerSkinId = "cha01"),
            EntitlementCleanup.sanitizeSelections(data, localSkins, catalog, fallbackSkinId = "cha01"),
        )
    }

    @Test
    fun gift_unlocked_paid_skin_is_not_revoked() {
        val data = timerData(
            selectedCharacterSkinId = "gift",
            selectedTimerSkinId = "gift",
            giftUnlockedSkinIds = setOf("gift"),
        )
        val localSkins = listOf(skin("gift", isFree = false))
        val catalog = mapOf("gift" to entry("gift", price = 1000))

        assertEquals(
            emptySet<String>(),
            EntitlementCleanup.revokedDownloadedSkinIds(localSkins, catalog, data),
        )
    }

    @Test
    fun lifetime_pass_keeps_regular_paid_skin() {
        val data = timerData(
            selectedCharacterSkinId = "paid",
            selectedTimerSkinId = "paid",
            hasLifetimePass = true,
        )
        val localSkins = listOf(skin("paid", isFree = false))
        val catalog = mapOf("paid" to entry("paid", price = 1000))

        assertEquals(
            emptySet<String>(),
            EntitlementCleanup.revokedDownloadedSkinIds(localSkins, catalog, data),
        )
    }

    @Test
    fun lifetime_pass_does_not_keep_prestige_skin() {
        val data = timerData(
            selectedCharacterSkinId = "prestige",
            selectedTimerSkinId = "prestige",
            hasLifetimePass = true,
        )
        val localSkins = listOf(skin("prestige", isFree = false, prestige = true))
        val catalog = mapOf("prestige" to entry("prestige", price = 2000, prestige = true))

        assertEquals(
            setOf("prestige"),
            EntitlementCleanup.revokedDownloadedSkinIds(localSkins, catalog, data),
        )
    }

    @Test
    fun fallback_prefers_default_bundled_free_skin() {
        val fallback = EntitlementCleanup.bundledFreeFallbackSkinId(
            listOf(
                skin("asset01", isFree = true, bundled = true),
                skin("cha01", isFree = true, bundled = true),
            ),
        )

        assertEquals("cha01", fallback)
    }

    private fun timerData(
        selectedCharacterSkinId: String = "cha01",
        selectedTimerSkinId: String = "cha01",
        purchasedSkinIds: Set<String> = emptySet(),
        hasLifetimePass: Boolean = false,
        giftUnlockedSkinIds: Set<String> = emptySet(),
    ) = TimerData(
        state = TimerState.IDLE,
        targetEndElapsed = 0L,
        stateEnteredElapsed = 0L,
        remainingMillisAtPause = 0L,
        totalMillis = 0L,
        lastSetSeconds = 600,
        stepSeconds = 60,
        layoutMode = LayoutMode.TOP,
        selectedCharacterSkinId = selectedCharacterSkinId,
        selectedTimerSkinId = selectedTimerSkinId,
        purchasedSkinIds = purchasedSkinIds,
        hasLifetimePass = hasLifetimePass,
        giftUnlockedSkinIds = giftUnlockedSkinIds,
    )

    private fun skin(
        skinId: String,
        isFree: Boolean,
        prestige: Boolean = false,
        bundled: Boolean = false,
    ) = Skin(
        skinId = skinId,
        name = skinId,
        isFree = isFree,
        prestige = prestige,
        bundled = bundled,
        character = CharacterStates(
            stop = frame(),
            running = RunningState(default = frame()),
            pause = null,
            complete = frame(),
        ),
        timer = null,
    )

    private fun entry(skinId: String, price: Int, prestige: Boolean = false) = RemoteSkinEntry(
        skinId = skinId,
        name = skinId,
        price = price,
        isFree = price <= 0,
        productId = "${skinId}_sku",
        prestige = prestige,
        zipUrl = "https://example.test/$skinId.zip",
        thumbnailUrl = "https://example.test/$skinId.png",
        baseUrl = "https://example.test",
    )

    private fun frame() = FrameSet(frames = listOf("f0.png"), frameDurationMs = 1000L)
}
