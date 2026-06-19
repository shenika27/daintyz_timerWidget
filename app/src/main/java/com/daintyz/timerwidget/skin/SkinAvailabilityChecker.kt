package com.daintyz.timerwidget.skin

/**
 * "이 스킨을 사용 가능한가?"를 판단하는 함수를 한 곳에 집중 (설계 문서 6-4).
 *
 * 1차 버전: isFree == true 만 검사.
 * 향후 결제 연동 시 아래 한 줄만 바꾸면 됨:
 *   isFree == true || purchasedSkinIds.contains(skinId)
 *
 * TODO(1차 구현):
 * - fun isSkinAvailable(skin: Skin, purchasedSkinIds: Set<String> = emptySet()): Boolean
 */
class SkinAvailabilityChecker
