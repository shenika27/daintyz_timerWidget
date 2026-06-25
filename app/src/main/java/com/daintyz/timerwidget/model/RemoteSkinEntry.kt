package com.daintyz.timerwidget.model

/**
 * 서버 catalog.json 스킨(테마) 목록 항목. 앱 업데이트 없이 신규 테마 배포할 때 사용.
 *
 * URL들은 catalog에 명시돼 있으면 그 값을, 없으면 baseUrl + skinId 규칙으로 유추한 값이 채워진다
 * (SkinDownloader.fetchCatalog / SkinRepoUrls 참고). 구매·다운로드는 테마 단위이며,
 * 한 테마 zip에 캐릭터+타이머가 함께 들어있다.
 */
data class RemoteSkinEntry(
    val skinId: String,
    val name: String,
    /** 가격(원). 0이면 무료. [isFree]는 이 값에서 도출된다. */
    val price: Int,
    /** price <= 0 이면 무료. (구매/잠금 판정용 — 기존 호출부 호환을 위해 유지) */
    val isFree: Boolean,
    /** 프리스티지(희귀) 스킨 여부. 평생이용권으로도 해금 안 됨(항상 개별구매). 상점에서 별도 표시. */
    val prestige: Boolean = false,
    val zipUrl: String,
    /** 테마 썸네일 PNG URL (preview/{id}/thumb.png) — 상점/타이머 탭 공용. */
    val thumbnailUrl: String,
    /** 미리보기 에셋 베이스 URL(catalog baseUrl 또는 jsDelivr ASSET_BASE). 상세화면이 prevNN.png를 유추하는 기준. */
    val baseUrl: String,
    /** 상점 히어로 카드 부제(한 줄 설명). 없으면 카드에서 부제 줄 생략. */
    val description: String? = null,
    /** 출시일("yyyy-MM-dd"). 상점 NEW 배지 판정 기준(출시일+7일 이내). 없으면 NEW 안 뜸. */
    val createdAt: String? = null
)
