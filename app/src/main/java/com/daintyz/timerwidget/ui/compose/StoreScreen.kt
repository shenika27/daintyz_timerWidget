package com.daintyz.timerwidget.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.daintyz.timerwidget.R
import com.daintyz.timerwidget.data.TimerPreferences
import com.daintyz.timerwidget.model.RemoteSkinEntry
import com.daintyz.timerwidget.skin.SkinAvailabilityChecker
import com.daintyz.timerwidget.skin.SkinDownloader
import com.daintyz.timerwidget.skin.SkinRepoUrls
import com.daintyz.timerwidget.skin.SkinRepository
import com.daintyz.timerwidget.ui.SaleStatus
import com.daintyz.timerwidget.ui.VaultItem
import com.daintyz.timerwidget.ui.displayDescription
import com.daintyz.timerwidget.ui.displayName
import com.daintyz.timerwidget.ui.priceLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 보유(완료) 상태 표시 색 — 팔레트에 없는 슬롯이라 카드 푸터에서만 인라인으로 쓴다. */
private val OwnedGreen = Color(0xFF1D9E75)

/** 상점 탭 — 로컬+카탈로그 테마 목록. 큰 히어로 카드 리스트. 카드 탭 시 상세로 이동, 거기서 구매/다운로드. */
@Composable
fun StoreScreen(onOpenDetail: (VaultItem) -> Unit) {
    val context = LocalContext.current

    var localSkins by remember { mutableStateOf(SkinRepository.loadAllSkins(context)) }
    var catalog by remember { mutableStateOf(emptyList<RemoteSkinEntry>()) }
    var purchased by remember { mutableStateOf(emptySet<String>()) }
    var hasPass by remember { mutableStateOf(false) }
    var giftUnlocked by remember { mutableStateOf(emptySet<String>()) }
    var favoriteIds by remember { mutableStateOf(TimerPreferences.get(context).loadFavoriteSkinIds()) }
    var showAll by rememberSaveable { mutableStateOf(false) }
    var wishlistOnly by rememberSaveable { mutableStateOf(false) }

    fun reload() {
        val data = TimerPreferences.get(context).load()
        purchased = data.purchasedSkinIds
        hasPass = data.hasEffectiveLifetimePass
        giftUnlocked = data.giftUnlockedSkinIds
        favoriteIds = TimerPreferences.get(context).loadFavoriteSkinIds()
        localSkins = SkinRepository.loadAllSkins(context)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) reload() }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    LaunchedEffect(Unit) {
        val entries = withContext(Dispatchers.IO) {
            runCatching { SkinDownloader.fetchCatalog(SkinRepoUrls.CATALOG_URL) }.getOrNull()
        }
        if (entries != null) catalog = entries
    }

    val items = remember(localSkins, catalog, purchased, hasPass, giftUnlocked, favoriteIds, showAll, wishlistOnly) {
        val localIds = localSkins.map { it.skinId }.toSet()
        val catalogById = catalog.associateBy { it.skinId }
        buildList {
            for (skin in localSkins) {
                // 앱 내장 기본 에셋(예: cha01)은 상점에 노출하지 않는다 — 상점은 디자인 레포 항목만.
                if (skin.bundled) continue
                val owned = SkinAvailabilityChecker.isSkinAvailable(skin, purchased, hasPass, giftUnlocked)
                if (owned && !showAll) continue
                add(VaultItem.Local(skin, owned, catalogById[skin.skinId]))
            }
            for (entry in catalog) {
                // 숨김 스킨은 상점 목록에서 제외(기프트코드/링크 전용). 이미 보유한 경우엔
                // 로컬 스킨으로 위에서 처리되므로 여기서 빠져도 창고/표시에 영향 없음.
                if (entry.hidden) continue
                if (entry.skinId in localIds) continue
                // 이용권/구매/기프트로 이미 권리가 있는(미다운로드) 원격 테마는 보유로 간주 → 기본은 숨김(전체보기 시 노출).
                val owned = SkinAvailabilityChecker.isSkinAvailable(
                    entry.skinId, entry.isFree, entry.prestige, purchased, hasPass, giftUnlocked
                )
                if (owned && !showAll) continue
                val remote = VaultItem.Remote(entry, owned)
                // 한정구매: 시작 전(UPCOMING)은 미출시라 숨김. 종료 후(EXPIRED)는 카드는 두되 잠금 표시.
                if (remote.saleStatus == SaleStatus.UPCOMING) continue
                add(remote)
            }
        }.let { list ->
            if (wishlistOnly) list.filter { !it.owned && it.id in favoriteIds } else list
        }
    }

    Column(Modifier.fillMaxSize().background(AppColors.Background)) {
        // 중앙 타이틀.
        Text(
            text = stringResource(R.string.store_title),
            style = AppTypography.headlineSmall,
            color = AppColors.TextPrimary,
            fontSize = 22.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
        )

        // 목록 좌측상단 작은 전체보기 토글.
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.clickable { showAll = !showAll },
                verticalAlignment = Alignment.CenterVertically,
            ) {
            Checkbox(
                checked = showAll,
                onCheckedChange = { showAll = it },
                modifier = Modifier.scale(0.75f),
            )
            Text(
                text = stringResource(R.string.store_show_all),
                color = AppColors.Brown,
                fontSize = 12.sp,
            )
            }
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (wishlistOnly) AppColors.Primary else AppColors.Surface)
                        .border(
                            1.dp,
                            if (wishlistOnly) AppColors.Primary else AppColors.Stroke,
                            CircleShape,
                        )
                        .clickable { wishlistOnly = !wishlistOnly },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (wishlistOnly) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = stringResource(
                            if (wishlistOnly) R.string.cd_disable_wishlist_filter
                            else R.string.cd_enable_wishlist_filter
                        ),
                        tint = if (wishlistOnly) AppColors.OnPrimary else AppColors.Brown,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(items, key = { it.id }) { item ->
                StoreHeroCard(
                    item = item,
                    favorited = item.id in favoriteIds,
                    onToggleWishlist = {
                        val next = item.id !in favoriteIds
                        TimerPreferences.get(context).setFavorite(item.id, next)
                        favoriteIds = if (next) favoriteIds + item.id else favoriteIds - item.id
                    },
                    onClick = { onOpenDetail(item) },
                )
            }
        }
    }
}

/**
 * 히어로 카드 1장: 위쪽 투명 영역에 캐릭터를 크게, 아래 푸터에 이름/부제 + 가격칩(또는 보유표시).
 * 좌상단 배지: NEW(출시일+7일 이내) / 프리스티지. 탭하면 상세로 이동(구매/다운로드는 거기서).
 */
@Composable
private fun StoreHeroCard(
    item: VaultItem,
    favorited: Boolean,
    onToggleWishlist: () -> Unit,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val displayName = item.displayName(context)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(AppColors.CardCream)
            .border(1.dp, AppColors.Stroke, RoundedCornerShape(24.dp))
            .clickable { onClick() },
    ) {
        // 캐릭터 영역(투명 배경) + 좌상단 배지.
        Box(Modifier.fillMaxWidth().height(168.dp)) {
            when (item) {
                is VaultItem.Local -> BitmapImage(
                    bitmap = item.skin.character.stop.frames.firstOrNull()
                        ?.let { SkinRepository.loadFrameBitmap(context, item.id, it) },
                    contentDescription = displayName,
                    modifier = Modifier.fillMaxSize().padding(20.dp),
                    contentScale = ContentScale.Fit,
                )
                is VaultItem.Remote -> RemoteImage(
                    url = item.entry.thumbnailUrl,
                    contentDescription = displayName,
                    modifier = Modifier.fillMaxSize().padding(20.dp),
                    contentScale = ContentScale.Fit,
                )
            }

            Row(
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (item.saleStatus == SaleStatus.EXPIRED) {
                    Badge(stringResource(R.string.sale_expired), AppColors.OnSurface, AppColors.Stroke)
                }
                if (item.isNew) {
                    Badge(stringResource(R.string.skin_tag_new), AppColors.OnPrimary, AppColors.Primary)
                }
                if (item.prestige) {
                    Badge(stringResource(R.string.skin_tag_prestige), AppColors.Primary, AppColors.OnPrimary)
                }
            }
            if (!item.owned) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable { onToggleWishlist() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (favorited) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = stringResource(
                            if (favorited) R.string.cd_remove_wishlist else R.string.cd_add_wishlist
                        ),
                        tint = if (favorited) AppColors.Primary else AppColors.Brown,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }

        // 푸터: 이름/부제 + 우측 가격칩/보유표시.
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = displayName,
                    color = AppColors.TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
                item.displayDescription(context)?.let { desc ->
                    Text(
                        text = desc,
                        color = AppColors.Brown,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            when {
                item.owned -> Text(
                    text = "✓ ${stringResource(R.string.skin_badge_owned)}",
                    color = OwnedGreen,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
                item.saleStatus == SaleStatus.EXPIRED -> Text(
                    text = stringResource(R.string.sale_expired),
                    color = AppColors.Brown,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppColors.Background)
                        .border(1.dp, AppColors.Stroke, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                )
                else -> Text(
                    text = priceLabel(context, item),
                    color = AppColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppColors.Background)
                        .border(1.dp, AppColors.Stroke, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }
    }
}

/** 좌상단 작은 알약 배지. */
@Composable
private fun Badge(text: String, fg: Color, bg: Color) {
    Text(
        text = text,
        color = fg,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

