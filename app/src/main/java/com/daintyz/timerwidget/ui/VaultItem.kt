package com.daintyz.timerwidget.ui

import com.daintyz.timerwidget.model.RemoteSkinEntry
import com.daintyz.timerwidget.model.Skin
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** 상점 NEW 배지: 출시일(createdAt)로부터 이 일수 이내면 신규로 표시. */
private const val NEW_BADGE_DAYS = 7L

/**
 * 통합 캐러셀의 카드 1장. 로컬(내장/다운로드 완료) 스킨이거나, 카탈로그에만 있는(미다운로드) 원격 테마다.
 *
 * - [Local]: 보유 여부([owned])가 갈린다. 보유면 적용 가능, 미보유(유료 미구매)면 잠김.
 * - [Remote]: 항상 미보유. 무료면 다운로드, 유료면 구매 대상.
 */
sealed interface VaultItem {
    val id: String
    val name: String
    val owned: Boolean
    val isFree: Boolean
    val price: Int
    val prestige: Boolean

    /** 상점 히어로 카드 부제. 없으면 부제 줄 생략. */
    val description: String?

    /** 출시일("yyyy-MM-dd"). null이면 NEW 판정 불가. */
    val createdAt: String?

    /** 출시일로부터 [NEW_BADGE_DAYS]일 이내면 true(상점 NEW 배지). 파싱 실패/없음이면 false. */
    val isNew: Boolean get() = isWithinNewWindow(createdAt)

    data class Local(val skin: Skin, override val owned: Boolean) : VaultItem {
        override val id get() = skin.skinId
        override val name get() = skin.name
        override val isFree get() = skin.isFree
        override val price get() = 0
        override val prestige get() = skin.prestige
        override val description get() = skin.description
        override val createdAt get() = skin.createdAt
    }

    data class Remote(val entry: RemoteSkinEntry) : VaultItem {
        override val id get() = entry.skinId
        override val name get() = entry.name
        override val owned get() = false
        override val isFree get() = entry.isFree
        override val price get() = entry.price
        override val prestige get() = entry.prestige
        override val description get() = entry.description
        override val createdAt get() = entry.createdAt
    }
}

private fun isWithinNewWindow(createdAt: String?): Boolean {
    if (createdAt.isNullOrBlank()) return false
    val released = runCatching {
        LocalDate.parse(createdAt.trim(), DateTimeFormatter.ISO_LOCAL_DATE)
    }.getOrNull() ?: return false
    val today = LocalDate.now()
    // 출시일 당일 ~ 출시일+7일 까지 NEW. 미래 출시일도 NEW로 본다.
    return !today.isAfter(released.plusDays(NEW_BADGE_DAYS))
}
