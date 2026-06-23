package com.daintyz.timerwidget.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_skin_store)
        title = getString(R.string.store_title)
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

        // 로컬(보유/내장) 테마
        for (skin in localSkins) {
            val card = inflater.inflate(R.layout.item_skin, container, false)
            bindLocalCard(card, skin, data.purchasedSkinIds)
            container.addView(card)
        }
        // 카탈로그에만 있는(미다운로드) 테마
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

        val available = SkinAvailabilityChecker.isSkinAvailable(skin, purchasedSkinIds)
        card.findViewById<TextView>(R.id.tv_skin_badge).text =
            if (available) getString(R.string.skin_badge_owned) else getString(R.string.skin_badge_locked)
        card.findViewById<Button>(R.id.btn_skin_action).visibility = View.GONE

        card.setOnClickListener {
            openDetail(
                skinId = skin.skinId,
                name = skin.name,
                isFree = skin.isFree,
                owned = available
            )
        }
    }

    private fun bindRemoteCard(card: View, entry: RemoteSkinEntry) {
        RemoteImageLoader.load(
            card.findViewById(R.id.iv_skin_thumb), entry.thumbnailUrl, R.drawable.frame_placeholder
        )
        card.findViewById<TextView>(R.id.tv_skin_name).text = entry.name
        card.findViewById<TextView>(R.id.tv_skin_badge).text =
            if (entry.isFree) getString(R.string.skin_badge_free) else getString(R.string.skin_badge_locked)
        card.findViewById<Button>(R.id.btn_skin_action).visibility = View.GONE

        card.setOnClickListener {
            openDetail(
                skinId = entry.skinId,
                name = entry.name,
                isFree = entry.isFree,
                owned = false,
                zipUrl = entry.zipUrl,
                previewStopUrl = entry.previewStopUrl,
                previewRunningUrl = entry.previewRunningUrl
            )
        }
    }

    private fun openDetail(
        skinId: String,
        name: String,
        isFree: Boolean,
        owned: Boolean,
        zipUrl: String? = null,
        previewStopUrl: String = SkinRepoUrls.previewStop(skinId),
        previewRunningUrl: String = SkinRepoUrls.previewRunning(skinId)
    ) {
        startActivity(Intent(this, SkinDetailActivity::class.java).apply {
            putExtra(SkinDetailActivity.EXTRA_SKIN_ID, skinId)
            putExtra(SkinDetailActivity.EXTRA_NAME, name)
            putExtra(SkinDetailActivity.EXTRA_IS_FREE, isFree)
            putExtra(SkinDetailActivity.EXTRA_OWNED, owned)
            putExtra(SkinDetailActivity.EXTRA_ZIP_URL, zipUrl)
            putExtra(SkinDetailActivity.EXTRA_PREVIEW_STOP, previewStopUrl)
            putExtra(SkinDetailActivity.EXTRA_PREVIEW_RUNNING, previewRunningUrl)
        })
    }
}
