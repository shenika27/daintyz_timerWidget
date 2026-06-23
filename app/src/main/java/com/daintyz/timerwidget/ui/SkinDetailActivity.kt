package com.daintyz.timerwidget.ui

import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.daintyz.timerwidget.R
import com.daintyz.timerwidget.data.TimerPreferences
import com.daintyz.timerwidget.model.RemoteSkinEntry
import com.daintyz.timerwidget.skin.RemoteImageLoader
import com.daintyz.timerwidget.skin.SkinAvailabilityChecker
import com.daintyz.timerwidget.skin.SkinRepoUrls
import com.daintyz.timerwidget.skin.SkinDownloader
import com.daintyz.timerwidget.skin.SkinRepository

/**
 * 테마 상세/미리보기 화면.
 *
 * preview/{id}/prevNN.png를 가로 스와이프 갤러리로 보여준다(prev01부터 [MAX_PREVIEWS]까지 탐침, 첫 결번에서 중단).
 * 하단 액션은 상태에 따라: 보유 중 → 적용하러 가기 / 무료 미보유 → 다운로드 / 유료 미보유 → 구매(준비 중).
 */
class SkinDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SKIN_ID = "skin_id"
        const val EXTRA_NAME = "name"
        const val EXTRA_IS_FREE = "is_free"
        const val EXTRA_OWNED = "owned"
        const val EXTRA_ZIP_URL = "zip_url"
        /** 미리보기 에셋 베이스 URL(catalog baseUrl 또는 jsDelivr ASSET_BASE). prevNN/zip 유추에 사용. */
        const val EXTRA_PREVIEW_BASE = "preview_base"

        private const val MAX_PREVIEWS = 30
    }

    private lateinit var skinId: String
    private lateinit var skinName: String
    private var isFree = true
    private var zipUrl: String? = null
    private lateinit var previewBase: String

    private var downloading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_skin_detail)
        title = getString(R.string.store_detail_title)

        skinId = intent.getStringExtra(EXTRA_SKIN_ID) ?: run { finish(); return }
        skinName = intent.getStringExtra(EXTRA_NAME) ?: skinId
        isFree = intent.getBooleanExtra(EXTRA_IS_FREE, true)
        zipUrl = intent.getStringExtra(EXTRA_ZIP_URL)
        previewBase = intent.getStringExtra(EXTRA_PREVIEW_BASE) ?: SkinRepoUrls.ASSET_BASE

        findViewById<TextView>(R.id.tv_detail_name).text = skinName

        loadGallery()
        renderAction()
    }

    /** prev01..MAX_PREVIEWS를 순차 탐침해 발견되는 만큼 갤러리에 추가. 첫 결번에서 멈춘다. */
    private fun loadGallery() {
        val gallery = findViewById<LinearLayout>(R.id.preview_gallery)
        val empty = findViewById<TextView>(R.id.tv_preview_empty)
        gallery.removeAllViews()
        empty.visibility = android.view.View.GONE

        val urls = (1..MAX_PREVIEWS).map { SkinRepoUrls.preview(skinId, it, previewBase) }
        RemoteImageLoader.loadGallery(
            urls = urls,
            isCancelled = { isFinishing || isDestroyed },
        ) { _, bitmap ->
            val iv = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(240), ViewGroup.LayoutParams.MATCH_PARENT)
                    .also { it.marginEnd = dp(8) }
                scaleType = ImageView.ScaleType.FIT_CENTER
                adjustViewBounds = true
                contentDescription = getString(R.string.store_detail_title)
                setImageBitmap(bitmap)
            }
            gallery.addView(iv)
        }
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    /** 보유 여부를 매번 재평가 (다운로드 후 갱신 반영). */
    private fun isOwned(): Boolean {
        val skin = SkinRepository.findSkin(this, skinId) ?: return false
        val purchased = TimerPreferences.get(this).load().purchasedSkinIds
        return SkinAvailabilityChecker.isSkinAvailable(skin, purchased)
    }

    private fun renderAction() {
        val price = findViewById<TextView>(R.id.tv_detail_price)
        val action = findViewById<Button>(R.id.btn_detail_action)
        val owned = isOwned()

        price.text = when {
            owned -> getString(R.string.skin_badge_owned)
            isFree -> getString(R.string.skin_badge_free)
            else -> getString(R.string.skin_badge_locked)
        }

        when {
            downloading -> {
                action.text = getString(R.string.skin_btn_downloading)
                action.isEnabled = false
                action.setOnClickListener(null)
            }
            owned -> {
                action.text = getString(R.string.store_go_apply)
                action.isEnabled = true
                action.setOnClickListener {
                    startActivity(Intent(this, SkinSelectActivity::class.java))
                }
            }
            isFree -> {
                action.text = getString(R.string.skin_btn_download)
                action.isEnabled = true
                action.setOnClickListener { startDownload() }
            }
            else -> {
                action.text = getString(R.string.skin_btn_buy)
                action.isEnabled = true
                action.setOnClickListener {
                    // TODO: Google Play Billing 연동. 결제 성공 시 purchasedSkinIds에 skinId 추가 후 다운로드.
                    Toast.makeText(this, getString(R.string.store_buy_stub), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun startDownload() {
        downloading = true
        renderAction()
        val entry = RemoteSkinEntry(
            skinId = skinId,
            name = skinName,
            isFree = isFree,
            zipUrl = zipUrl ?: "$previewBase/character_zip/$skinId.zip",
            thumbnailUrl = SkinRepoUrls.themeThumb(skinId, previewBase),
            previewStopUrl = SkinRepoUrls.preview(skinId, 1, previewBase),
            previewRunningUrl = SkinRepoUrls.preview(skinId, 2, previewBase)
        )
        SkinDownloader.download(
            context = this,
            entry = entry,
            onProgress = { },
            onComplete = { success ->
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    downloading = false
                    val msg = if (success)
                        "$skinName ${getString(R.string.skin_download_complete)}"
                    else getString(R.string.skin_download_fail)
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                    renderAction()
                }
            }
        )
    }
}
