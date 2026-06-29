package com.daintyz.timerwidget.ui.compose

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.daintyz.timerwidget.R
import com.daintyz.timerwidget.billing.BillingManager
import com.daintyz.timerwidget.controller.TimerController
import com.daintyz.timerwidget.data.TimerPreferences
import com.daintyz.timerwidget.model.RemoteSkinEntry
import com.daintyz.timerwidget.model.TimerData
import com.daintyz.timerwidget.model.TimerState
import com.daintyz.timerwidget.skin.RemoteImageLoader
import com.daintyz.timerwidget.skin.SkinRepoUrls
import com.daintyz.timerwidget.skin.SkinAvailabilityChecker
import com.daintyz.timerwidget.skin.SkinDownloader
import com.daintyz.timerwidget.skin.SkinRepository
import com.daintyz.timerwidget.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue

/** prevNN 미리보기 최대 탐침 수 (디자인레포 규칙상 prev01부터 연속, 첫 결번에서 중단). */
private const val MAX_PREVIEWS = 30

internal enum class DetailPrimaryAction {
    APPLY,
    DOWNLOAD,
    BUY,
    NONE
}

internal fun detailPrimaryAction(
    localRenderable: Boolean,
    entitled: Boolean,
    applied: Boolean,
    downloading: Boolean,
    saleExpired: Boolean,
): DetailPrimaryAction = when {
    downloading -> DetailPrimaryAction.NONE
    localRenderable && entitled && !applied -> DetailPrimaryAction.APPLY
    localRenderable && entitled && applied -> DetailPrimaryAction.NONE
    entitled -> DetailPrimaryAction.DOWNLOAD
    saleExpired -> DetailPrimaryAction.NONE
    else -> DetailPrimaryAction.BUY
}

@Composable
private fun stateLabel(state: TimerState): String = stringResource(
    when (state) {
        TimerState.IDLE -> R.string.detail_state_idle
        TimerState.RUNNING -> R.string.detail_state_run
        TimerState.PAUSED -> R.string.detail_state_paused
        TimerState.COMPLETE -> R.string.detail_state_complete
    }
)

/**
 * 테마 상세/미리보기 화면.
 *
 * - 보유: '실제 위젯'을 그대로 띄운 인터랙티브 미리보기(샌드박스). +/- · 시작/일시정지 · 정지를
 *   직접 눌러 카운트다운/애니메이션을 그 자리에서 체험한다(실제 홈 위젯/타이머 상태엔 영향 없음).
 *   하단 캡션 = 현재 상태명, 하단 버튼 = 적용하기/적용 중.
 * - 미보유: character/preview/{id}/prevNN.png 캐러셀(하단 = n/총). 하단 버튼 = 구매하기(가격) / (무료면 다운로드).
 *
 * 다운로드로 보유 전환되면 그 자리에서 인터랙티브 미리보기로 바뀐다.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DetailScreen(
    skinId: String,
    name: String,
    isFree: Boolean,
    price: Int,
    prestige: Boolean,
    productId: String? = null,
    previewBase: String,
    zipUrl: String?,
    saleExpired: Boolean = false,
    showWishlist: Boolean = false,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activity = context.findActivity()

    // 권한(사용 권리) 판정 — SkinAvailabilityChecker와 동일 규칙. 원격(미다운로드) 항목도 이용권/구매/기프트면 true.
    fun computeEntitled(): Boolean {
        val d = TimerPreferences.get(context).load()
        return SkinAvailabilityChecker.isSkinAvailable(
            skinId = skinId,
            isFree = isFree,
            prestige = prestige,
            purchasedSkinIds = d.purchasedSkinIds,
            hasLifetimePass = d.hasLifetimePass,
            giftUnlockedSkinIds = d.giftUnlockedSkinIds,
        )
    }

    // 권한(entitled)과 로컬 렌더 가능 여부(localRenderable)를 분리한다.
    // 다운로드 스킨은 filesDir, 기본 스킨은 assets에 있으므로 SkinRepository 기준으로 판단한다.
    var entitled by remember { mutableStateOf(computeEntitled()) }
    var downloadProgress by remember { mutableStateOf<Int?>(null) }
    var downloadFailed by remember { mutableStateOf(false) }
    var skin by remember { mutableStateOf(SkinRepository.findSkin(context, skinId)) }
    fun computeApplied(entitledNow: Boolean = computeEntitled()): Boolean {
        val d = TimerPreferences.get(context).load()
        return entitledNow &&
            d.selectedCharacterSkinId == skinId &&
            d.selectedTimerSkinId == skinId
    }

    var applied by remember { mutableStateOf(computeApplied()) }
    var wishlisted by remember { mutableStateOf(skinId in TimerPreferences.get(context).loadFavoriteSkinIds()) }
    // 유료 상품의 현지화 가격(formattedPrice). SKU 미등록이면 null → catalog 가격으로 폴백.
    var priceText by remember { mutableStateOf<String?>(null) }
    // 평생이용권 보유 여부 + 가격(하단 '평생이용권 구매' 버튼 노출/표시용).
    var hasPass by remember { mutableStateOf(TimerPreferences.get(context).load().hasLifetimePass) }
    var passPriceText by remember { mutableStateOf<String?>(null) }
    val downloading = downloadProgress != null

    // 화면 복귀(구매 다이얼로그 종료 등)마다 권한/다운로드 상태를 다시 읽어 버튼을 갱신한다.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) {
                val nextEntitled = computeEntitled()
                entitled = nextEntitled
                skin = SkinRepository.findSkin(context, skinId)
                val d = TimerPreferences.get(context).load()
                applied = computeApplied(nextEntitled)
                hasPass = d.hasLifetimePass
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    // 유료 상품 가격 조회(Play 현지화 가격). SKU가 아직 없으면 빈 결과 → 폴백.
    LaunchedEffect(productId, isFree) {
        val pid = productId
        if (!isFree && pid != null) {
            priceText = withContext(Dispatchers.IO) {
                BillingManager.productDetails(context, listOf(pid))
            }.firstOrNull()?.oneTimePurchaseOfferDetails?.formattedPrice
        }
    }

    // 평생이용권 가격 조회(프리스티지·무료 상세에선 버튼 자체가 없어 생략).
    LaunchedEffect(isFree, prestige) {
        if (!isFree && !prestige) {
            passPriceText = withContext(Dispatchers.IO) {
                BillingManager.lifetimePassDetails(context)
            }?.oneTimePurchaseOfferDetails?.formattedPrice
        }
    }

    // 미보유: prevNN을 앞에서부터 순차 탐침(첫 결번에서 중단)해 '존재하는 URL'만 모은다. 표시는 Coil(RemoteImage).
    val previews = remember { mutableStateListOf<String>() }
    var cancelled by remember { mutableStateOf(false) }
    DisposableEffect(skinId, skin != null) {
        cancelled = false
        if (skin == null) {
            previews.clear()
            val candidates = (1..MAX_PREVIEWS).map { SkinRepoUrls.previewCandidates(skinId, it, previewBase) }
            RemoteImageLoader.resolveGallery(
                candidatesPerIndex = candidates,
                isCancelled = { cancelled },
            ) { _, url -> if (!cancelled) previews.add(url) }
        }
        onDispose { cancelled = true }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 상단 바: 뒤로 버튼만(좌측). 보유목록(FilterRow) 자리에 대응.
        // 테마 이름 — 보유목록과 동일 위치/스타일(캐러셀 위 중앙, 24sp).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_detail_back),
                    tint = AppColors.Brown,
                    modifier = Modifier.size(22.dp),
                )
            }
            Text(
                text = name,
                style = AppTypography.headlineSmall,
                color = AppColors.OnSurface,
                fontSize = 24.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp),
            )
            if (showWishlist && !entitled) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable {
                            val next = !wishlisted
                            TimerPreferences.get(context).setFavorite(skinId, next)
                            wishlisted = next
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (wishlisted) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = stringResource(
                            if (wishlisted) R.string.cd_remove_wishlist else R.string.cd_add_wishlist
                        ),
                        tint = if (wishlisted) AppColors.Primary else AppColors.Brown,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        val localSkin = skin
        if (localSkin != null) {
            // 보유: '실제 위젯 작동' 인터랙티브 미리보기(샌드박스). 캐러셀 대신 단일 위젯을 띄운다.
            val previewState = remember(skinId) { mutableStateOf(initialPreviewData(context, skinId)) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .aspectRatio(10f / 9f),
                contentAlignment = Alignment.Center,
            ) {
                DetailCard {
                    InteractiveWidgetPreview(
                        // 캐릭터만이 아니라 '실제 위젯 전체'(타이머+캐릭터)를 같은 RemoteViews 코드로 렌더한다.
                        // 타이머 위치(위/아래)는 현재 설정(layoutMode)을 따르고, 폰트/버튼도 위젯과 100% 일치한다.
                        state = previewState,
                        // 실제 2x2 위젯과 같은 정사각 비율로 렌더(미리보기=위젯 종횡비 일치).
                        modifier = Modifier.fillMaxHeight().aspectRatio(1f),
                    )
                }
            }
            // 캡션 = 미리보기 위젯의 현재 상태명(조작에 따라 실시간 갱신).
            Text(
                text = stateLabel(previewState.value.state),
                color = AppColors.OnSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 14.dp),
            )
        } else {
            // 미보유: prevNN 홍보 이미지 커버플로우 캐러셀. Coil 렌더라 prevNN이 GIF/애니 WebP면 그대로 재생된다.
            val pageCount = previews.size.coerceAtLeast(1)
            val pagerState = rememberPagerState(pageCount = { pageCount })
            HorizontalPager(
                state = pagerState,
                contentPadding = PaddingValues(horizontal = 52.dp),
                pageSpacing = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .aspectRatio(10f / 9f),
            ) { page ->
                CoverFlowBox(pagerState = pagerState, page = page) {
                    DetailCard {
                        val url = previews.getOrNull(page)
                        if (url != null) {
                            RemoteImage(
                                url = url,
                                contentDescription = name,
                                modifier = Modifier.fillMaxSize().padding(14.dp),
                                contentScale = ContentScale.Fit,
                            )
                        } else {
                            // 아직 탐침 전/빈 목록 → placeholder.
                            BitmapImage(
                                bitmap = null,
                                contentDescription = name,
                                modifier = Modifier.fillMaxSize().padding(14.dp),
                                contentScale = ContentScale.Fit,
                            )
                        }
                    }
                }
            }
            val caption = if (previews.isEmpty()) {
                stringResource(R.string.detail_preview_empty)
            } else {
                stringResource(R.string.vault_position, pagerState.currentPage + 1, previews.size)
            }
            Text(
                text = caption,
                color = AppColors.Brown,
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.padding(top = 14.dp),
            )
        }

        Spacer(Modifier.weight(1f))

        // 하단 액션 버튼: 다운로드됨→적용 / 보유O 미다운로드→다운로드(무료=공개·유료=Worker) / 미보유 유료→구매.
        val priceLabel = priceText
            ?: if (isFree || price <= 0) stringResource(R.string.skin_btn_download)
            else stringResource(R.string.detail_buy_price, "%,d원".format(price))

        // 보유했지만 파일이 아직 없는 경우의 다운로드. 무료=공개 CDN, 유료=결제검증 Worker(영수증 토큰 첨부).
        fun startEntitledDownload() {
            downloadProgress = -1
            downloadFailed = false
            if (isFree) {
                startDownload(
                    context, skinId, name, isFree, price, prestige, previewBase, zipUrl,
                    onStart = {},
                    onProgress = { percent -> downloadProgress = percent },
                    onDone = { success ->
                        downloadProgress = null
                        downloadFailed = !success
                        if (success) { skin = SkinRepository.findSkin(context, skinId) }
                    },
                )
            } else {
                scope.launch {
                    val tokens = withContext(Dispatchers.IO) { BillingManager.ownedTokens(context) }
                    SkinDownloader.downloadFromWorker(
                        context = context.applicationContext,
                        skinId = skinId,
                        purchaseToken = productId?.let { tokens[it] },
                        passToken = tokens[BillingManager.LIFETIME_PASS_PRODUCT_ID],
                        onProgress = { percent -> downloadProgress = percent },
                        onComplete = { success ->
                            downloadProgress = null
                            downloadFailed = !success
                            if (success) { skin = SkinRepository.findSkin(context, skinId) }
                            Toast.makeText(
                                context,
                                if (success) "$name ${context.getString(R.string.skin_download_complete)}"
                                else context.getString(R.string.skin_download_fail),
                                Toast.LENGTH_SHORT,
                            ).show()
                        },
                    )
                }
            }
        }

        // 미보유 유료 → 구매. 상품정보(SKU)가 아직 없으면 '준비 중' 안내로 폴백.
        fun startPurchase() {
            val act = activity
            val pid = productId
            if (act == null || pid == null) {
                Toast.makeText(context, context.getString(R.string.store_buy_stub), Toast.LENGTH_SHORT).show()
                return
            }
            scope.launch {
                val details = withContext(Dispatchers.IO) {
                    BillingManager.productDetails(context, listOf(pid))
                }.firstOrNull()
                if (details == null) {
                    Toast.makeText(context, context.getString(R.string.store_buy_stub), Toast.LENGTH_SHORT).show()
                } else {
                    // 구매 결과는 비동기 도착 → ON_RESUME에서 entitled/localRenderable이 갱신된다.
                    BillingManager.launchPurchase(act, details)
                }
            }
        }

        // 평생이용권 구매(프리스티지 제외 유료 테마 일괄 해금). 결과는 ON_RESUME에서 반영.
        fun startPassPurchase() {
            val act = activity
            if (act == null) {
                Toast.makeText(context, context.getString(R.string.store_buy_stub), Toast.LENGTH_SHORT).show()
                return
            }
            scope.launch {
                val details = withContext(Dispatchers.IO) { BillingManager.lifetimePassDetails(context) }
                if (details == null) {
                    Toast.makeText(context, context.getString(R.string.store_buy_stub), Toast.LENGTH_SHORT).show()
                } else {
                    BillingManager.launchPurchase(act, details)
                }
            }
        }

        Button(
            onClick = {
                val localRenderable = skin != null
                when (detailPrimaryAction(localRenderable, entitled, applied, downloading, saleExpired)) {
                    DetailPrimaryAction.APPLY -> {
                        TimerController.selectCharacterSkin(context, skinId)
                        TimerController.selectTimerSkin(context, skinId)
                        applied = true
                        Toast.makeText(
                            context, context.getString(R.string.vault_applied_toast, name),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    DetailPrimaryAction.DOWNLOAD -> startEntitledDownload()
                    DetailPrimaryAction.BUY -> startPurchase()
                    DetailPrimaryAction.NONE -> {}
                }
            },
            enabled = detailPrimaryAction(skin != null, entitled, applied, downloading, saleExpired) != DetailPrimaryAction.NONE,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AppColors.Primary,
                contentColor = AppColors.OnPrimary,
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp).height(52.dp),
        ) {
            val label = when {
                downloading -> stringResource(R.string.skin_btn_downloading)
                skin != null && entitled -> stringResource(if (applied) R.string.detail_applied else R.string.detail_apply)
                downloadFailed && entitled -> stringResource(R.string.skin_btn_retry)
                entitled -> stringResource(R.string.skin_btn_download)
                saleExpired -> stringResource(R.string.sale_expired)
                else -> priceLabel
            }
            DownloadButtonContent(label = label, progress = downloadProgress)
        }

        // '구매하기' 하단의 평생이용권 구매 버튼.
        // 프리스티지는 이용권으로 해금 안 되므로 노출 제외. 무료/이미 보유/이미 이용권 보유 시에도 숨김.
        if (!prestige && !isFree && !entitled && !hasPass) {
            val passLabel = stringResource(R.string.lifetime_pass_buy) +
                (passPriceText?.let { " ($it)" } ?: "")
            Button(
                onClick = { startPassPurchase() },
                enabled = !downloading,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.Surface,
                    contentColor = AppColors.Primary,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 8.dp)
                    .height(48.dp),
            ) {
                Text(passLabel, fontSize = 15.sp)
            }
        }

        Spacer(Modifier.height(48.dp))
    }
}

/** 커버플로우 스케일/알파 (포커스에서 멀어질수록 축소·반투명). VaultScreen과 동일 곡선. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CoverFlowBox(
    pagerState: androidx.compose.foundation.pager.PagerState,
    page: Int,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val offset = ((pagerState.currentPage - page) +
                    pagerState.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                val s = lerp(1f, 0.86f, offset)
                scaleX = s
                scaleY = s
                alpha = lerp(1f, 0.62f, offset)
            },
    ) { content() }
}

/** 미리보기 카드 컨테이너 — 배경 투명(위젯이 홈화면처럼 비쳐 보이게). */
@Composable
private fun DetailCard(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/**
 * '실제 위젯'(타이머+캐릭터)을 RemoteViews로 라이브 렌더하고, 위젯 버튼/시간영역에 **미리보기 전용 로컬
 * 클릭 리스너**를 직접 달아 그 자리에서 조작 가능한 인터랙티브 미리보기(샌드박스)를 만든다.
 *
 * - 위젯과 동일한 [WidgetUpdater.buildRemoteViews] 경로(forPreview=true)를 그대로 써서 폰트/버튼/타이머 위치가 위젯과 100% 일치.
 * - forPreview=true라 실제 PendingIntent가 바인딩되지 않으므로, [bindSandboxClicks]가 단 로컬 리스너만 동작 → 실제 타이머/홈 위젯엔 영향 없음.
 * - 250ms마다 [android.widget.RemoteViews.reapply]로 제자리 갱신(뷰 재생성 없이 깜빡임 방지) → 카운트다운 + 캐릭터 애니메이션.
 * - RUNNING 만료 시 미리보기 안에서 COMPLETE로 전환된다(완료 애니메이션 확인 가능).
 */
@Composable
private fun InteractiveWidgetPreview(
    state: androidx.compose.runtime.MutableState<TimerData>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appCtx = context.applicationContext
    val container = remember { FrameLayout(context) }

    AndroidView(factory = { container }, modifier = modifier)

    LaunchedEffect(container) {
        fun build() = WidgetUpdater.buildRemoteViews(
            appCtx, state.value, SystemClock.elapsedRealtime(), forPreview = true,
        )

        // 최초 1회 apply 후 로컬 클릭 리스너 바인딩(이후 reapply는 리스너를 지우지 않는다).
        val root = build().apply(appCtx, container)
        container.removeAllViews()
        root.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        )
        container.addView(root)
        bindSandboxClicks(root, state)

        while (true) {
            val now = SystemClock.elapsedRealtime()
            val d = state.value
            // 카운트다운이 0에 도달하면 미리보기 안에서 완료로 전환(완료 애니메이션 1회 재생).
            if (d.state == TimerState.RUNNING && now >= d.targetEndElapsed) {
                state.value = d.copy(
                    state = TimerState.COMPLETE,
                    stateEnteredElapsed = now,
                    targetEndElapsed = 0L,
                    remainingMillisAtPause = 0L,
                )
            }
            build().reapply(appCtx, root)
            delay(100)
        }
    }
}

/**
 * 미리보기 위젯의 버튼/시간영역에 '미리보기 내부 전용' 로컬 클릭 리스너를 단다.
 * 각 동작은 [TimerController]와 동일한 규칙으로 로컬 [state]만 전이시킨다(실제 타이머 prefs는 건드리지 않음).
 */
private fun bindSandboxClicks(root: View, state: androidx.compose.runtime.MutableState<TimerData>) {
    fun now() = SystemClock.elapsedRealtime()
    root.findViewById<View?>(R.id.btn_minus)?.setOnClickListener { state.value = previewStep(state.value, -1) }
    root.findViewById<View?>(R.id.btn_plus)?.setOnClickListener { state.value = previewStep(state.value, +1) }
    root.findViewById<View?>(R.id.btn_start_pause)?.setOnClickListener { state.value = previewToggle(state.value, now()) }
    root.findViewById<View?>(R.id.btn_stop_reset)?.setOnClickListener { state.value = previewStop(state.value, now()) }
    root.findViewById<View?>(R.id.time_area)?.setOnClickListener {
        val d = state.value
        state.value = if (d.state == TimerState.COMPLETE) previewReset(d, now()) else previewToggle(d, now())
    }
}

// ---- 미리보기 로컬 상태 전이 (TimerController 규칙의 미리보기판) ----

/**
 * 정지 상태에서만 설정 시간 ±step (TimerController 규칙의 미리보기판).
 * 1분 미만은 10초 단위. 1분 이상은 step(초) 배수 격자로 스냅. 10초~999분으로 제한.
 */
private fun previewStep(d: TimerData, steps: Int): TimerData {
    if (d.state != TimerState.IDLE) return d
    val step = d.stepSeconds.coerceAtLeast(5)
    val cur = d.lastSetSeconds
    val next = when {
        steps > 0 && cur < 60 -> cur + 10
        steps < 0 && cur <= 60 -> cur - 10
        steps > 0 -> ((cur / step) + 1) * step // 다음 배수로 올림 스냅
        else -> (((cur - 1) / step) * step).let { if (it < 60) 60 else it } // 이전 배수, 1분 미만은 1:00에 멈춤
    }
    return d.copy(lastSetSeconds = next.coerceIn(10, 999 * 60))
}

/**
 * 미리보기 데모 재생 길이(고정). 미리보기는 '실제 분 단위 타이머'가 아니라 동작 확인용이므로,
 * 설정 시간과 무관하게 재생 즉시 5초로 카운트다운한다(진행→완료를 빠르게 확인).
 */
private const val PREVIEW_RUN_MS = 5_000L

/** 시작/일시정지/재개 토글. */
private fun previewToggle(d: TimerData, now: Long): TimerData = when (d.state) {
    TimerState.IDLE -> d.copy(
        state = TimerState.RUNNING,
        stateEnteredElapsed = now,
        targetEndElapsed = now + PREVIEW_RUN_MS,
        totalMillis = PREVIEW_RUN_MS,
        remainingMillisAtPause = 0L,
    )
    TimerState.RUNNING -> d.copy(
        state = TimerState.PAUSED,
        stateEnteredElapsed = now,
        remainingMillisAtPause = (d.targetEndElapsed - now).coerceAtLeast(0L),
    )
    TimerState.PAUSED -> d.copy(
        state = TimerState.RUNNING,
        stateEnteredElapsed = now,
        targetEndElapsed = now + d.remainingMillisAtPause,
        remainingMillisAtPause = 0L,
    )
    TimerState.COMPLETE -> d
}

/** 진행/일시정지 → 정지(설정 시간 유지). */
private fun previewStop(d: TimerData, now: Long): TimerData = d.copy(
    state = TimerState.IDLE,
    stateEnteredElapsed = now,
    targetEndElapsed = 0L,
    remainingMillisAtPause = 0L,
    totalMillis = 0L,
)

/** 완료 → 정지 복귀. */
private fun previewReset(d: TimerData, now: Long): TimerData =
    d.copy(state = TimerState.IDLE, stateEnteredElapsed = now)

/**
 * 현재 적용중 설정(layoutMode/설정시간 등)을 베이스로, 미리보는 캐릭터만 [skinId]로 치환한 IDLE 초기 스냅샷.
 * 타이머 스킨은 이 스킨이 timer 블록을 가질 때만 함께 치환(없으면 현재 타이머 유지).
 * stateEnteredElapsed=now라 미리보기를 열면 stop(중지) 애니메이션이 1회 재생된 뒤 마지막 프레임에서 멈춘다.
 */
private fun initialPreviewData(context: Context, skinId: String): TimerData {
    val base = TimerPreferences.get(context).load()
    val skin = SkinRepository.findSkin(context, skinId)
    return base.copy(
        state = TimerState.IDLE,
        stateEnteredElapsed = SystemClock.elapsedRealtime(),
        targetEndElapsed = 0L,
        remainingMillisAtPause = 0L,
        totalMillis = 0L,
        lastSetSeconds = 10,
        selectedCharacterSkinId = skinId,
        selectedTimerSkinId = if (skin?.timer != null) skinId else base.selectedTimerSkinId,
    )
}

/** 무료/미보유 테마 다운로드 (SkinDownloader 경유, 상점과 동일 경로). */
private fun startDownload(
    context: android.content.Context,
    skinId: String,
    name: String,
    isFree: Boolean,
    price: Int,
    prestige: Boolean,
    previewBase: String,
    zipUrl: String?,
    onStart: () -> Unit,
    onProgress: (Int) -> Unit,
    onDone: (Boolean) -> Unit,
) {
    onStart()
    val entry = RemoteSkinEntry(
        skinId = skinId,
        name = name,
        price = price,
        isFree = isFree,
        prestige = prestige,
        zipUrl = zipUrl ?: "$previewBase/character/zip/$skinId.zip",
        thumbnailUrl = SkinRepoUrls.themeThumb(skinId, previewBase),
        baseUrl = previewBase,
    )
    SkinDownloader.download(
        context = context.applicationContext,
        entry = entry,
        onProgress = onProgress,
        onComplete = { success ->
            val msg = if (success)
                "$name ${context.getString(R.string.skin_download_complete)}"
            else context.getString(R.string.skin_download_fail)
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            onDone(success)
        },
    )
}

/** Compose의 ContextWrapper 체인에서 호스트 Activity를 찾는다(launchBillingFlow에 필요). 없으면 null. */
internal fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/** 다운로드 중에는 버튼 안에서 실제 수신률(또는 전체 크기 미상 상태)을 보여준다. */
@Composable
fun DownloadButtonContent(label: String, progress: Int?) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 16.sp)
        if (progress != null) {
            if (progress >= 0) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                    color = AppColors.OnPrimary,
                    trackColor = AppColors.Primary,
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                    color = AppColors.OnPrimary,
                    trackColor = AppColors.Primary,
                )
            }
        }
    }
}
