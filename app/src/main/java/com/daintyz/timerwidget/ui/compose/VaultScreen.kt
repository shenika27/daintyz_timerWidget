package com.daintyz.timerwidget.ui.compose

import android.graphics.Bitmap
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.daintyz.timerwidget.R
import com.daintyz.timerwidget.controller.TimerController
import com.daintyz.timerwidget.data.TimerPreferences
import com.daintyz.timerwidget.model.RemoteSkinEntry
import com.daintyz.timerwidget.model.TimerState
import com.daintyz.timerwidget.skin.FrameAnimationController
import com.daintyz.timerwidget.skin.SkinAvailabilityChecker
import com.daintyz.timerwidget.skin.SkinDownloader
import com.daintyz.timerwidget.skin.SkinRepository
import com.daintyz.timerwidget.ui.SaleStatus
import com.daintyz.timerwidget.ui.VaultItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue

private const val CATALOG_URL =
    "https://raw.githubusercontent.com/shenika27/daintyz_timer_characterList/main/catalog.json"

/**
 * 캐러셀 카드에서 캐릭터(불투명 영역)가 차지할 목표 비율(카드 변 대비). '먹' 정도의 여유 있는 크기.
 * 캐릭터가 이 비율보다 더 꽉 찬 스킨(예: cha01 '런닝하는 홉빵')만 이 크기로 줄이고,
 * 이미 더 작은(여백 많은) 스킨은 건드리지 않는다(스케일 1로 클램프). 값↑ = 더 크게.
 */
private const val CARD_TARGET_FILL = 0.80f

/**
 * 보유(테마) 화면 — 보유/미보유 통합 커버플로우 캐러셀 (Compose 재작성).
 * 정렬: 즐겨찾기 → 보유 → 미보유. 적용=캐릭터+타이머 동시. 미리보기=상세 화면.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun VaultScreen(
    onOpenDetail: (VaultItem) -> Unit,
    focusSkinId: String? = null,
    onFocusConsumed: () -> Unit = {},
) {
    val context = LocalContext.current

    // 진입 시점의 보유/적용 상태를 prefs에서 바로 읽어 초기화한다(탭 전환 진입 시에도 정렬이 흔들리지 않게).
    val initialData = remember { TimerPreferences.get(context).load() }
    var localSkins by remember { mutableStateOf(SkinRepository.loadAllSkins(context)) }
    var catalog by remember { mutableStateOf(emptyList<RemoteSkinEntry>()) }
    var favoriteIds by remember { mutableStateOf(TimerPreferences.get(context).loadFavoriteSkinIds()) }
    // 별 토글은 카드만 즉시 바꾸고, 캐러셀 정렬은 의도적인 화면 재진입/필터 전환 때만 갱신한다.
    var orderingFavoriteIds by remember { mutableStateOf(favoriteIds) }
    var purchased by remember { mutableStateOf(initialData.purchasedSkinIds) }
    var hasPass by remember { mutableStateOf(initialData.hasLifetimePass) }
    var appliedId by remember {
        mutableStateOf(
            if (initialData.selectedCharacterSkinId == initialData.selectedTimerSkinId)
                initialData.selectedCharacterSkinId else null
        )
    }

    var ownedOnly by rememberSaveable { mutableStateOf(false) }
    var favOnly by rememberSaveable { mutableStateOf(false) }
    val downloadProgresses = remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    val failedDownloadIds = remember { mutableStateOf(emptySet<String>()) }

    fun reload() {
        val prefs = TimerPreferences.get(context)
        favoriteIds = prefs.loadFavoriteSkinIds()
        orderingFavoriteIds = favoriteIds
        val data = prefs.load()
        purchased = data.purchasedSkinIds
        hasPass = data.hasLifetimePass
        localSkins = SkinRepository.loadAllSkins(context)
        appliedId = if (data.selectedCharacterSkinId == data.selectedTimerSkinId)
            data.selectedCharacterSkinId else null
    }

    // 화면 진입/복귀(상세에서 돌아옴)마다 재조회 → 즐겨찾기/보유 상태 반영.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) reload()
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    LaunchedEffect(Unit) {
        val entries = withContext(Dispatchers.IO) {
            runCatching { SkinDownloader.fetchCatalog(CATALOG_URL) }.getOrNull()
        }
        if (entries != null) catalog = entries
    }

    val items = remember(localSkins, catalog, ownedOnly, favOnly, favoriteIds, orderingFavoriteIds, purchased, hasPass) {
        buildDisplayList(
            localSkins, catalog, favoriteIds, orderingFavoriteIds,
            purchased, hasPass, ownedOnly, favOnly,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FilterRow(
            ownedOnly = ownedOnly,
            favOnly = favOnly,
            onOwned = { ownedOnly = !ownedOnly },
            onFav = {
                favOnly = !favOnly
                orderingFavoriteIds = favoriteIds
            },
            modifier = Modifier.padding(top = 16.dp),
        )

        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(
                        if (favOnly) R.string.vault_fav_empty else R.string.vault_locked_empty
                    ),
                    color = AppColors.Brown,
                    fontSize = 16.sp,
                )
            }
            return@Column
        }

        // 첫 진입 시 '적용 중'인 테마로 포커싱(예: 9개 중 적용중이 2번이면 2/9로 시작).
        // appliedId 상태는 진입 직후 아직 null이라, 설정값에서 직접 동기 계산해 초기 페이지로 쓴다.
        // remember(키 없음) → 화면 최초 1회만 계산(이후 사용자가 넘긴 위치는 유지).
        val initialPage = remember {
            val d = TimerPreferences.get(context).load()
            val appId = if (d.selectedCharacterSkinId == d.selectedTimerSkinId)
                d.selectedCharacterSkinId else null
            // 기프트코드 해금 직후 진입 시 해당 항목으로 포커싱, 아니면 적용 중 테마.
            val target = focusSkinId ?: appId
            items.indexOfFirst { it.id == target }.coerceAtLeast(0)
        }
        // 포커스 요청은 1회성 — 초기 페이지에 반영했으면 비워 재진입 시 재포커싱되지 않게 한다.
        LaunchedEffect(Unit) { if (focusSkinId != null) onFocusConsumed() }
        val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { items.size })
        val focused = items.getOrNull(pagerState.currentPage) ?: items.first()

        // 테마 이름.
        Text(
            text = focused.name,
            style = AppTypography.headlineSmall,
            color = AppColors.OnSurface,
            fontSize = 24.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )

        // 커버플로우 캐러셀.
        HorizontalPager(
            state = pagerState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 52.dp),
            pageSpacing = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .aspectRatio(10f / 9f),
        ) { page ->
            val item = items[page]
            val isFocused = page == pagerState.currentPage
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
            ) {
                VaultCard(
                    item = item,
                    isFocused = isFocused,
                    favorited = item.owned && item.id in favoriteIds,
                    applied = item.id == appliedId,
                    onToggleStar = {
                        val nowFav = item.id !in favoriteIds
                        TimerPreferences.get(context).setFavorite(item.id, nowFav)
                        favoriteIds = if (nowFav) favoriteIds + item.id else favoriteIds - item.id
                    },
                )
            }
        }

        // 위치 표시 (카드 바로 밑).
        Text(
            text = stringResource(R.string.vault_position, pagerState.currentPage + 1, items.size),
            color = AppColors.Brown,
            fontSize = 15.sp,
            modifier = Modifier.padding(top = 14.dp),
        )

        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))

        // 액션 버튼 (하단).
        ActionButtons(
            item = focused,
            appliedId = appliedId,
            downloadProgress = downloadProgresses.value[focused.id],
            downloadFailed = focused.id in failedDownloadIds.value,
            onApply = {
                TimerController.selectCharacterSkin(context, focused.id)
                TimerController.selectTimerSkin(context, focused.id)
                appliedId = focused.id
                Toast.makeText(
                    context, context.getString(R.string.vault_applied_toast, focused.name),
                    Toast.LENGTH_SHORT
                ).show()
            },
            onBuy = {
                Toast.makeText(context, context.getString(R.string.store_buy_stub), Toast.LENGTH_SHORT).show()
            },
            onDownload = { entry ->
                downloadProgresses.value = downloadProgresses.value + (entry.skinId to -1)
                failedDownloadIds.value = failedDownloadIds.value - entry.skinId
                SkinDownloader.download(
                    context = context.applicationContext,
                    entry = entry,
                    onProgress = { percent ->
                        downloadProgresses.value = downloadProgresses.value + (entry.skinId to percent)
                    },
                    onComplete = { success ->
                        downloadProgresses.value = downloadProgresses.value - entry.skinId
                        failedDownloadIds.value = if (success) {
                            failedDownloadIds.value - entry.skinId
                        } else {
                            failedDownloadIds.value + entry.skinId
                        }
                        val msg = if (success)
                            "${entry.name} ${context.getString(R.string.skin_download_complete)}"
                        else context.getString(R.string.skin_download_fail)
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        reload()
                    },
                )
            },
            onPreview = { onOpenDetail(focused) },
        )
    }
}

/** 정렬: 즐겨찾기 → 보유 → 미보유. 필터: 보유만/즐겨찾기만. */
private fun buildDisplayList(
    localSkins: List<com.daintyz.timerwidget.model.Skin>,
    catalog: List<RemoteSkinEntry>,
    favoriteIds: Set<String>,
    orderingFavoriteIds: Set<String>,
    purchased: Set<String>,
    hasPass: Boolean,
    ownedOnly: Boolean,
    favOnly: Boolean,
): List<VaultItem> {
    val localIds = localSkins.map { it.skinId }.toSet()
    val all = buildList {
        for (skin in localSkins) {
            val owned = SkinAvailabilityChecker.isSkinAvailable(skin, purchased, hasPass)
            add(VaultItem.Local(skin, owned))
        }
        for (entry in catalog) {
            if (entry.hidden) continue
            if (entry.skinId in localIds) continue
            val remote = VaultItem.Remote(entry)
            // 창고 캐러셀은 보유/구매가능(ACTIVE)만 노출. 미출시(UPCOMING)·기간만료(EXPIRED)는 제외.
            if (remote.saleStatus != SaleStatus.ACTIVE) continue
            add(remote)
        }
    }
    var filtered = all
    if (ownedOnly) filtered = filtered.filter { it.owned }
    if (favOnly) filtered = filtered.filter { it.owned && it.id in favoriteIds }
    return filtered.sortedWith(
        compareByDescending<VaultItem> { it.owned && it.id in orderingFavoriteIds }
            .thenByDescending { it.owned }
    )
}

@Composable
private fun FilterRow(
    ownedOnly: Boolean,
    favOnly: Boolean,
    onOwned: () -> Unit,
    onFav: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // '보유한 항목만 보기' 체크박스.
        Row(
            modifier = Modifier.clickable { onOwned() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = ownedOnly,
                onCheckedChange = { onOwned() },
                modifier = Modifier.scale(0.75f),
            )
            Text(
                text = stringResource(R.string.vault_owned_only),
                color = AppColors.Brown,
                fontSize = 13.sp,
            )
        }
        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
        // 즐겨찾기만 보기 토글(별 아이콘).
        FilterToggle(Icons.Filled.StarBorder, stringResource(R.string.vault_favorites), favOnly, onFav)
    }
}

@Composable
private fun FilterToggle(icon: ImageVector, contentDescription: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) AppColors.Primary else AppColors.Surface
    val fg = if (selected) AppColors.OnPrimary else AppColors.Brown
    Box(
        modifier = Modifier
            .size(width = 40.dp, height = 36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .border(1.dp, if (selected) AppColors.Primary else AppColors.Stroke, RoundedCornerShape(18.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = fg,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun VaultCard(
    item: VaultItem,
    isFocused: Boolean,
    favorited: Boolean,
    applied: Boolean,
    onToggleStar: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 12.dp)) {
        // 카드.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(24.dp))
                .background(AppColors.CardCream)
                .border(1.dp, AppColors.Stroke, RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center,
        ) {
            when (item) {
                is VaultItem.Local -> {
                    val bmp = localCardBitmap(item, isFocused)
                    // 캔버스 여백이 거의 없는 스킨(꽉 차 보임)만 목표 크기로 축소(중심 스케일 → 움직임/등록 유지).
                    val scale = rememberCardContentScale(item)
                    BitmapImage(
                        bitmap = bmp,
                        contentDescription = item.name,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp)
                            .graphicsLayer { scaleX = scale; scaleY = scale },
                        contentScale = ContentScale.Fit,
                    )
                }
                is VaultItem.Remote -> RemoteImage(
                    url = item.entry.thumbnailUrl,
                    contentDescription = item.name,
                    modifier = Modifier.fillMaxSize().padding(14.dp),
                    contentScale = ContentScale.Fit,
                )
            }

            // 미보유 딤 + 자물쇠.
            if (!item.owned) {
                Box(Modifier.fillMaxSize().background(AppColors.Dim))
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = stringResource(R.string.cd_locked),
                    tint = AppColors.OnPrimary,
                    modifier = Modifier.size(52.dp),
                )
            }
        }

        // 우상단: 적용됨 배지 / 가격 칩.
        if (applied) {
            Text(
                text = stringResource(R.string.vault_applied),
                color = AppColors.OnPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(14.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(AppColors.Primary)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        } else {
            Text(
                text = priceLabel(item),
                color = AppColors.TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(14.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(AppColors.CardCream)
                    .border(1.dp, AppColors.Stroke, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }

        // 좌상단: 즐겨찾기 별 (팝 애니메이션).
        if (item.owned) {
            StarToggle(
                favorited = favorited,
                onClick = onToggleStar,
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
            )
        }
    }
}

/** 즐겨찾기 별 — 원형 배경 없이 단독, 클릭 시 0.6→1 오버슈트 팝. */
@Composable
private fun StarToggle(favorited: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable {
                onClick()
                scope.launch {
                    scale.snapTo(0.6f)
                    scale.animateTo(
                        1f,
                        animationSpec = androidx.compose.animation.core.spring(
                            dampingRatio = 0.35f,
                            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium,
                        ),
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (favorited) Icons.Filled.Star else Icons.Filled.StarBorder,
            contentDescription = stringResource(R.string.cd_favorite),
            tint = if (favorited) AppColors.Primary else AppColors.Brown,
            modifier = Modifier
                .size(26.dp)
                .graphicsLayer { scaleX = scale.value; scaleY = scale.value },
        )
    }
}

/**
 * 카드 캐릭터 표시 스케일(스킨별 1회 계산, 캐싱). 정지 프레임의 불투명 영역 비율을 재서,
 * [CARD_TARGET_FILL]보다 꽉 찬 스킨만 그 크기로 줄이고(≤1) 이미 작은 스킨은 1.0 그대로 둔다.
 */
@Composable
private fun rememberCardContentScale(item: VaultItem.Local): Float {
    val context = LocalContext.current
    return remember(item.id) {
        val stopFrame = item.skin.character.stop.frames.first()
        val bmp = SkinRepository.loadFrameBitmap(context, item.id, stopFrame)
        val fill = bmp?.let { opaqueFillRatio(it) } ?: 1f
        if (fill <= 0f) 1f else (CARD_TARGET_FILL / fill).coerceIn(0.5f, 1f)
    }
}

/** 비트맵에서 불투명(알파>0) 영역이 캔버스 변 대비 차지하는 최대 비율(가로/세로 중 큰 쪽). 0~1. */
private fun opaqueFillRatio(bmp: Bitmap): Float {
    val w = bmp.width
    val h = bmp.height
    if (w <= 0 || h <= 0) return 1f
    val pixels = IntArray(w * h)
    bmp.getPixels(pixels, 0, w, 0, 0, w, h)
    var minX = w; var minY = h; var maxX = -1; var maxY = -1
    var i = 0
    for (y in 0 until h) {
        for (x in 0 until w) {
            if ((pixels[i] ushr 24) != 0) {
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
            i++
        }
    }
    if (maxX < minX) return 1f // 전부 투명
    val fw = (maxX - minX + 1).toFloat() / w
    val fh = (maxY - minY + 1).toFloat() / h
    return maxOf(fw, fh)
}

/** 로컬 카드 비트맵: 정지 1프레임. 포커스+보유면 running 프레임으로 100ms 틱 애니메이션. */
@Composable
private fun localCardBitmap(item: VaultItem.Local, isFocused: Boolean): Bitmap? {
    val context = LocalContext.current
    val stopFrame = item.skin.character.stop.frames.first()
    var bmp by remember(item.id) {
        mutableStateOf(SkinRepository.loadFrameBitmap(context, item.id, stopFrame))
    }
    LaunchedEffect(item.id, isFocused, item.owned) {
        if (isFocused && item.owned) {
            val ctx0 = FrameAnimationController.FrameContext(0L, 0L, 0L)
            while (true) {
                val file = FrameAnimationController.currentFrameFile(
                    item.skin, TimerState.RUNNING, ctx0, SystemClock.elapsedRealtime()
                )
                SkinRepository.loadFrameBitmap(context, item.id, file)?.let { bmp = it }
                delay(100)
            }
        } else {
            bmp = SkinRepository.loadFrameBitmap(context, item.id, stopFrame)
        }
    }
    return bmp
}

@Composable
private fun ActionButtons(
    item: VaultItem,
    appliedId: String?,
    downloadProgress: Int?,
    downloadFailed: Boolean,
    onApply: () -> Unit,
    onBuy: () -> Unit,
    onDownload: (RemoteSkinEntry) -> Unit,
    onPreview: () -> Unit,
) {
    val applied = item.id == appliedId
    val downloading = downloadProgress != null
    Button(
        onClick = {
            when {
                downloading -> {}
                item.owned -> if (!applied) onApply()
                item is VaultItem.Remote && item.isFree -> onDownload(item.entry)
                else -> onBuy()
            }
        },
        enabled = !(downloading || (item.owned && applied)),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AppColors.Primary,
            contentColor = AppColors.OnPrimary,
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp).height(52.dp),
    ) {
        val label = when {
            downloading -> stringResource(R.string.skin_btn_downloading)
            item.owned -> stringResource(if (applied) R.string.vault_in_use else R.string.vault_apply)
            item is VaultItem.Remote && item.isFree && downloadFailed -> stringResource(R.string.skin_btn_retry)
            item is VaultItem.Remote && item.isFree -> stringResource(R.string.skin_btn_download)
            else -> stringResource(R.string.vault_buy)
        }
        DownloadButtonContent(label = label, progress = downloadProgress)
    }

    TextButton(
        onClick = onPreview,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 2.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = AppColors.Brown,
            modifier = Modifier.size(18.dp),
        )
        androidx.compose.foundation.layout.Spacer(Modifier.size(6.dp))
        Text(stringResource(R.string.vault_preview), color = AppColors.Brown, fontSize = 15.sp)
    }
    androidx.compose.foundation.layout.Spacer(Modifier.height(48.dp))
}

private fun priceLabel(item: VaultItem): String {
    val base = if (item.isFree || item.price <= 0) "무료" else "%,d원".format(item.price)
    return if (item.prestige) "✦ $base" else base
}
