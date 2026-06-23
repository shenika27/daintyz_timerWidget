package com.daintyz.timerwidget.skin

import com.daintyz.timerwidget.model.Skin

/**
 * "이 스킨을 사용 가능한가?"를 판단하는 단일 지점 (설계 문서 6-4).
 *
 * 1차 버전: isFree == true 만 검사.
 * 향후 결제 연동 시 [isSkinAvailable] 한 줄만 아래로 바꾸면 됨:
 *   skin.isFree || purchasedSkinIds.contains(skin.skinId)
 */
object SkinAvailabilityChecker {

    fun isSkinAvailable(skin: Skin, purchasedSkinIds: Set<String> = emptySet()): Boolean {
        // 1차 버전: 무료만 사용 가능. (purchasedSkinIds는 결제 대비 자리로 미리 받아둠)
        return skin.isFree
        // 결제 연동 후: return skin.isFree || purchasedSkinIds.contains(skin.skinId)
    }

    /** 잠금 표시 여부 = 사용 불가일 때. (스킨 선택 UI에서 자물쇠/구매버튼 노출 판단) */
    fun isLocked(skin: Skin, purchasedSkinIds: Set<String> = emptySet()): Boolean =
        !isSkinAvailable(skin, purchasedSkinIds)
}
