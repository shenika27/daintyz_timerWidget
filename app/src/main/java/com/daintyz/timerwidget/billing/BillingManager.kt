package com.daintyz.timerwidget.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.daintyz.timerwidget.data.TimerPreferences
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Google Play Billing 단일 진입점 (BM: 개별구매 + 평생이용권 / [[billing-bm-decision]]).
 *
 * 권한(entitlement)의 단일 출처는 Play의 [queryPurchasesAsync]다 — 로컬 [TimerPreferences]는 그 결과를
 * 비추는 캐시일 뿐이며, 환불되면 동기화 때 회수된다. **기프트 해금분(giftUnlockedSkinIds)은 출처가 달라
 * 여기서 건드리지 않는다**(동기화 회수 대상 아님).
 *
 * Play 상품 ↔ 앱 스킨 매핑:
 *   - 평생이용권    = 비소비성 1개([LIFETIME_PASS_PRODUCT_ID]) → 보유 시 hasLifetimePass=true
 *   - 유료 스킨     = 각 비소비성 SKU(productId). productId→skinId는 catalog가 알려준다([syncEntitlements]에 맵 주입).
 *
 * 이 클래스는 골격이다 — 실제 구매 흐름(서버 토큰검증 → 보호 zip 다운로드, Phase 4)과 가격 표시(Phase 4)는
 * 여기서 노출하는 [productDetails]/[launchPurchase]/[syncEntitlements]를 호출부가 이어 붙인다.
 */
object BillingManager {

    private const val TAG = "BillingManager"

    /** 평생이용권 Play 인앱상품 ID. Play Console에 1회 등록. non-prestige 유료 테마 일괄 해금. */
    const val LIFETIME_PASS_PRODUCT_ID = "lifetime_pass"

    @Volatile
    private var client: BillingClient? = null

    /**
     * 최근 동기화 때 받은 productId→skinId 매핑. [PurchasesUpdatedListener]가 구매 직후 권한을 기록할 때 쓴다.
     * 맵에 없는 productId는 productId==skinId로 폴백한다.
     */
    @Volatile
    private var productIdToSkinId: Map<String, String> = emptyMap()

    /** 구매 완료 콜백으로 권한이 갱신되면 호출부(UI)가 새로고침할 수 있게 알린다. */
    @Volatile
    private var onEntitlementsChanged: (() -> Unit)? = null

    private val purchasesListener = PurchasesUpdatedListener { result, purchases ->
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            // 구매 직후엔 코루틴 스코프가 없으므로 별도 스레드에서 권한 기록 + acknowledge.
            Thread {
                val ctx = appContext ?: return@Thread
                recordPurchases(ctx, purchases)
                runCatching { acknowledgeBlocking(purchases) }
                onEntitlementsChanged?.invoke()
            }.start()
        }
    }

    @Volatile
    private var appContext: Context? = null

    /**
     * 빌링 클라이언트를 생성·연결한다(이미 연결돼 있으면 무시). 비-블로킹.
     * 앱 시작 시 1회 호출하고, [onEntitlementsChanged]로 구매 반영 알림을 받는다.
     */
    fun start(context: Context, onEntitlementsChanged: (() -> Unit)? = null) {
        this.appContext = context.applicationContext
        this.onEntitlementsChanged = onEntitlementsChanged
        if (client?.isReady == true) return
        val c = BillingClient.newBuilder(context.applicationContext)
            .setListener(purchasesListener)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
            )
            .build()
        client = c
        c.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    Log.w(TAG, "빌링 연결 실패: ${result.responseCode} ${result.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "빌링 서비스 연결 끊김 — 다음 호출 때 재연결 필요")
            }
        })
    }

    /** 연결이 준비될 때까지 대기(이미 준비됐으면 즉시). 끊겼으면 재연결을 시도한다. */
    private suspend fun ensureReady(context: Context): BillingClient? {
        val existing = client
        if (existing?.isReady == true) return existing
        if (existing == null) start(context)
        val c = client ?: return null
        if (c.isReady) return c
        return suspendCancellableCoroutine { cont ->
            c.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    val ok = result.responseCode == BillingClient.BillingResponseCode.OK
                    if (cont.isActive) cont.resume(if (ok) c else null)
                }

                override fun onBillingServiceDisconnected() {
                    if (cont.isActive) cont.resume(null)
                }
            })
        }
    }

    /**
     * Play의 현재 구매내역을 조회해 [TimerPreferences]의 purchasedSkinIds/hasLifetimePass를 갱신한다.
     * 환불·취소로 빠진 항목은 회수된다(Play가 단일 출처). 기프트 해금분은 건드리지 않는다.
     *
     * @param productIdToSkinId catalog에서 만든 productId→skinId 매핑(평생이용권 SKU는 제외).
     */
    suspend fun syncEntitlements(context: Context, productIdToSkinId: Map<String, String>): Boolean {
        this.productIdToSkinId = productIdToSkinId
        val c = ensureReady(context) ?: return false
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val result = c.queryPurchasesAsync(params)
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            Log.w(TAG, "구매내역 조회 실패: ${result.billingResult.responseCode}")
            return false
        }
        val owned = result.purchasesList.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }

        var hasPass = false
        val skinIds = mutableSetOf<String>()
        for (purchase in owned) {
            for (productId in purchase.products) {
                if (productId == LIFETIME_PASS_PRODUCT_ID) hasPass = true
                else skinIds += (productIdToSkinId[productId] ?: productId)
            }
            // 미확인(unacknowledged) 구매는 여기서 확정 처리(3일 내 미확인 시 자동 환불 방지).
            if (!purchase.isAcknowledged) runCatching { acknowledge(c, purchase) }
        }

        // Play가 단일 출처 — 전체 교체(회수 포함). 기프트 집합은 분리돼 있어 영향 없음.
        TimerPreferences.get(context).update {
            it.copy(purchasedSkinIds = skinIds, hasLifetimePass = hasPass)
        }
        onEntitlementsChanged?.invoke()
        return true
    }

    /**
     * 현재 '구매완료' 상태인 productId→purchaseToken 매핑을 돌려준다. 보호 다운로드 요청 시
     * 그 스킨의 토큰([SkinDownloader.downloadFromWorker]의 purchaseToken)과 평생이용권 토큰을 꺼내는 데 쓴다.
     */
    suspend fun ownedTokens(context: Context): Map<String, String> {
        val c = ensureReady(context) ?: return emptyMap()
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val result = c.queryPurchasesAsync(params)
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) return emptyMap()
        val map = mutableMapOf<String, String>()
        for (purchase in result.purchasesList) {
            if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) continue
            for (productId in purchase.products) map[productId] = purchase.purchaseToken
        }
        return map
    }

    /**
     * 주어진 productId들의 상품정보(현지화 가격 등)를 조회한다. 가격 표시는 catalog의 price(원)가 아니라
     * 여기서 얻은 [ProductDetails.getOneTimePurchaseOfferDetails]의 formattedPrice를 써야 정확하다.
     */
    suspend fun productDetails(context: Context, productIds: List<String>): List<ProductDetails> {
        if (productIds.isEmpty()) return emptyList()
        val c = ensureReady(context) ?: return emptyList()
        val products = productIds.distinct().map { id ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(id)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder().setProductList(products).build()
        val result = c.queryProductDetails(params)
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            Log.w(TAG, "상품정보 조회 실패: ${result.billingResult.responseCode}")
            return emptyList()
        }
        return result.productDetailsList.orEmpty()
    }

    /** 평생이용권 상품정보(가격 표시 + 구매 띄우기에 공용). SKU 미등록이면 null. */
    suspend fun lifetimePassDetails(context: Context): ProductDetails? =
        productDetails(context, listOf(LIFETIME_PASS_PRODUCT_ID)).firstOrNull()

    /**
     * 구매 흐름을 띄운다. 결과는 [PurchasesUpdatedListener]로 비동기 도착 → 권한 기록 + acknowledge 후
     * [onEntitlementsChanged] 통지. 구매 완료 후 보호 zip 다운로드는 호출부(Phase 4)가 이어 붙인다.
     */
    fun launchPurchase(activity: Activity, productDetails: ProductDetails): BillingResult? {
        val c = client ?: return null
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .build()
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()
        return c.launchBillingFlow(activity, flowParams)
    }

    // ---- 내부 ----

    private fun recordPurchases(context: Context, purchases: List<Purchase>) {
        val granted = purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        if (granted.isEmpty()) return
        val prefs = TimerPreferences.get(context)
        prefs.update { data ->
            var hasPass = data.hasLifetimePass
            val skinIds = data.purchasedSkinIds.toMutableSet()
            for (purchase in granted) {
                for (productId in purchase.products) {
                    if (productId == LIFETIME_PASS_PRODUCT_ID) hasPass = true
                    else skinIds += (productIdToSkinId[productId] ?: productId)
                }
            }
            data.copy(purchasedSkinIds = skinIds, hasLifetimePass = hasPass)
        }
    }

    private suspend fun acknowledge(client: BillingClient, purchase: Purchase): BillingResult {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        return client.acknowledgePurchase(params)
    }

    /** 리스너 스레드(비-suspend)에서 acknowledge를 동기 호출하기 위한 블로킹 래퍼. */
    private fun acknowledgeBlocking(purchases: List<Purchase>) {
        val c = client ?: return
        for (purchase in purchases) {
            if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) continue
            if (purchase.isAcknowledged) continue
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            c.acknowledgePurchase(params) { result ->
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    Log.w(TAG, "구매 확인(acknowledge) 실패: ${result.responseCode}")
                }
            }
        }
    }
}
