package com.daintyz.timerwidget.skin

/**
 * 디자인 레포(github.com/shenika27/daintyz_timer_characterList) 에셋 URL 규칙의 단일 출처.
 *
 * 폴더 규칙 (skinId 기준):
 *   thumb_character/{id}.png   ← 캐릭터 탭 썸네일
 *   thumb_timer/{id}.png       ← 타이머 탭 썸네일(미리보기)
 *   preview/{id}/prev01.png    ← 상세화면 '정지' 상태 미리보기
 *   preview/{id}/prev02.png    ← 상세화면 '진행중' 상태 미리보기
 *
 * catalog.json이 baseUrl을 명시하면 [forBase]로 그 값을 쓰고, 카탈로그 밖(내장/이미 받은) 스킨은
 * [ASSET_BASE](jsDelivr CDN)를 쓴다. 무거운 에셋은 jsDelivr로 받는 게 캐시 전략(메모리: skin-catalog-cdn-strategy).
 */
object SkinRepoUrls {

    const val ASSET_BASE = "https://cdn.jsdelivr.net/gh/shenika27/daintyz_timer_characterList@main"

    fun characterThumb(skinId: String, base: String = ASSET_BASE) = "$base/thumb_character/$skinId.png"
    fun timerThumb(skinId: String, base: String = ASSET_BASE) = "$base/thumb_timer/$skinId.png"
    fun previewStop(skinId: String, base: String = ASSET_BASE) = "$base/preview/$skinId/prev01.png"
    fun previewRunning(skinId: String, base: String = ASSET_BASE) = "$base/preview/$skinId/prev02.png"
}
