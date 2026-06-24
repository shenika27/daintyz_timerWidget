package com.daintyz.timerwidget.ui.compose

import android.graphics.Bitmap
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
import com.daintyz.timerwidget.ui.VaultItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue

private const val CATALOG_URL =
    "https://raw.githubusercontent.com/shenika27/daintyz_timer_characterList/main/catalog.json"

/**
 * 보유(테마) 화면 — 보유/미보유 통합 커버플로우 캐러셀 (Compose 재작성).
 * 정렬: 즐겨찾기 → 보유 → 미보유. 적용=캐릭터+타이머 동시. 미리보기=상세 화면.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun VaultScreen(onOpenDetail: (VaultItem) -> Unit) {
    val context = LocalContext.current

    var localSkins by remember { mutableStateOf(SkinRepository.loadAllSkins(context)) }
    var catalog by remember { mutableStateOf(emptyList<RemoteSkinEntry>()) }
    var favoriteIds by remember { mutableStateOf(TimerPreferences.get(context).loadFavoriteSkinIds()) }
    var purchased by remember { mutableStateOf(emptySet<String>()) }
    var hasPass by remember { mutableStateOf(false) }
    var appliedId by remember { mutableStateOf<String?>(null) }

    var ownedOnly by rememberSaveable { mutableStateOf(false) }
    var favOnly by rememberSaveable { mutableStateOf(false) }
    val downloadingIds = remember { mutableStateOf(setOf<String>()) }

    fun reload() {
        val prefs = TimerPreferences.get(context)
        favoriteIds = prefs.loadFavoriteSkinIds()
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

    val items = remember(localSkins, catalog, ownedOnly, favOnly, favoriteIds, purchased, hasPass) {
        buildDisplayList(localSkins, catalog, favoriteIds, purchased, hasPass, ownedOnly, favOnly)
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
            onFav = { favOnly = !favOnly },
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

        val pagerState = rememberPagerState(pageCount = { items.size })
        val focused = items.getOrNull(pagerState.currentPage) ?: items.first()

        // 테마 이름.
        Text(
            text = focused.name,
            color = AppColors.OnSurface,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
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
                    favorited = item.id in favoriteIds,
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
            downloading = focused.id in downloadingIds.value,
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
                downloadingIds.value = downloadingIds.value + entry.skinId
                SkinDownloader.download(
                    context = context.applicationContext,
                    entry = entry,
                    onProgress = {},
                    onComplete = { success ->
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            downloadingIds.value = downloadingIds.value - entry.skinId
                            val msg = if (success)
                                "${entry.name} ${context.getString(R.string.skin_download_complete)}"
                            else context.getString(R.string.skin_download_fail)
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            reload()
                        }
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
            if (entry.skinId !in localIds) add(VaultItem.Remote(entry))
        }
    }
    var filtered = all
    if (ownedOnly) filtered = filtered.filter { it.owned }
    if (favOnly) filtered = filtered.filter { it.id in favoriteIds }
    return filtered.sortedWith(
        compareByDescending<VaultItem> { it.id in favoriteIds }.thenByDescending { it.owned }
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
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FilterToggle(R.drawable.ic_money, stringResource(R.string.vault_owned_only), ownedOnly, onOwned)
        FilterToggle(R.drawable.ic_star_outline, stringResource(R.string.vault_favorites), favOnly, onFav)
    }
}

@Composable
private fun FilterToggle(iconRes: Int, contentDescription: String, selected: Boolean, onClick: () -> Unit) {
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
            painter = painterResource(iconRes),
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
    val context = LocalContext.current

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
                    BitmapImage(
                        bitmap = bmp,
                        contentDescription = item.name,
                        modifier = Modifier.fillMaxSize().padding(14.dp),
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
                    painter = painterResource(R.drawable.ic_lock),
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
        StarToggle(
            favorited = favorited,
            onClick = onToggleStar,
            modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
        )
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
            painter = painterResource(
                if (favorited) R.drawable.ic_star_filled else R.drawable.ic_star_outline
            ),
            contentDescription = stringResource(R.string.cd_favorite),
            tint = if (favorited) AppColors.Primary else AppColors.Brown,
            modifier = Modifier
                .size(26.dp)
                .graphicsLayer { scaleX = scale.value; scaleY = scale.value },
        )
    }
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
    downloading: Boolean,
    onApply: () -> Unit,
    onBuy: () -> Unit,
    onDownload: (RemoteSkinEntry) -> Unit,
    onPreview: () -> Unit,
) {
    val applied = item.id == appliedId
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
            item is VaultItem.Remote && item.isFree -> stringResource(R.string.skin_btn_download)
            else -> stringResource(R.string.vault_buy)
        }
        Text(label, fontSize = 16.sp)
    }

    TextButton(
        onClick = onPreview,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 2.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_search),
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
