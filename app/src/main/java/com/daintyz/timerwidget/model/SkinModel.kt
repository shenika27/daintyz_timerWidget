package com.daintyz.timerwidget.model

/**
 * skin.json 스키마에 대응하는 데이터 클래스 (설계 문서 6-2 참고).
 *
 * 신규 스킨은 코드 수정 없이 assets/skins/<id>/ 폴더 + skin.json 추가만으로 확장된다.
 */
data class Skin(
    val skinId: String,
    val name: String,
    val isFree: Boolean,
    /**
     * 프리스티지(희귀) 스킨 여부. true면 '업데이트 평생이용권'으로도 해금되지 않고 항상 개별구매다.
     * skin.json의 "prestige" 플래그에서 파싱(없으면 false). 상점에서 별도 표시한다.
     */
    val prestige: Boolean,
    /** 상점 히어로 카드 부제(한 줄 설명). 없으면 카드에서 부제 줄 생략. */
    val description: String? = null,
    val localized: Map<String, LocalizedSkinText> = emptyMap(),
    /** 출시일("yyyy-MM-dd"). 상점 NEW 배지 판정 기준(출시일+7일 이내). 없으면 NEW 안 뜸. */
    val createdAt: String? = null,
    /**
     * 앱에 내장된 기본 에셋(assets/skins/) 스킨이면 true, 디자인 레포에서 다운로드한(filesDir) 스킨이면 false.
     * 상점은 디자인 레포 항목만 노출하므로 내장 기본 스킨(예: cha01)은 bundled=true로 걸러낸다.
     */
    val bundled: Boolean = false,
    val character: CharacterStates,
    /**
     * 타이머 영역 스킨 (설계: "기능=영역, 버튼=그림").
     * 모든 테마는 타이머 디자인을 포함한다(필수). skin.json에 timer 블록이 있으면 그 값으로,
     * (내장/테스트 스킨처럼) 없으면 기본 스킨(내장 박스/구분선/기호 버튼)으로 채워진다.
     * (파싱 단계에서 항상 채워지므로 실질적으로 non-null이지만, 안전을 위해 nullable 유지)
     */
    val timer: TimerSkin?
)

fun Skin.displayName(languageTag: String): String =
    localizedText(languageTag)?.name?.takeIf { it.isNotBlank() } ?: name

fun Skin.displayDescription(languageTag: String): String? =
    localizedText(languageTag)?.description?.takeIf { it.isNotBlank() } ?: description

private fun Skin.localizedText(languageTag: String): LocalizedSkinText? =
    localized[languageTag.lowercase()] ?: localized[languageTag.substringBefore('-').lowercase()]

/**
 * 타이머 영역의 시각 요소를 스킨이 그릴지 결정한다.
 * 1차: 디폴트(현재 모습) / 노스킨(전부 없음) 두 가지를 boolean/enum으로 표현.
 * 추후 유료 스킨은 여기에 배경/버튼 PNG 파일명을 추가해 확장한다.
 */
data class TimerSkin(
    /**
     * 스킨이 그린 타이머 배경 PNG 파일명(스킨 폴더 기준). 박스·세로선(시간↔버튼)·버튼사이 가로선이
     * 모두 이 한 장에 그려진다(가로 레이아웃은 모든 상태에서 선 구성이 동일). 출시 테마는 무조건 포함한다.
     * null이면 내장 박스(showBox)로 폴백(주로 개발/내장 테스트 스킨).
     */
    val background: String?,
    /** background가 없을 때만 쓰는 내장 박스 폴백. false면 투명. */
    val showBox: Boolean,
    /** 버튼 그림 방식. */
    val buttonStyle: ButtonStyle,
    /**
     * buttonStyle == SKIN일 때 사용할 버튼 PNG 파일명(스킨 폴더 기준).
     * 누락된 심볼은 내장 벡터로 폴백한다. SKIN이 아니면 무시된다.
     */
    val buttons: TimerButtons?,
    /**
     * 타이머 숫자 글꼴 지정. 위젯에서 숫자는 편집 불가이므로 스킨이 글꼴/색/크기를 정한다.
     * null이면 레이아웃 기본값(monospace / timer_digit / 30sp)을 그대로 쓴다.
     */
    val font: TimerFont?
)

/**
 * 타이머 숫자 폰트. 각 필드 null이면 해당 속성은 레이아웃 기본값 유지.
 * family는 RemoteViews가 지원하는 내장 패밀리명(monospace, sans-serif, serif, sans-serif-condensed 등).
 * 커스텀 .ttf는 1차 미지원(추후 비트맵 렌더링으로 확장).
 */
data class TimerFont(
    /** 내장 패밀리명(monospace 등). [file]이 있으면 무시된다. */
    val family: String?,
    /** "#RRGGBB" 또는 "#AARRGGBB". */
    val color: String?,
    val sizeSp: Float?,
    /**
     * 커스텀 폰트 파일명(.ttf, 스킨 폴더 기준). 지정 시 숫자를 이 Typeface로 비트맵 렌더링한다
     * (RemoteViews는 setFontFamily가 비-remotable이라 TextView에 .ttf를 직접 못 먹임).
     * null이면 [family] 기반 TextView 경로. 비트맵 모드에선 [sizeSp]는 fitCenter로 영역에 맞춰져 무시된다.
     */
    val file: String?
)

/**
 * 스킨이 직접 그린 버튼 PNG 파일명 (스킨 폴더 기준). 각 필드 null이면 그 심볼만 내장 벡터로 폴백.
 * 한 슬롯(start_pause)이 상태에 따라 play/pause로 갈리므로 심볼 단위(5개)로 받는다.
 */
data class TimerButtons(
    val minus: String?,
    val plus: String?,
    val play: String?,
    val pause: String?,
    val stop: String?
)

enum class ButtonStyle {
    /** 내장 기호 벡터(ic_btn_*)를 그린다. */
    DEFAULT,
    /** 버튼 그림 없음(투명). 탭 영역은 유지. */
    NONE,
    /** 스킨이 제공한 버튼 PNG를 그린다(누락 심볼은 내장 벡터 폴백). 탭 영역은 유지. */
    SKIN;

    companion object {
        fun fromKey(key: String?): ButtonStyle =
            entries.firstOrNull { it.name.equals(key, ignoreCase = true) } ?: DEFAULT
    }
}

data class CharacterStates(
    /** 정지(타이머 미작동) 상태 캐릭터. (이전 명칭: idle) */
    val stop: FrameSet,
    /** default 서브키로 한 단계 감싸 향후 컨디션 분기(tired/fresh 등) 대비 (설계 문서 6-2). */
    val running: RunningState,
    /** 일시정지 상태 전용 캐릭터. null이면 stop 프레임으로 폴백. */
    val pause: FrameSet?,
    val complete: FrameSet
)

/**
 * running 상태의 프레임셋 묶음.
 * 1차 버전에서는 [default]만 사용. 향후 조건부 키(tired/fresh 등)를 [conditional]에 채워 넣는다.
 * 폴백 규칙: 조건에 맞는 분기 프레임셋이 없으면 항상 [default]로 폴백 (설계 문서 6-2 필수 규칙).
 */
data class RunningState(
    val default: FrameSet,
    val conditional: List<ConditionalFrameSet> = emptyList()
)

/**
 * 향후 컨디션 분기용 프레임셋 (1차 버전 미사용 — 스키마 자리만 마련).
 * 예: condition = { "type": "remainingTime", "lte": 600 }
 */
data class ConditionalFrameSet(
    val key: String,
    val frameSet: FrameSet,
    val condition: Map<String, Any?>
)

data class FrameSet(
    val frames: List<String>,
    val frameDurationMs: Long
) {
    init {
        require(frames.isNotEmpty()) { "FrameSet은 최소 1개의 프레임이 필요합니다." }
    }
}
