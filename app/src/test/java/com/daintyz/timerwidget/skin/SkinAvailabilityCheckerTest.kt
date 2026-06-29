package com.daintyz.timerwidget.skin

import com.daintyz.timerwidget.model.CharacterStates
import com.daintyz.timerwidget.model.FrameSet
import com.daintyz.timerwidget.model.RunningState
import com.daintyz.timerwidget.model.Skin
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 해금 규칙(BM) 단일 지점 검증. 개별구매 + 평생이용권 모델 + 기프트 출처분리.
 */
class SkinAvailabilityCheckerTest {

    @Test
    fun free_skin_is_always_available() {
        assertTrue(SkinAvailabilityChecker.isSkinAvailable(skin(isFree = true)))
    }

    @Test
    fun paid_skin_locked_without_any_entitlement() {
        assertFalse(SkinAvailabilityChecker.isSkinAvailable(skin(skinId = "p")))
    }

    @Test
    fun individual_purchase_unlocks_only_that_skin() {
        val purchased = setOf("p")
        assertTrue(SkinAvailabilityChecker.isSkinAvailable(skin(skinId = "p"), purchasedSkinIds = purchased))
        assertFalse(SkinAvailabilityChecker.isSkinAvailable(skin(skinId = "q"), purchasedSkinIds = purchased))
    }

    @Test
    fun lifetime_pass_unlocks_non_prestige_but_not_prestige() {
        assertTrue(
            SkinAvailabilityChecker.isSkinAvailable(skin(skinId = "p", prestige = false), hasLifetimePass = true)
        )
        assertFalse(
            SkinAvailabilityChecker.isSkinAvailable(skin(skinId = "p", prestige = true), hasLifetimePass = true)
        )
    }

    @Test
    fun gift_unlock_works_for_prestige_too_and_is_independent_of_purchases() {
        val gift = setOf("rare")
        // 기프트는 프리스티지도 해금하며, purchasedSkinIds/평생이용권과 무관하게 동작한다.
        assertTrue(
            SkinAvailabilityChecker.isSkinAvailable(
                skin(skinId = "rare", prestige = true), giftUnlockedSkinIds = gift
            )
        )
        assertFalse(
            SkinAvailabilityChecker.isSkinAvailable(
                skin(skinId = "other", prestige = true), giftUnlockedSkinIds = gift
            )
        )
    }

    private fun skin(
        skinId: String = "cha01",
        isFree: Boolean = false,
        prestige: Boolean = false,
    ) = Skin(
        skinId = skinId,
        name = skinId,
        isFree = isFree,
        prestige = prestige,
        character = CharacterStates(
            stop = frame(),
            running = RunningState(default = frame()),
            pause = null,
            complete = frame(),
        ),
        timer = null,
    )

    private fun frame() = FrameSet(frames = listOf("f0.png"), frameDurationMs = 1000L)
}
