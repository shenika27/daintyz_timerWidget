package com.daintyz.timerwidget.ui.compose

import android.widget.Toast
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
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

/** 상점 탭 — 로컬+카탈로그 테마 목록. 카드 탭 시 상세로 이동, 거기서 구매/다운로드. */
@Composable
fun StoreScreen(onOpenDetail: (VaultItem) -> Unit) {
    val context = LocalContext.current

    var localSkins by remember { mutableStateOf(SkinRepository.loadAllSkins(context)) }
    var catalog by remember { mutableStateOf(emptyList<RemoteSkinEntry>()) }
    var purchased by remember { mutableStateOf(emptySet<String>()) }
    var hasPass by remember { mutableStateOf(false) }
    var showAll by rememberSaveable { mutableStateOf(false) }
    var downloadingIds by remember { mutableStateOf(setOf<String>()) }

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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items, key = { it.id }) { item ->
                StoreCard(
                    item = item,
                    downloading = item.id in downloadingIds,
                    onClick = { onOpenDetail(item) },
                    onBuy = {
                        Toast.makeText(context, context.getString(R.string.store_buy_stub), Toast.LENGTH_SHORT).show()
                    },
                    onDownload = { entry ->
                        downloadingIds = downloadingIds + entry.skinId
                        SkinDownloader.download(
                            context = context.applicationContext,
                            entry = entry,
                            onProgress = {},
                            onComplete = { success ->
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    downloadingIds = downloadingIds - entry.skinId
                                    val msg = if (success)
                                        "${entry.name} ${context.getString(R.string.skin_download_complete)}"
                                    else context.getString(R.string.skin_download_fail)
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    reload()
                                }
                            },
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun StoreCard(
    item: VaultItem,
    downloading: Boolean,
    onClick: () -> Unit,
    onBuy: () -> Unit,
    onDownload: (RemoteSkinEntry) -> Unit,
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.CardCream)
            .border(1.dp, AppColors.Stroke, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 썸네일.
        Box(Modifier.size(72.dp).clip(RoundedCornerShape(12.dp))) {
            when (item) {
                is VaultItem.Local -> BitmapImage(
                    bitmap = item.skin.character.stop.frames.firstOrNull()
                        ?.let { SkinRepository.loadFrameBitmap(context, item.id, it) },
                    contentDescription = item.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
                is VaultItem.Remote -> RemoteImage(
                    url = item.entry.thumbnailUrl,
                    contentDescription = item.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
        }

        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.prestige) {
                    Text("✦ ", color = AppColors.Primary, fontWeight = FontWeight.Bold)
                }
                Text(item.name, color = AppColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                text = storeBadge(item),
                color = AppColors.Brown,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        // 액션 (보유면 없음).
        if (!item.owned) {
            Button(
                onClick = {
                    when {
                        downloading -> {}
                        item is VaultItem.Remote && item.isFree -> onDownload(item.entry)
                        else -> onBuy()
                    }
                },
                enabled = !downloading,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.Primary, contentColor = AppColors.OnPrimary,
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            ) {
                val label = when {
                    downloading -> stringResource(R.string.skin_btn_downloading)
                    item is VaultItem.Remote && item.isFree -> stringResource(R.string.skin_btn_download)
                    else -> stringResource(R.string.skin_btn_buy)
                }
                Text(label, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun storeBadge(item: VaultItem): String = when {
    item.owned -> stringResource(R.string.skin_badge_owned)
    item.isFree || item.price <= 0 -> stringResource(R.string.skin_badge_free)
    else -> "%,d원".format(item.price)
}
