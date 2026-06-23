package com.daintyz.timerwidget.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.daintyz.timerwidget.R
import com.daintyz.timerwidget.data.TimerPreferences
import com.daintyz.timerwidget.model.RemoteSkinEntry
import com.daintyz.timerwidget.model.Skin
import com.daintyz.timerwidget.skin.RemoteImageLoader
import com.daintyz.timerwidget.skin.SkinAvailabilityChecker
import com.daintyz.timerwidget.skin.SkinRepoUrls
import com.daintyz.timerwidget.skin.SkinDownloader
import com.daintyz.timerwidget.skin.SkinRepository

/**
 * 상점 목록 화면.
 *
 * 로컬 스킨(내장 + 다운로드 완료)과 서버 카탈로그를 합쳐 전체 테마를 보여준다.
 * 카드를 탭하면 상세/미리보기([SkinDetailActivity])로 이동하고, 구매·다운로드는 거기서 처리한다.
 * (구매는 테마 단위 — 해금되면 캐릭터/타이머 둘 다 사용 가능)
 */
class SkinStoreActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SkinStoreActivity"
        // catalog.json은 자주 바뀌므로 항상 최신인 raw에서 받는다. (무거운 에셋은 catalog의 baseUrl=jsDelivr로)
        private const val CATALOG_URL = "https://raw.githubusercontent.com/shenika27/daintyz_timer_characterList/main/catalog.json"
    }

    private var catalogEntries: List<RemoteSkinEntry> = emptyList()

    /** 체크 시 구매(보유)한 항목도 표시. 기본은 미구매만. */
    private var showAll = false

    /** 현재 다운로드 중인 skinId — 카드가 매 renderList마다 새로 그려져도 버튼 상태를 유지. */
    private val downloadingIds = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_skin_store)
        title = getString(R.string.store_title)
        findViewById<android.widget.CheckBox>(R.id.cb_show_all).setOnCheckedChangeListener { _, checked ->
            showAll = checked
            renderList()
        }
        renderList()
        fetchCatalog()
    }

    override fun onResume() {
        super.onResume()
        // 상세화면에서 다운로드/구매 후 돌아오면 상태 갱신.
        renderList()
    }

    private fun fetchCatalog() {
        Thread {
            runCatching { SkinDownloader.fetchCatalog(CATALOG_URL) }
                .onSuccess { entries ->
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        catalogEntries = entries
                        renderList()
                    }
                }
                .onFailure { Log.w(TAG, "카탈로그 로드 실패 (오프라인이거나 URL 미설정)", it) }
        }.start()
    }

    private fun renderList() {
        val container = findViewById<LinearLayout>(R.id.store_list_container)
        container.removeAllViews()

        val data = TimerPreferences.get(this).load()
        val localSkins = SkinRepository.loadAllSkins(this)
        val localIds = localSkins.map { it.skinId }.toSet()
        val inflater = LayoutInflater.from(this)

        // 로컬(보유/내장) 테마 — 기본은 미구매만 보이므로, 보유(사용 가능) 항목은 전체보기일 때만.
        for (skin in localSkins) {
            val owned = SkinAvailabilityChecker.isSkinAvailable(skin, data.purchasedSkinIds)
            if (owned && !showAll) continue
            val card = inflater.inflate(R.layout.item_skin, container, false)
            bindLocalCard(card, skin, data.purchasedSkinIds)
            container.addView(card)
        }
        // 카탈로그에만 있는(미다운로드) 테마 — 미구매라 항상 표시.
        for (entry in catalogEntries) {
            if (entry.skinId in localIds) continue
            val card = inflater.inflate(R.layout.item_skin, container, false)
            bindRemoteCard(card, entry)
            container.addView(card)
        }
    }

    private fun bindLocalCard(card: View, skin: Skin, purchasedSkinIds: Set<String>) {
        card.findViewById<ImageView>(R.id.iv_skin_thumb).let { thumb ->
            val bitmap = skin.character.stop.frames.firstOrNull()
                ?.let { SkinRepository.loadFrameBitmap(this, skin.skinId, it) }
            if (bitmap != null) thumb.setImageBitmap(bitmap)
            else thumb.setImageResource(R.drawable.frame_placeholder)
        }
        card.findViewById<TextView>(R.id.tv_skin_name).text = skin.name
        bindTag(card, skin.hasCustomTimer)

        val available = SkinAvailabilityChecker.isSkinAvailable(skin, purchasedSkinIds)
        card.findViewById<TextView>(R.id.tv_skin_badge).text =
            if (available) getString(R.string.skin_badge_owned) else getString(R.string.skin_badge_locked)

        // 보유(사용 가능)면 액션 버튼 숨김. 잠긴(유료 미보유) 로컬 테마는 구매(스텁).
        val action = card.findViewById<Button>(R.id.btn_skin_action)
        if (available) {
            action.visibility = View.GONE
        } else {
            action.visibility = View.VISIBLE
            action.isEnabled = true
            action.text = getString(R.string.skin_btn_buy)
            action.setOnClickListener { buyStub() }
        }

        card.setOnClickListener {
            openDetail(skinId = skin.skinId, name = skin.name, isFree = skin.isFree, owned = available)
        }
    }

    private fun bindRemoteCard(card: View, entry: RemoteSkinEntry) {
        RemoteImageLoader.load(
            card.findViewById(R.id.iv_skin_thumb), entry.thumbnailUrl, R.drawable.frame_placeholder
        )
        card.findViewById<TextView>(R.id.tv_skin_name).text = entry.name
        bindTag(card, entry.hasTimer)
        card.findViewById<TextView>(R.id.tv_skin_badge).text =
            if (entry.isFree) getString(R.string.skin_badge_free) else getString(R.string.skin_badge_locked)

        // 카탈로그(미다운로드) 테마: 무료=다운로드, 유료=구매(스텁). 다운로드 중이면 비활성 표시.
        val action = card.findViewById<Button>(R.id.btn_skin_action)
        action.visibility = View.VISIBLE
        when {
            entry.skinId in downloadingIds -> {
                action.text = getString(R.string.skin_btn_downloading)
                action.isEnabled = false
                action.setOnClickListener(null)
            }
            entry.isFree -> {
                action.text = getString(R.string.skin_btn_download)
                action.isEnabled = true
                action.setOnClickListener { startDownload(entry) }
            }
            else -> {
                action.text = getString(R.string.skin_btn_buy)
                action.isEnabled = true
                action.setOnClickListener { buyStub() }
            }
        }

        card.setOnClickListener {
            openDetail(
                skinId = entry.skinId,
                name = entry.name,
                isFree = entry.isFree,
                owned = false,
                zipUrl = entry.zipUrl,
                previewBaseUrl = entry.previewStopUrl.substringBefore("/preview/")
            )
        }
    }

    /** 테마 타입 태그(캐릭터+타이머 vs 캐릭터). */
    private fun bindTag(card: View, hasTimer: Boolean) {
        card.findViewById<TextView>(R.id.tv_skin_tag).apply {
            visibility = View.VISIBLE
            text = getString(
                if (hasTimer) R.string.skin_tag_with_timer else R.string.skin_tag_character_only
            )
        }
    }

    private fun buyStub() {
        // TODO: Google Play Billing 연동. 결제 성공 시 purchasedSkinIds에 skinId 추가 후 다운로드.
        Toast.makeText(this, getString(R.string.store_buy_stub), Toast.LENGTH_SHORT).show()
    }

    private fun startDownload(entry: RemoteSkinEntry) {
        downloadingIds.add(entry.skinId)
        renderList()
        SkinDownloader.download(
            context = this,
            entry = entry,
            onProgress = { },
            onComplete = { success ->
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    downloadingIds.remove(entry.skinId)
                    val msg = if (success)
                        "${entry.name} ${getString(R.string.skin_download_complete)}"
                    else getString(R.string.skin_download_fail)
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                    renderList()
                }
            }
        )
    }

    private fun openDetail(
        skinId: String,
        name: String,
        isFree: Boolean,
        owned: Boolean,
        zipUrl: String? = null,
        previewBaseUrl: String = SkinRepoUrls.ASSET_BASE
    ) {
        startActivity(Intent(this, SkinDetailActivity::class.java).apply {
            putExtra(SkinDetailActivity.EXTRA_SKIN_ID, skinId)
            putExtra(SkinDetailActivity.EXTRA_NAME, name)
            putExtra(SkinDetailActivity.EXTRA_IS_FREE, isFree)
            putExtra(SkinDetailActivity.EXTRA_OWNED, owned)
            putExtra(SkinDetailActivity.EXTRA_ZIP_URL, zipUrl)
            putExtra(SkinDetailActivity.EXTRA_PREVIEW_BASE, previewBaseUrl)
        })
    }
}
