package com.daintyz.timerwidget.skin

/**
 * assets/skins/ 폴더를 스캔하여 skin.json들을 로드/캐싱하는 저장소.
 *
 * TODO(1차 구현):
 * - fun loadAllSkins(context: Context): List<Skin>  (assets/skins/*/skin.json 파싱)
 * - 신규 스킨 추가 시 코드 수정 없이 폴더+json 등록만으로 확장되도록 보장 (설계 문서 6-3)
 */
class SkinRepository
