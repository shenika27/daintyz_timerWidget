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
    /** 캐릭터 탭 썸네일 PNG URL (thumb_character/{id}.png). */
    val thumbnailUrl: String,
    /** 타이머 탭 썸네일(미리보기) PNG URL (thumb_timer/{id}.png). */
    val timerThumbnailUrl: String,
    /** 상세화면 '정지' 미리보기 URL (preview/{id}/prev01.png). */
    val previewStopUrl: String,
    /** 상세화면 '진행중' 미리보기 URL (preview/{id}/prev02.png). */
    val previewRunningUrl: String,
    val version: Int = 1
)
