package com.daintyz.timerwidget.skin

/**
 * 디자인 레포(github.com/shenika27/daintyz_timer_characterList) 에셋 URL 규칙의 단일 출처.
 *
 * 폴더 규칙 (skinId 기준): 테마별 표시 에셋을 preview/{id}/ 한 폴더에 모은다.
 *   preview/{id}/thumb.png     ← 테마 썸네일(고정 파일명, 상점 목록/타이머 탭 공용)
 *   preview/{id}/prev01.* …    ← 미리보기. prev01,02,03… 가변 개수. 확장자는 칸마다 자유(.png/.gif 혼용 가능, [PREVIEW_EXTS] 순으로 탐침).
 *
 * catalog.json이 baseUrl을 명시하면 그 값을 쓰고, 카탈로그 밖(내장/이미 받은) 스킨은
 * [ASSET_BASE](jsDelivr CDN)를 쓴다. 무거운 에셋은 jsDelivr로 받는 게 캐시 전략(메모리: skin-catalog-cdn-strategy).
 */
object SkinRepoUrls {

    const val ASSET_BASE = "https://cdn.jsdelivr.net/gh/shenika27/daintyz_timer_characterList@main"

    /** 미리보기 칸마다 시도할 확장자(앞에서부터 탐침, 먼저 존재하는 것을 사용). */
    val PREVIEW_EXTS = listOf("gif", "png")

    fun themeThumb(skinId: String, base: String = ASSET_BASE) = "$base/preview/$skinId/thumb.png"

    /**
     * prev{NN} 미리보기의 확장자 후보 URL 목록. index는 1부터(prev01, prev02, …).
     * 각 칸은 .gif/.png 등 무엇이든 될 수 있어, [PREVIEW_EXTS] 순으로 후보 URL을 만들어 존재하는 것을 고른다.
     */
    fun previewCandidates(skinId: String, index: Int, base: String = ASSET_BASE): List<String> =
        PREVIEW_EXTS.map { ext -> "$base/preview/$skinId/prev%02d.$ext".format(index) }
}
