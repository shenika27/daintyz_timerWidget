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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.daintyz.timerwidget.R
import com.daintyz.timerwidget.data.TimerPreferences
import com.daintyz.timerwidget.model.RemoteSkinEntry
import com.daintyz.timerwidget.skin.SkinAvailabilityChecker
import com.daintyz.timerwidget.skin.SkinDownloader
import com.daintyz.timerwidget.skin.SkinRepository
import com.daintyz.timerwidget.ui.VaultItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val CATALOG_URL =
    "https://raw.githubusercontent.com/shenika27/daintyz_timer_characterList/main/catalog.json"

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
    var showAll by rememberSaveable { mutableStateOf(false) }

    fun reload() {
        val data = TimerPreferences.get(context).load()
        purchased = data.purchasedSkinIds
        hasPass = data.hasLifetimePass
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
            runCatching { SkinDownloader.fetchCatalog(CATALOG_URL) }.getOrNull()
        }
        if (entries != null) catalog = entries
    }

    val items = remember(localSkins, catalog, purchased, hasPass, showAll) {
        val localIds = localSkins.map { it.skinId }.toSet()
        buildList {
            for (skin in localSkins) {
                val owned = SkinAvailabilityChecker.isSkinAvailable(skin, purchased, hasPass)
                if (owned && !showAll) continue
                add(VaultItem.Local(skin, owned))
            }
            for (entry in catalog) {
                if (entry.skinId !in localIds) add(VaultItem.Remote(entry))
            }
        }
    }

    Column(Modifier.fillMaxSize().background(AppColors.Background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = showAll, onCheckedChange = { showAll = it })
            Text(stringResource(R.string.store_show_all), color = AppColors.TextPrimary)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(items, key = { it.id }) { item ->
                StoreHeroCard(item = item, onClick = { onOpenDetail(item) })
            }
        }
    }
}

/**
 * 히어로 카드 1장: 위쪽 투명 영역에 캐릭터를 크게, 아래 푸터에 이름/부제 + 가격칩(또는 보유표시).
 * 좌상단 배지: NEW(출시일+7일 이내) / 프리스티지. 탭하면 상세로 이동(구매/다운로드는 거기서).
 */
@Composable
private fun StoreHeroCard(item: VaultItem, onClick: () -> Unit) {
    val context = LocalContext.current
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
                    contentDescription = item.name,
                    modifier = Modifier.fillMaxSize().padding(20.dp),
                    contentScale = ContentScale.Fit,
                )
                is VaultItem.Remote -> RemoteImage(
                    url = item.entry.thumbnailUrl,
                    contentDescription = item.name,
                    modifier = Modifier.fillMaxSize().padding(20.dp),
                    contentScale = ContentScale.Fit,
                )
            }

            Row(
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (item.isNew) {
                    Badge(stringResource(R.string.skin_tag_new), AppColors.OnPrimary, AppColors.Primary)
                }
                if (item.prestige) {
                    Badge(stringResource(R.string.skin_tag_prestige), AppColors.Primary, AppColors.OnPrimary)
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
                    text = item.name,
                    color = AppColors.TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
                item.description?.let { desc ->
                    Text(
                        text = desc,
                        color = AppColors.Brown,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            if (item.owned) {
                Text(
                    text = "✓ ${stringResource(R.string.skin_badge_owned)}",
                    color = OwnedGreen,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            } else {
                Text(
                    text = priceLabel(item),
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

private fun priceLabel(item: VaultItem): String =
    if (item.isFree || item.price <= 0) "무료" else "%,d원".format(item.price)
