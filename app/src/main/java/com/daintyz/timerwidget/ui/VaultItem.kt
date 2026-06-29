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
 * - [Remote]: 미다운로드 catalog 테마. [owned]=권리 보유(이용권/구매/기프트/무료)지만 아직 파일 없음 → 다운로드 대상.
 *   권리 없으면 구매 대상. (owned는 빌더가 권한 판정으로 채워 넣는다)
 */
sealed interface VaultItem {
    val id: String
    val name: String
    val owned: Boolean
    val isFree: Boolean
    val price: Int
    val prestige: Boolean

    /** Play 인앱상품 ID(SKU). 결제 대상 원격 테마만 가짐. 로컬(보유/내장) 스킨은 null. */
    val productId: String?

    /** 상점 히어로 카드 부제. 없으면 부제 줄 생략. */
    val description: String?

    /** 출시일("yyyy-MM-dd"). null이면 NEW 판정 불가. */
    val createdAt: String?

    /** 출시일로부터 [NEW_BADGE_DAYS]일 이내면 true(상점 NEW 배지). 파싱 실패/없음이면 false. */
    val isNew: Boolean get() = isWithinNewWindow(createdAt)

    /** 한정구매 시작일("yyyy-MM-dd"). null이면 시작 제한 없음. 로컬 스킨은 항상 null. */
    val saleStart: String?

    /** 한정구매 종료일("yyyy-MM-dd", 당일 포함). null이면 종료 제한 없음. 로컬 스킨은 항상 null. */
    val saleEnd: String?

    /** 현재 기기 날짜 기준 판매 상태. 로컬(보유/내장) 스킨은 saleStart/End가 없어 항상 ACTIVE. */
    val saleStatus: SaleStatus get() = saleWindowStatus(saleStart, saleEnd)

    data class Local(val skin: Skin, override val owned: Boolean) : VaultItem {
        override val id get() = skin.skinId
        override val name get() = skin.name
        override val isFree get() = skin.isFree
        override val price get() = 0
        override val prestige get() = skin.prestige
        override val productId: String? get() = null
        override val description get() = skin.description
        override val createdAt get() = skin.createdAt
        override val saleStart get() = null
        override val saleEnd get() = null
    }

    data class Remote(val entry: RemoteSkinEntry, override val owned: Boolean = false) : VaultItem {
        override val id get() = entry.skinId
        override val name get() = entry.name
        override val isFree get() = entry.isFree
        override val price get() = entry.price
        override val prestige get() = entry.prestige
        override val productId: String? get() = entry.productId
        override val description get() = entry.description
        override val createdAt get() = entry.createdAt
        override val saleStart get() = entry.saleStart
        override val saleEnd get() = entry.saleEnd
    }
}

/**
 * 가격 라벨. 무료/미설정이면 "무료", 아니면 "12,000원" 형식.
 * [prestigeMark]=true면 프리스티지 테마 가격 앞에 ✦ 를 붙인다(창고 카드 전용 표기).
 * Store/Vault 양쪽에서 쓰던 중복 구현을 단일 출처로 모은 것.
 */
fun priceLabel(item: VaultItem, prestigeMark: Boolean = false): String {
    val base = if (item.isFree || item.price <= 0) "무료" else "%,d원".format(item.price)
    return if (prestigeMark && item.prestige) "✦ $base" else base
}

/** 한정구매 기간 판정 결과. */
enum class SaleStatus {
    /** 구매 가능(기간 내이거나 기간 제한 없음). */ ACTIVE,
    /** 시작일 전 — 아직 미출시(상점에서 숨김). */ UPCOMING,
    /** 종료일 후 — 기간만료(상점에 잠금 표시, 구매 불가). */ EXPIRED,
}

/**
 * saleStart/saleEnd("yyyy-MM-dd") + 오늘 날짜로 판매 상태 판정. saleEnd는 당일 포함(그날 23:59까지 구매 가능).
 * 값이 없거나 파싱 실패한 경계는 '제한 없음'으로 취급한다.
 */
private fun saleWindowStatus(start: String?, end: String?): SaleStatus {
    val today = LocalDate.now()
    val startDate = start?.takeIf { it.isNotBlank() }
        ?.let { runCatching { LocalDate.parse(it.trim(), DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull() }
    val endDate = end?.takeIf { it.isNotBlank() }
        ?.let { runCatching { LocalDate.parse(it.trim(), DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull() }
    if (startDate != null && today.isBefore(startDate)) return SaleStatus.UPCOMING
    if (endDate != null && today.isAfter(endDate)) return SaleStatus.EXPIRED
    return SaleStatus.ACTIVE
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
