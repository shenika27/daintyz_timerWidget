package com.daintyz.timerwidget.ui

import com.daintyz.timerwidget.model.RemoteSkinEntry
import com.daintyz.timerwidget.model.Skin

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

    data class Local(val skin: Skin, override val owned: Boolean) : VaultItem {
        override val id get() = skin.skinId
        override val name get() = skin.name
        override val isFree get() = skin.isFree
        override val price get() = 0
        override val prestige get() = skin.prestige
    }

    data class Remote(val entry: RemoteSkinEntry) : VaultItem {
        override val id get() = entry.skinId
        override val name get() = entry.name
        override val owned get() = false
        override val isFree get() = entry.isFree
        override val price get() = entry.price
        override val prestige get() = entry.prestige
    }
}
