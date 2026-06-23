package com.daintyz.timerwidget.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
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
 * 상단 [정지][진행중] 토글로 preview/prev01·prev02 미리보기를 전환한다.
 * 하단 액션은 상태에 따라: 보유 중 → 적용하러 가기 / 무료 미보유 → 다운로드 / 유료 미보유 → 구매(준비 중).
 */
class SkinDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SKIN_ID = "skin_id"
        const val EXTRA_NAME = "name"
        const val EXTRA_IS_FREE = "is_free"
        const val EXTRA_OWNED = "owned"
        const val EXTRA_ZIP_URL = "zip_url"
        const val EXTRA_PREVIEW_STOP = "preview_stop"
        const val EXTRA_PREVIEW_RUNNING = "preview_running"
    }

    private enum class PreviewState { STOP, RUNNING }

    private lateinit var skinId: String
    private lateinit var skinName: String
    private var isFree = true
    private var zipUrl: String? = null
    private lateinit var previewStopUrl: String
    private lateinit var previewRunningUrl: String

    private var preview = PreviewState.STOP
    private var downloading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_skin_detail)
        title = getString(R.string.store_detail_title)

        skinId = intent.getStringExtra(EXTRA_SKIN_ID) ?: run { finish(); return }
        skinName = intent.getStringExtra(EXTRA_NAME) ?: skinId
        isFree = intent.getBooleanExtra(EXTRA_IS_FREE, true)
        zipUrl = intent.getStringExtra(EXTRA_ZIP_URL)
        previewStopUrl = intent.getStringExtra(EXTRA_PREVIEW_STOP) ?: SkinRepoUrls.previewStop(skinId)
        previewRunningUrl = intent.getStringExtra(EXTRA_PREVIEW_RUNNING) ?: SkinRepoUrls.previewRunning(skinId)

        findViewById<TextView>(R.id.tv_detail_name).text = skinName
        findViewById<Button>(R.id.btn_state_stop).setOnClickListener { showPreview(PreviewState.STOP) }
        findViewById<Button>(R.id.btn_state_running).setOnClickListener { showPreview(PreviewState.RUNNING) }

        showPreview(PreviewState.STOP)
        renderAction()
    }

    private fun showPreview(state: PreviewState) {
        preview = state
        val url = if (state == PreviewState.STOP) previewStopUrl else previewRunningUrl
        RemoteImageLoader.load(
            findViewById<ImageView>(R.id.iv_preview), url, R.drawable.frame_placeholder
        )
        // 선택된 토글 강조 (활성=enabled false로 눌린 느낌)
        findViewById<Button>(R.id.btn_state_stop).isEnabled = state != PreviewState.STOP
        findViewById<Button>(R.id.btn_state_running).isEnabled = state != PreviewState.RUNNING
    }

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
            zipUrl = zipUrl ?: "${SkinRepoUrls.ASSET_BASE}/character_zip/$skinId.zip",
            thumbnailUrl = SkinRepoUrls.characterThumb(skinId),
            timerThumbnailUrl = SkinRepoUrls.timerThumb(skinId),
            previewStopUrl = previewStopUrl,
            previewRunningUrl = previewRunningUrl
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
