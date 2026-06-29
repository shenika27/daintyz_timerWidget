package com.daintyz.timerwidget.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import com.daintyz.timerwidget.skin.SkinRepoUrls
import com.daintyz.timerwidget.ui.compose.AdaptiveContent
import com.daintyz.timerwidget.ui.compose.AppTheme
import com.daintyz.timerwidget.ui.compose.DetailScreen

/**
 * 테마 상세/미리보기 화면 (Compose).
 *
 * 보유면 캐릭터 상태(정지/진행 중/중단/완료) 스와이프 + 적용하기, 미보유면 prevNN 스와이프 + 구매/다운로드.
 * 화면 구성은 [DetailScreen]에 위임한다.
 */
class DetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SKIN_ID = "skin_id"
        const val EXTRA_NAME = "name"
        const val EXTRA_OWNED = "owned"
        const val EXTRA_IS_FREE = "is_free"
        const val EXTRA_PRICE = "price"
        const val EXTRA_PRESTIGE = "prestige"
        const val EXTRA_PRODUCT_ID = "product_id"
        const val EXTRA_ZIP_URL = "zip_url"
        const val EXTRA_PREVIEW_BASE = "preview_base"
        const val EXTRA_SALE_EXPIRED = "sale_expired"
        const val EXTRA_OPENED_FROM_STORE = "opened_from_store"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val skinId = intent.getStringExtra(EXTRA_SKIN_ID) ?: run { finish(); return }
        val name = intent.getStringExtra(EXTRA_NAME) ?: skinId
        val isFree = intent.getBooleanExtra(EXTRA_IS_FREE, true)
        val price = intent.getIntExtra(EXTRA_PRICE, 0)
        val prestige = intent.getBooleanExtra(EXTRA_PRESTIGE, false)
        val productId = intent.getStringExtra(EXTRA_PRODUCT_ID)
        val zipUrl = intent.getStringExtra(EXTRA_ZIP_URL)
        val previewBase = intent.getStringExtra(EXTRA_PREVIEW_BASE) ?: SkinRepoUrls.ASSET_BASE
        val saleExpired = intent.getBooleanExtra(EXTRA_SALE_EXPIRED, false)
        val openedFromStore = intent.getBooleanExtra(EXTRA_OPENED_FROM_STORE, false)

        setContent {
            AppTheme {
                AdaptiveContent {
                    DetailScreen(
                        skinId = skinId,
                        name = name,
                        isFree = isFree,
                        price = price,
                        prestige = prestige,
                        productId = productId,
                        previewBase = previewBase,
                        zipUrl = zipUrl,
                        saleExpired = saleExpired,
                        showWishlist = openedFromStore,
                        onBack = { finish() },
                    )
                }
            }
        }
    }
}
