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
    val isFree: Boolean,
    val zipUrl: String,
    /** 테마 썸네일 PNG URL (preview/{id}/thumb.png) — 상점/타이머 탭 공용. */
    val thumbnailUrl: String,
    /** 상세화면 '정지' 미리보기 URL (preview/{id}/prev01.png). */
    val previewStopUrl: String,
    /** 상세화면 '진행중' 미리보기 URL (preview/{id}/prev02.png). */
    val previewRunningUrl: String,
    /**
     * 이 테마가 타이머 디자인을 제공하는지(캐릭터+타이머) 여부. 캐릭터만이면 false.
     * 상점에서 태그로 식별. catalog.json에 없으면 true로 간주(기존 테마 호환). 로컬은 [com.daintyz.timerwidget.model.Skin.hasCustomTimer].
     */
    val hasTimer: Boolean = true,
    val version: Int = 1
)
