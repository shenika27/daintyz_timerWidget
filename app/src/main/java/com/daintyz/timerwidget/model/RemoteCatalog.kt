package com.daintyz.timerwidget.model

/**
 * catalog.json 최상위 메타와 스킨 목록을 함께 담는다.
 *
 * [skins]는 기존 상점/창고 목록용 테마 항목이고,
 * [lifetimePassGiftCodes]는 평생이용권 기프트코드 해시와 입력 가능 기한이다.
 */
data class RemoteCatalog(
    val skins: List<RemoteSkinEntry>,
    val lifetimePassGiftCodes: List<LifetimePassGiftCode> = emptyList(),
)

data class LifetimePassGiftCode(
    val hash: String,
    /** yyyy-MM-dd. 해당 날짜까지 입력 가능하며, null이면 만료 제한 없음. */
    val expiresAt: String? = null,
)
