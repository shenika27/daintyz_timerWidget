package com.daintyz.timerwidget.skin

import com.daintyz.timerwidget.model.Skin

/**
 * "이 스킨을 사용 가능한가?"를 판단하는 단일 지점 (설계 문서 6-4).
 *
 * 해금 규칙(BM):
 *   1) 무료(isFree)                                  → 항상 사용 가능
 *   2) 개별구매(purchasedSkinIds 포함)               → 사용 가능
 *   3) 업데이트 평생이용권(hasLifetimePass)          → 프리스티지가 '아닌' 유료 테마 일괄 해금
 *      └ 프리스티지(prestige) 스킨은 이용권으로 안 풀림 → 반드시 개별구매(위 2번)로만 해금
 *
 * 결제 미연동 단계에서는 purchasedSkinIds=∅, hasLifetimePass=false로 들어와 사실상 '무료만' 동작한다.
 */
object SkinAvailabilityChecker {

    fun isSkinAvailable(
        skin: Skin,
        purchasedSkinIds: Set<String> = emptySet(),
        hasLifetimePass: Boolean = false
    ): Boolean {
        if (skin.isFree) return true
        if (skin.skinId in purchasedSkinIds) return true
        // 평생이용권은 프리스티지를 제외한 유료 테마만 해금.
        if (hasLifetimePass && !skin.prestige) return true
        return false
    }

    /** 잠금 표시 여부 = 사용 불가일 때. (스킨 선택 UI에서 자물쇠/구매버튼 노출 판단) */
    fun isLocked(
        skin: Skin,
        purchasedSkinIds: Set<String> = emptySet(),
        hasLifetimePass: Boolean = false
    ): Boolean = !isSkinAvailable(skin, purchasedSkinIds, hasLifetimePass)
}
