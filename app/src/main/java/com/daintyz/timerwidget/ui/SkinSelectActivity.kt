package com.daintyz.timerwidget.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.daintyz.timerwidget.R
import com.daintyz.timerwidget.controller.TimerController
import com.daintyz.timerwidget.data.TimerPreferences
import com.daintyz.timerwidget.model.Skin
import com.daintyz.timerwidget.skin.RemoteImageLoader
import com.daintyz.timerwidget.skin.SkinAvailabilityChecker
import com.daintyz.timerwidget.skin.SkinRepoUrls
import com.daintyz.timerwidget.skin.SkinRepository

/**
 * 스킨 설정(적용) 화면.
 *
 * 캐릭터 / 타이머 두 탭으로 나눠, '가진(=사용 가능)' 스킨을 영역별로 독립 선택한다.
 * 구매·다운로드는 상점([SkinStoreActivity])에서 처리하고, 여기서는 적용만 한다.
 */
class SkinSelectActivity : AppCompatActivity() {

    private enum class Tab { CHARACTER, TIMER }

    private var tab = Tab.CHARACTER

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_skin_select)
        title = getString(R.string.skin_select_title)

        findViewById<Button>(R.id.btn_tab_character).setOnClickListener { switchTab(Tab.CHARACTER) }
        findViewById<Button>(R.id.btn_tab_timer).setOnClickListener { switchTab(Tab.TIMER) }
        renderList()
    }

    private fun switchTab(next: Tab) {
        if (tab == next) return
        tab = next
        renderList()
    }

    private fun renderList() {
        val container = findViewById<LinearLayout>(R.id.skin_list_container)
        container.removeAllViews()

        val data = TimerPreferences.get(this).load()
        // 적용 대상은 '사용 가능한' 로컬 스킨(내장 + 다운로드 완료)만. 잠긴 테마는 상점에서 구매.
        val skins = SkinRepository.loadAllSkins(this)
            .filter { SkinAvailabilityChecker.isSkinAvailable(it, data.purchasedSkinIds) }

        if (skins.isEmpty()) {
            val empty = TextView(this).apply {
                text = getString(R.string.skin_apply_empty)
                setPadding(0, 24, 0, 0)
            }
            container.addView(empty)
            return
        }

        val selectedId =
            if (tab == Tab.CHARACTER) data.selectedCharacterSkinId else data.selectedTimerSkinId
        val inflater = LayoutInflater.from(this)
        for (skin in skins) {
            val card = inflater.inflate(R.layout.item_skin, container, false)
            bindCard(card, skin, selectedId)
            container.addView(card)
        }
    }

    private fun bindCard(card: android.view.View, skin: Skin, selectedId: String) {
        val thumb = card.findViewById<ImageView>(R.id.iv_skin_thumb)
        when (tab) {
            // 캐릭터 탭: 내장 캐릭터 정지 프레임을 썸네일로.
            Tab.CHARACTER -> {
                val bitmap = skin.character.stop.frames.firstOrNull()
                    ?.let { SkinRepository.loadFrameBitmap(this, skin.skinId, it) }
                if (bitmap != null) thumb.setImageBitmap(bitmap)
                else thumb.setImageResource(R.drawable.frame_placeholder)
            }
            // 타이머 탭: 디자인 레포의 타이머 썸네일(thumb_timer)을 원격 로드.
            Tab.TIMER -> RemoteImageLoader.load(
                thumb, SkinRepoUrls.timerThumb(skin.skinId), R.drawable.frame_placeholder
            )
        }

        card.findViewById<TextView>(R.id.tv_skin_name).text = skin.name

        val isSelected = skin.skinId == selectedId
        val badge = card.findViewById<TextView>(R.id.tv_skin_badge)
        val action = card.findViewById<Button>(R.id.btn_skin_action)

        badge.text = if (isSelected) getString(R.string.skin_badge_selected)
        else getString(R.string.skin_badge_owned)

        if (isSelected) {
            action.text = getString(R.string.skin_badge_selected)
            action.isEnabled = false
            action.setOnClickListener(null)
        } else {
            action.text = getString(R.string.skin_btn_select)
            action.isEnabled = true
            action.setOnClickListener {
                when (tab) {
                    Tab.CHARACTER -> TimerController.selectCharacterSkin(this, skin.skinId)
                    Tab.TIMER -> TimerController.selectTimerSkin(this, skin.skinId)
                }
                Toast.makeText(this, "${skin.name} 적용됨", Toast.LENGTH_SHORT).show()
                renderList()
            }
        }
    }
}
