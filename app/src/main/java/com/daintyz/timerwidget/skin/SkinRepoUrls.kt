package com.daintyz.timerwidget.skin

/**
 * 디자인 레포(github.com/shenika27/daintyz_timer_characterList) 에셋 URL 규칙의 단일 출처.
 *
 * 폴더 규칙 (skinId 기준): 테마별 표시 에셋을 preview/{id}/ 한 폴더에 모은다.
 *   preview/{id}/thumb.png     ← 테마 썸네일(고정 파일명, 상점 목록/타이머 탭 공용)
 *   preview/{id}/prev01.png …  ← 미리보기 팝업용. prev01,02,03… 가변 개수
 *
 * catalog.json이 baseUrl을 명시하면 그 값을 쓰고, 카탈로그 밖(내장/이미 받은) 스킨은
 * [ASSET_BASE](jsDelivr CDN)를 쓴다. 무거운 에셋은 jsDelivr로 받는 게 캐시 전략(메모리: skin-catalog-cdn-strategy).
 */
object SkinRepoUrls {

    const val ASSET_BASE = "https://cdn.jsdelivr.net/gh/shenika27/daintyz_timer_characterList@main"

    fun themeThumb(skinId: String, base: String = ASSET_BASE) = "$base/preview/$skinId/thumb.png"

    /** prev{NN}.png 미리보기 URL. index는 1부터(prev01, prev02, …). */
    fun preview(skinId: String, index: Int, base: String = ASSET_BASE) =
        "$base/preview/$skinId/prev%02d.png".format(index)

    // 미리보기 팝업이 가변 개수로 바뀌기 전까지 상세화면이 쓰는 첫 두 장(prev01/prev02) 별칭.
    fun previewStop(skinId: String, base: String = ASSET_BASE) = preview(skinId, 1, base)
    fun previewRunning(skinId: String, base: String = ASSET_BASE) = preview(skinId, 2, base)
}
