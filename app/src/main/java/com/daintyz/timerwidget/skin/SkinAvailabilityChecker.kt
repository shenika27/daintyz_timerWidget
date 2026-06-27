package com.daintyz.timerwidget.skin

import com.daintyz.timerwidget.model.Skin

/**
 * "이 스킨을 사용 가능한가?"를 판단하는 단일 지점 (설계 문서 6-4).
 *
 * 해금 규칙(BM):
 *   1) 무료(isFree)                                  → 항상 사용 가능
 *   2) 개별구매(purchasedSkinIds 포함, Play 결제)     → 사용 가능
 *   3) 기프트코드 해금(giftUnlockedSkinIds 포함)      → 사용 가능 (프리스티지 포함 — 코드는 무엇이든 해금)
 *   4) 업데이트 평생이용권(hasLifetimePass)          → 프리스티지가 '아닌' 유료 테마 일괄 해금
 *      └ 프리스티지(prestige) 스킨은 이용권으로 안 풀림 → 개별구매(2) 또는 기프트(3)로만 해금
 *
 * 결제 미연동 단계에서는 purchasedSkinIds=∅, hasLifetimePass=false로 들어와 사실상 '무료 + 기프트만' 동작한다.
 * [giftUnlockedSkinIds]는 Play 결제와 출처가 달라(환불 회수 대상 아님) 별도 인자로 받는다.
 */
object SkinAvailabilityChecker {

    fun isSkinAvailable(
        skin: Skin,
        purchasedSkinIds: Set<String> = emptySet(),
        hasLifetimePass: Boolean = false,
        giftUnlockedSkinIds: Set<String> = emptySet()
    ): Boolean = isSkinAvailable(
        skin.skinId, skin.isFree, skin.prestige, purchasedSkinIds, hasLifetimePass, giftUnlockedSkinIds
    )

    /**
     * Skin 객체가 없을 때(원격 catalog 항목 등) 필드만으로 동일 규칙을 판정한다.
     * 상점/창고 목록에서 미다운로드 원격 테마의 '보유(권리)' 여부 계산에 쓴다.
     */
    fun isSkinAvailable(
        skinId: String,
        isFree: Boolean,
        prestige: Boolean,
        purchasedSkinIds: Set<String> = emptySet(),
        hasLifetimePass: Boolean = false,
        giftUnlockedSkinIds: Set<String> = emptySet()
    ): Boolean {
        if (isFree) return true
        if (skinId in purchasedSkinIds) return true
        if (skinId in giftUnlockedSkinIds) return true
        // 평생이용권은 프리스티지를 제외한 유료 테마만 해금.
        return hasLifetimePass && !prestige
    }

    /** 잠금 표시 여부 = 사용 불가일 때. (스킨 선택 UI에서 자물쇠/구매버튼 노출 판단) */
    fun isLocked(
        skin: Skin,
        purchasedSkinIds: Set<String> = emptySet(),
        hasLifetimePass: Boolean = false,
        giftUnlockedSkinIds: Set<String> = emptySet()
    ): Boolean = !isSkinAvailable(skin, purchasedSkinIds, hasLifetimePass, giftUnlockedSkinIds)
}
