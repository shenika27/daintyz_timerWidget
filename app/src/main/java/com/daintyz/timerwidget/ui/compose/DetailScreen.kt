package com.daintyz.timerwidget.ui.compose

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.daintyz.timerwidget.R
import com.daintyz.timerwidget.controller.TimerController
import com.daintyz.timerwidget.data.TimerPreferences
import com.daintyz.timerwidget.model.RemoteSkinEntry
import com.daintyz.timerwidget.model.TimerData
import com.daintyz.timerwidget.model.TimerState
import com.daintyz.timerwidget.skin.RemoteImageLoader
import com.daintyz.timerwidget.skin.SkinRepoUrls
import com.daintyz.timerwidget.skin.SkinDownloader
import com.daintyz.timerwidget.skin.SkinRepository
import com.daintyz.timerwidget.widget.WidgetUpdater
import kotlinx.coroutines.delay
import kotlin.math.absoluteValue

/** prevNN 미리보기 최대 탐침 수 (디자인레포 규칙상 prev01부터 연속, 첫 결번에서 중단). */
private const val MAX_PREVIEWS = 30

/** 보유 미리보기에서 스와이프할 상태 순서. */
private val PREVIEW_STATES = listOf(
    TimerState.IDLE, TimerState.RUNNING, TimerState.PAUSED, TimerState.COMPLETE
)

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
 * 테마 상세/미리보기 화면 (Compose 커버플로우 캐러셀).
 *
 * - 보유: character_zip 프레임으로 정지/진행 중/중단/완료를 스와이프(페이지네이션 자리에 상태명). 하단 = 적용하기/적용 중.
 * - 미보유: preview/{id}/prevNN.png를 스와이프(하단 = n/총). 하단 버튼 = 구매하기(가격) / (무료면 다운로드).
 *
 * 다운로드로 보유 전환되면 그 자리에서 상태 스와이프 모드로 바뀐다.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DetailScreen(
    skinId: String,
    name: String,
    initialOwned: Boolean,
    isFree: Boolean,
    price: Int,
    prestige: Boolean,
    previewBase: String,
    zipUrl: String?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current

    var owned by remember { mutableStateOf(initialOwned) }
    var downloading by remember { mutableStateOf(false) }
    var skin by remember { mutableStateOf(SkinRepository.findSkin(context, skinId)) }
    var applied by remember {
        mutableStateOf(TimerPreferences.get(context).load().selectedCharacterSkinId == skinId)
    }

    // 미보유: prevNN 이미지를 앞에서부터 순차 탐침해 채운다(첫 결번에서 중단).
    val previews = remember { mutableStateListOf<Bitmap>() }
    var cancelled by remember { mutableStateOf(false) }
    DisposableEffect(skinId, owned) {
        cancelled = false
        if (!owned) {
            previews.clear()
            val urls = (1..MAX_PREVIEWS).map { SkinRepoUrls.preview(skinId, it, previewBase) }
            RemoteImageLoader.loadGallery(
                urls = urls,
                isCancelled = { cancelled },
            ) { _, bmp -> if (!cancelled) previews.add(bmp) }
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
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_back),
                    contentDescription = stringResource(R.string.cd_detail_back),
                    tint = AppColors.Brown,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        // 테마 이름 — 보유목록과 동일 위치/스타일(캐러셀 위 중앙, 24sp).
        Text(
            text = name,
            color = AppColors.OnSurface,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )

        val localSkin = skin
        val pageCount = if (owned) PREVIEW_STATES.size else previews.size.coerceAtLeast(1)
        val pagerState = rememberPagerState(pageCount = { pageCount })

        // 커버플로우 캐러셀.
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 52.dp),
            pageSpacing = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .aspectRatio(10f / 9f),
        ) { page ->
            val isFocused = page == pagerState.currentPage
            CoverFlowBox(pagerState = pagerState, page = page) {
                DetailCard {
                    when {
                        owned && localSkin != null -> WidgetStatePreview(
                            // 캐릭터만이 아니라 '실제 위젯 전체'(타이머+캐릭터)를 같은 RemoteViews 코드로 렌더한다.
                            // 타이머 위치(위/아래)는 현재 설정(layoutMode)을 따르고, 폰트/버튼도 위젯과 100% 일치한다.
                            skinId = skinId,
                            state = PREVIEW_STATES[page],
                            isFocused = isFocused,
                            // 실제 2x2 위젯과 같은 정사각 비율로 렌더(미리보기=위젯 종횡비 일치).
                            modifier = Modifier.fillMaxHeight().aspectRatio(1f),
                        )
                        !owned -> BitmapImage(
                            bitmap = previews.getOrNull(page),
                            contentDescription = name,
                            modifier = Modifier.fillMaxSize().padding(14.dp),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
            }
        }

        // 페이지네이션 자리: 보유=상태명, 미보유=n/총.
        val caption = when {
            owned -> stateLabel(PREVIEW_STATES[pagerState.currentPage])
            previews.isEmpty() -> stringResource(R.string.detail_preview_empty)
            else -> stringResource(R.string.vault_position, pagerState.currentPage + 1, previews.size)
        }
        Text(
            text = caption,
            color = if (owned) AppColors.OnSurface else AppColors.Brown,
            fontSize = if (owned) 18.sp else 15.sp,
            fontWeight = if (owned) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(top = 14.dp),
        )

        Spacer(Modifier.weight(1f))

        // 하단 액션 버튼.
        val buyLabel = if (isFree || price <= 0) {
            stringResource(R.string.skin_btn_download)
        } else {
            stringResource(R.string.detail_buy_price, "%,d원".format(price))
        }
        Button(
            onClick = {
                when {
                    owned -> if (!applied) {
                        TimerController.selectCharacterSkin(context, skinId)
                        TimerController.selectTimerSkin(context, skinId)
                        applied = true
                        Toast.makeText(
                            context, context.getString(R.string.vault_applied_toast, name),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    downloading -> {}
                    isFree || price <= 0 -> startDownload(
                        context, skinId, name, isFree, price, prestige, previewBase, zipUrl,
                        onStart = { downloading = true },
                        onDone = { success ->
                            downloading = false
                            if (success) {
                                owned = true
                                skin = SkinRepository.findSkin(context, skinId)
                            }
                        },
                    )
                    else -> Toast.makeText(
                        context, context.getString(R.string.store_buy_stub), Toast.LENGTH_SHORT
                    ).show()
                }
            },
            enabled = !(downloading || (owned && applied)),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AppColors.Primary,
                contentColor = AppColors.OnPrimary,
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp).height(52.dp),
        ) {
            val label = when {
                owned -> stringResource(if (applied) R.string.detail_applied else R.string.detail_apply)
                downloading -> stringResource(R.string.skin_btn_downloading)
                else -> buyLabel
            }
            Text(label, fontSize = 16.sp)
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
 * 한 상태의 '실제 위젯'(타이머+캐릭터)을 RemoteViews로 라이브 렌더한다(옛 PreviewActivity의 Compose 이식).
 * 위젯과 동일한 [WidgetUpdater.buildRemoteViews] 경로를 그대로 쓰므로 폰트/버튼/타이머 위치가 위젯과 일치한다.
 *
 * - 포커스 페이지: 100ms 틱으로 [android.widget.RemoteViews.reapply] → 캐릭터 애니메이션 + 타이머 카운트다운.
 * - 비포커스 페이지: 1회 apply 후 정지(부하 절약).
 * - forPreview=true 라 미리보기 안의 탭/버튼은 실제 타이머 상태를 건드리지 않는다.
 */
@Composable
private fun WidgetStatePreview(
    skinId: String,
    state: TimerState,
    isFocused: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appCtx = context.applicationContext
    val container = remember { FrameLayout(context) }

    AndroidView(factory = { container }, modifier = modifier)

    LaunchedEffect(skinId, state, isFocused) {
        // RUNNING 종료 시각은 진입 시 1회 고정 — 매 틱 재계산하면 카운트다운이 줄지 않는다.
        val runningEnd = SystemClock.elapsedRealtime() +
            TimerPreferences.get(context).load().lastSetMinutes.coerceAtLeast(1) * 60_000L

        fun build() = WidgetUpdater.buildRemoteViews(
            context,
            previewData(context, skinId, state, runningEnd),
            SystemClock.elapsedRealtime(),
            forPreview = true,
        )

        // 최초 1회 apply (이후 reapply로 제자리 갱신 → 뷰 재생성 없이 깜빡임 방지).
        val root = build().apply(appCtx, container)
        container.removeAllViews()
        root.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        )
        container.addView(root)

        if (isFocused) {
            while (true) {
                delay(100)
                build().reapply(appCtx, root)
            }
        }
    }
}

/**
 * 현재 적용중 설정(layoutMode 등)을 베이스로, 미리보는 캐릭터만 [skinId]로 치환하고 상태별 표본 시간을 채운
 * 미리보기용 스냅샷. 타이머 스킨은 이 스킨이 timer 블록을 가질 때만 함께 치환(없으면 현재 타이머 유지).
 */
private fun previewData(
    context: android.content.Context,
    skinId: String,
    state: TimerState,
    runningEndElapsed: Long,
): TimerData {
    val base = TimerPreferences.get(context).load()
    val sampleMs = base.lastSetMinutes.coerceAtLeast(1) * 60_000L
    val withState = when (state) {
        TimerState.IDLE -> base.copy(state = TimerState.IDLE)
        TimerState.RUNNING -> base.copy(
            state = TimerState.RUNNING,
            totalMillis = sampleMs,
            targetEndElapsed = runningEndElapsed,
            remainingMillisAtPause = 0L,
        )
        TimerState.PAUSED -> base.copy(
            state = TimerState.PAUSED,
            totalMillis = sampleMs,
            remainingMillisAtPause = sampleMs / 2,
        )
        TimerState.COMPLETE -> base.copy(
            state = TimerState.COMPLETE,
            targetEndElapsed = 0L,
            remainingMillisAtPause = 0L,
        )
    }
    val skin = SkinRepository.findSkin(context, skinId)
    return withState.copy(
        selectedCharacterSkinId = skinId,
        selectedTimerSkinId = if (skin?.timer != null) skinId else withState.selectedTimerSkinId,
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
    onDone: (Boolean) -> Unit,
) {
    onStart()
    val entry = RemoteSkinEntry(
        skinId = skinId,
        name = name,
        price = price,
        isFree = isFree,
        prestige = prestige,
        zipUrl = zipUrl ?: "$previewBase/character_zip/$skinId.zip",
        thumbnailUrl = SkinRepoUrls.themeThumb(skinId, previewBase),
        baseUrl = previewBase,
    )
    SkinDownloader.download(
        context = context.applicationContext,
        entry = entry,
        onProgress = {},
        onComplete = { success ->
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                val msg = if (success)
                    "$name ${context.getString(R.string.skin_download_complete)}"
                else context.getString(R.string.skin_download_fail)
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                onDone(success)
            }
        },
    )
}
