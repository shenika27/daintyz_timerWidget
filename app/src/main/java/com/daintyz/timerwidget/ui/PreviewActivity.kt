package com.daintyz.timerwidget.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.daintyz.timerwidget.R
import com.daintyz.timerwidget.data.TimerPreferences
import com.daintyz.timerwidget.model.Skin
import com.daintyz.timerwidget.model.TimerData
import com.daintyz.timerwidget.model.TimerState
import com.daintyz.timerwidget.skin.FrameAnimationController
import com.daintyz.timerwidget.skin.SkinRepository
import com.daintyz.timerwidget.widget.WidgetUpdater

/**
 * 적용 미리보기 화면 — 캐릭터/타이머 공용.
 *
 * 상태 탭(정지/진행/일시정지/완료)으로 실제 위젯을 [WidgetUpdater.buildRemoteViews] + [android.widget.RemoteViews.apply]로
 * 앱 내에 라이브 렌더한다(재구현 없음). **미리보는 영역만** 해당 테마로 치환하고, 나머지 영역은 현재 적용중 설정을 유지해
 * "이 테마 적용 시 내 위젯이 이렇게 된다"를 보여준다. 캐릭터 프레임은 Handler 루프로 모션을 살린다.
 *
 * [forPreview]=true로 렌더하므로 미리보기 안의 버튼/캐릭터 탭은 실제 타이머를 건드리지 않는다.
 */
class PreviewActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_AREA = "area"
        const val EXTRA_SKIN_ID = "skin_id"
        const val AREA_CHARACTER = "character"
        const val AREA_TIMER = "timer"

        private const val FRAME_TICK_MS = 100L
    }

    private lateinit var area: String
    private lateinit var skinId: String

    private var state = TimerState.IDLE
    private val handler = Handler(Looper.getMainLooper())

    /** 현재 렌더된 위젯 뷰의 캐릭터 이미지뷰 + 애니메이션에 쓸 캐릭터 스킨. */
    private var characterView: ImageView? = null
    private var characterSkin: Skin? = null

    /** RUNNING 상태에서 타이머 카운트다운을 표시할 TextView (커스텀 폰트 테마에선 null). */
    private var timerView: android.widget.TextView? = null
    /** RUNNING 상태의 타이머 종료 시각 (SystemClock.elapsedRealtime 기준). */
    private var timerEndElapsed: Long = 0L

    private val frameTick = object : Runnable {
        override fun run() {
            renderCharacterFrame()
            handler.postDelayed(this, FRAME_TICK_MS)
        }
    }

    private val timerTick = object : Runnable {
        override fun run() {
            val remaining = (timerEndElapsed - SystemClock.elapsedRealtime()).coerceAtLeast(0)
            val m = (remaining / 60_000).toInt()
            val s = ((remaining % 60_000) / 1_000).toInt()
            timerView?.text = "%02d:%02d".format(m, s)
            if (remaining > 0) handler.postDelayed(this, 1_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_preview)
        title = getString(R.string.preview_title)

        area = intent.getStringExtra(EXTRA_AREA) ?: AREA_CHARACTER
        skinId = intent.getStringExtra(EXTRA_SKIN_ID) ?: run { finish(); return }

        findViewById<Button>(R.id.btn_st_idle).setOnClickListener { selectState(TimerState.IDLE) }
        findViewById<Button>(R.id.btn_st_run).setOnClickListener { selectState(TimerState.RUNNING) }
        findViewById<Button>(R.id.btn_st_paused).setOnClickListener { selectState(TimerState.PAUSED) }
        findViewById<Button>(R.id.btn_st_complete).setOnClickListener { selectState(TimerState.COMPLETE) }

        selectState(TimerState.IDLE)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(frameTick)
        handler.removeCallbacks(timerTick)
    }

    private fun selectState(next: TimerState) {
        state = next
        handler.removeCallbacks(timerTick)
        // 토글 강조 (선택된 탭은 눌린 느낌으로 비활성).
        findViewById<Button>(R.id.btn_st_idle).isEnabled = next != TimerState.IDLE
        findViewById<Button>(R.id.btn_st_run).isEnabled = next != TimerState.RUNNING
        findViewById<Button>(R.id.btn_st_paused).isEnabled = next != TimerState.PAUSED
        findViewById<Button>(R.id.btn_st_complete).isEnabled = next != TimerState.COMPLETE
        renderWidget()
        if (next == TimerState.RUNNING) handler.post(timerTick)
    }

    /** 현재 상태로 위젯을 새로 인플레이트해 컨테이너에 붙이고, 캐릭터 애니메이션 루프를 재시작. */
    private fun renderWidget() {
        val data = previewData(state)
        val container = findViewById<FrameLayout>(R.id.preview_container)
        val views = WidgetUpdater.buildRemoteViews(this, data, SystemClock.elapsedRealtime(), forPreview = true)
        // applicationContext로 인플레이트: AppCompatActivity의 LayoutInflater는 <ImageView>를
        // AppCompatImageView로 치환하는데, 그 뷰는 setImageResource가 @RemotableViewMethod가 아니라
        // RemoteViews.apply가 거부한다(crash). 앱 컨텍스트엔 AppCompat factory가 없어 순수 프레임워크 뷰로 인플레이트됨.
        val rendered = views.apply(applicationContext, container)

        container.removeAllViews()
        rendered.layoutParams =
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        container.addView(rendered)

        characterView = rendered.findViewById<ImageView>(R.id.iv_character)
        timerView = rendered.findViewById(R.id.tv_timer_value)
        characterSkin = SkinRepository.findSkin(this, data.selectedCharacterSkinId)
            ?: SkinRepository.loadAllSkins(this).firstOrNull()

        handler.removeCallbacks(frameTick)
        renderCharacterFrame()
        handler.post(frameTick)
    }

    /** 캐릭터 프레임 1장 갱신 (모션). 프레임이 1장뿐이면 정지 이미지로 그대로 유지된다. */
    private fun renderCharacterFrame() {
        val view = characterView ?: return
        val skin = characterSkin ?: return
        val now = SystemClock.elapsedRealtime()
        val ctx = FrameAnimationController.FrameContext(remainingMs = 0L, elapsedMs = 0L, totalMs = 0L)
        val fileName = FrameAnimationController.currentFrameFile(skin, state, ctx, now)
        SkinRepository.loadFrameBitmap(this, skin.skinId, fileName)?.let { view.setImageBitmap(it) }
    }

    /**
     * 현재 적용중 설정을 베이스로, 미리보는 영역만 [skinId]로 치환하고 상태별 표본 시간을 채운 미리보기용 스냅샷.
     */
    private fun previewData(s: TimerState): TimerData {
        val base = TimerPreferences.get(this).load()
        val now = SystemClock.elapsedRealtime()
        val sampleMs = (base.lastSetMinutes.coerceAtLeast(1)) * 60_000L
        val withState = when (s) {
            TimerState.IDLE -> base.copy(state = TimerState.IDLE)
            TimerState.RUNNING -> {
                timerEndElapsed = now + sampleMs
                base.copy(
                    state = TimerState.RUNNING,
                    totalMillis = sampleMs,
                    targetEndElapsed = timerEndElapsed,
                    remainingMillisAtPause = 0L
                )
            }
            TimerState.PAUSED -> base.copy(
                state = TimerState.PAUSED,
                totalMillis = sampleMs,
                remainingMillisAtPause = sampleMs / 2
            )
            TimerState.COMPLETE -> base.copy(
                state = TimerState.COMPLETE,
                targetEndElapsed = 0L,
                remainingMillisAtPause = 0L
            )
        }
        // 이 스킨의 캐릭터를 항상 적용하고, 타이머 스킨이 있으면 함께 적용한다.
        val skin = SkinRepository.findSkin(this, skinId)
        return withState.copy(
            selectedCharacterSkinId = skinId,
            selectedTimerSkinId = if (skin?.timer != null) skinId else withState.selectedTimerSkinId
        )
    }
}
