package com.daintyz.timerwidget.ui.compose

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.daintyz.timerwidget.billing.BillingManager
import com.daintyz.timerwidget.controller.TimerController
import com.daintyz.timerwidget.data.TimerPreferences
import com.daintyz.timerwidget.model.LayoutMode
import com.daintyz.timerwidget.notification.TimerNotifications
import com.daintyz.timerwidget.skin.GiftCodeRedeemer
import com.daintyz.timerwidget.skin.SkinDownloader
import com.daintyz.timerwidget.skin.SkinRepoUrls
import com.daintyz.timerwidget.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 설정 탭 — 섹션(타이머/알림/표시/기타)으로 묶은 행 리스트.
 * 토글/세그먼트/딥링크 등 행 성격에 맞는 컨트롤을 우측에 둔다.
 */
@Composable
fun SettingsScreen(onGoToVault: (skinId: String) -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { TimerPreferences.get(context) }

    // 기프트코드 해금 성공 시 띄울 다이얼로그 대상 (skinId to name). null이면 닫힘.
    var unlockedSkin by remember { mutableStateOf<Pair<String, String>?>(null) }

    var stepMin by remember { mutableStateOf(TextFieldValue("")) }
    var stepSec by remember { mutableStateOf(TextFieldValue("")) }
    var layoutMode by remember { mutableStateOf(LayoutMode.TOP) }
    var completeSound by remember { mutableStateOf(prefs.isCompleteSoundEnabled()) }
    var vibrate by remember { mutableStateOf(prefs.isVibrateEnabled()) }
    var useSystemFont by remember { mutableStateOf(prefs.isUseSystemFont()) }
    var giftCode by remember { mutableStateOf(TextFieldValue("")) }
    var redeeming by remember { mutableStateOf(false) }
    var restoring by remember { mutableStateOf(false) }
    var hasPass by remember { mutableStateOf(prefs.load().hasLifetimePass) }
    var passPriceText by remember { mutableStateOf<String?>(null) }
    var buyingPass by remember { mutableStateOf(false) }

    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }

    fun sync() {
        val data = prefs.load()
        if (stepMin.text.isBlank() && stepSec.text.isBlank()) {
            stepMin = TextFieldValue((data.stepSeconds / 60).toString())
            stepSec = TextFieldValue((data.stepSeconds % 60).toString())
        }
        layoutMode = data.layoutMode
        hasPass = data.hasLifetimePass
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) sync() }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    // 평생이용권 가격(formattedPrice) 조회. SKU 미등록이면 null → '구매' 라벨만.
    LaunchedEffect(Unit) {
        passPriceText = withContext(Dispatchers.IO) {
            BillingManager.lifetimePassDetails(context)
        }?.oneTimePurchaseOfferDetails?.formattedPrice
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("설정", color = AppColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)

        // ── 타이머 ──
        SectionHeader("타이머")
        SettingRow("시간 조절") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                CompactField(stepMin, { stepMin = it }, width = 48.dp, keyboardType = KeyboardType.Number, textAlign = TextAlign.Center)
                Text("분", color = AppColors.Brown, fontSize = 13.sp)
                CompactField(stepSec, { stepSec = it }, width = 48.dp, keyboardType = KeyboardType.Number, textAlign = TextAlign.Center)
                Text("초", color = AppColors.Brown, fontSize = 13.sp)
                SmallButton("저장") {
                    val m = stepMin.text.toIntOrNull() ?: 0
                    val s = stepSec.text.toIntOrNull() ?: 0
                    val total = m * 60 + s
                    if (total < 5) {
                        toast(context, "5초보다 짧게는 조절할 수 없어요")
                    } else {
                        TimerController.setStepSeconds(context, total)
                        // 클램프된 실제 저장값으로 입력칸을 다시 동기화.
                        val saved = TimerPreferences.get(context).load().stepSeconds
                        stepMin = TextFieldValue((saved / 60).toString())
                        stepSec = TextFieldValue((saved % 60).toString())
                        toast(context, "한 번에 ${saved / 60}분 ${saved % 60}초씩 조절돼요")
                    }
                }
            }
        }
        SettingRow("타이머 위치") {
            SegmentedToggle(
                options = listOf("위", "아래"),
                selectedIndex = if (layoutMode == LayoutMode.TOP) 0 else 1,
            ) { idx ->
                val mode = if (idx == 0) LayoutMode.TOP else LayoutMode.BOTTOM
                layoutMode = mode
                TimerController.setLayoutMode(context, mode)
            }
        }

        // ── 알림 ──
        SectionHeader("알림")
        SettingRow("완료음") {
            ThemedSwitch(completeSound) {
                completeSound = it
                prefs.setCompleteSoundEnabled(it)
            }
        }
        SettingRow("진동") {
            ThemedSwitch(vibrate) {
                vibrate = it
                prefs.setVibrateEnabled(it)
            }
        }
        SettingRow(
            title = "시스템 알림 설정",
            subtitle = "진행·완료 알림을 폰 설정에서 변경",
            onClick = { openAppNotificationSettings(context) },
        ) {
            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = AppColors.Brown)
        }

        // ── 표시 ──
        SectionHeader("표시")
        SettingRow("앱 글꼴") {
            SegmentedToggle(
                options = listOf("기본", "시스템 글꼴"),
                selectedIndex = if (useSystemFont) 1 else 0,
            ) { idx ->
                val system = idx == 1
                useSystemFont = system
                prefs.setUseSystemFont(system)
                AppFontState.set(system) // 앱 전체 즉시 재구성
            }
        }

        // ── 기타 ──
        SectionHeader("기타")
        SettingRow(
            title = "위젯 새로고침",
            subtitle = "위젯이 멈췄거나 예전 모습으로 보일 때",
            onClick = {
                TimerNotifications.ensureChannels(context)
                WidgetUpdater.updateAllWidgets(context)
                toast(context, "위젯 새로고침 완료")
            },
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = null, tint = AppColors.Brown)
        }
        SettingRow(
            title = "평생이용권",
            subtitle = if (hasPass) "보유 중"
                else passPriceText?.let { "$it · 프리스티지 제외 일괄 해금" }
                    ?: "프리스티지 제외 유료 테마 일괄 해금",
        ) {
            if (hasPass) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = AppColors.Primary)
            } else {
                SmallButton(if (buyingPass) "확인 중…" else "구매", enabled = !buyingPass) {
                    val act = context.findActivity()
                    if (act == null) {
                        toast(context, "결제를 준비 중이에요")
                    } else {
                        scope.launch {
                            buyingPass = true
                            val details = withContext(Dispatchers.IO) {
                                BillingManager.lifetimePassDetails(context)
                            }
                            buyingPass = false
                            // 결과는 비동기 도착 → ON_RESUME의 sync()에서 hasPass 갱신.
                            if (details == null) toast(context, "결제를 준비 중이에요")
                            else BillingManager.launchPurchase(act, details)
                        }
                    }
                }
            }
        }
        SettingRow(
            title = "구매 복원",
            subtitle = "기기를 바꿨거나 앱을 다시 설치했을 때",
            onClick = if (restoring) null else {
                {
                    scope.launch {
                        restoring = true
                        // Play 구매내역을 다시 조회해 권한을 복원한다. productId→skinId는 catalog에서.
                        val map = withContext(Dispatchers.IO) {
                            runCatching { SkinDownloader.fetchCatalog(SkinRepoUrls.CATALOG_URL) }
                                .getOrNull()
                                ?.mapNotNull { e -> e.productId?.let { it to e.skinId } }
                                ?.toMap()
                                .orEmpty()
                        }
                        BillingManager.syncEntitlements(context, map)
                        restoring = false
                        toast(context, "구매 내역을 확인했어요")
                    }
                }
            },
        ) {
            if (restoring) {
                Text("확인 중…", color = AppColors.Brown, fontSize = 13.sp)
            } else {
                Icon(Icons.Filled.Restore, contentDescription = null, tint = AppColors.Brown)
            }
        }
        SettingRow("기프트코드") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CompactField(giftCode, { giftCode = it }, width = 120.dp, enabled = !redeeming)
                SmallButton(if (redeeming) "확인 중…" else "해금", enabled = !redeeming && giftCode.text.isNotBlank()) {
                    val code = giftCode.text
                    scope.launch {
                        redeeming = true
                        val result = withContext(Dispatchers.IO) { GiftCodeRedeemer.redeem(context, code) }
                        redeeming = false
                        when (result) {
                            is GiftCodeRedeemer.Result.Success -> {
                                giftCode = TextFieldValue("")
                                unlockedSkin = result.skinId to result.name // 다이얼로그로 후속 안내
                            }
                            is GiftCodeRedeemer.Result.AlreadyOwned ->
                                toast(context, "이미 보유한 테마예요 (${result.name})")
                            GiftCodeRedeemer.Result.Invalid -> toast(context, "앗, 코드를 다시 확인해 주세요")
                            GiftCodeRedeemer.Result.Error -> toast(context, "연결이 불안정해요. 잠시 후 다시 시도해 주세요")
                        }
                    }
                }
            }
        }
        SettingRow("앱 버전") {
            Text(if (versionName.isNotBlank()) "v$versionName" else "—", color = AppColors.Brown, fontSize = 13.sp)
        }
    }

    // 기프트코드 해금 성공 안내 → 보유 목록(해금 항목 포커싱)으로 이동.
    unlockedSkin?.let { (skinId, name) ->
        AlertDialog(
            onDismissRequest = { unlockedSkin = null },
            icon = { Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = AppColors.Primary) },
            title = { Text("$name 해금 완료!", color = AppColors.TextPrimary) },
            text = { Text("보유 목록에서 확인해볼까요?", color = AppColors.Brown) },
            shape = RoundedCornerShape(20.dp),
            containerColor = AppColors.Background,
            confirmButton = {
                TextButton(onClick = {
                    unlockedSkin = null
                    onGoToVault(skinId)
                }) { Text("보유 목록으로", color = AppColors.Primary, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { unlockedSkin = null }) {
                    Text("닫기", color = AppColors.Brown)
                }
            },
        )
    }
}

// ---- 재사용 컴포넌트 ----

@Composable
private fun SectionHeader(label: String) {
    Text(
        label,
        color = AppColors.Primary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
    )
}

/** 좌측 제목(+부제) / 우측 컨트롤 행. onClick이 있으면 행 전체가 탭 가능(액션 행). */
@Composable
private fun SettingRow(
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    val base = Modifier
        .fillMaxWidth()
    val clickable = if (onClick != null) base.clickable(onClick = onClick) else base
    Row(
        modifier = clickable.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = AppColors.TextPrimary, fontSize = 14.sp)
            if (subtitle != null) {
                Text(subtitle, color = AppColors.Brown, fontSize = 11.sp)
            }
        }
        Box(contentAlignment = Alignment.CenterEnd) { trailing() }
    }
}

/** 버튼 높이에 맞춘 컴팩트 입력칸(약 42dp). 기본 OutlinedTextField(56dp)보다 낮다. */
@Composable
private fun CompactField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    width: Dp,
    enabled: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    textAlign: TextAlign = TextAlign.Start,
) {
    val fieldTextColor by animateColorAsState(
        if (enabled) AppColors.TextPrimary else AppColors.Brown, label = "fieldText"
    )
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        textStyle = TextStyle(color = fieldTextColor, fontSize = 15.sp, textAlign = textAlign),
        cursorBrush = SolidColor(AppColors.Primary),
        modifier = Modifier
            .width(width)
            .height(42.dp)
            .alpha(if (enabled) 1f else 0.55f) // 비활성(확인 중) 시 흐리게
            .clip(RoundedCornerShape(10.dp))
            .background(if (enabled) AppColors.Surface else AppColors.CardCream)
            .border(1.dp, AppColors.Stroke, RoundedCornerShape(10.dp)),
        decorationBox = { inner ->
            Box(
                modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                contentAlignment = if (textAlign == TextAlign.Center) Alignment.Center else Alignment.CenterStart,
            ) { inner() }
        },
    )
}

@Composable
private fun ThemedSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = AppColors.OnPrimary,
            checkedTrackColor = AppColors.Primary,
            checkedBorderColor = AppColors.Primary,
        ),
    )
}

/** 작은 알약형 세그먼트 토글(2개). 선택 칸은 주황 채움. */
@Composable
private fun SegmentedToggle(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(AppColors.CardCream),
    ) {
        options.forEachIndexed { idx, label ->
            val selected = idx == selectedIndex
            val bg by animateColorAsState(
                if (selected) AppColors.Primary else Color.Transparent, label = "segBg"
            )
            val fg by animateColorAsState(
                if (selected) AppColors.OnPrimary else AppColors.Brown, label = "segFg"
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(bg)
                    .clickable { onSelect(idx) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            ) {
                Text(label, color = fg, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun SmallButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AppColors.Primary, contentColor = AppColors.OnPrimary,
        ),
    ) { Text(label, fontSize = 13.sp) }
}

private fun toast(context: android.content.Context, msg: String) =
    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

/** 폰의 앱 알림 설정 화면으로 이동(진행/완료 채널 포함). 실패 시 일반 앱 설정으로 폴백. */
private fun openAppNotificationSettings(context: android.content.Context) {
    val notif = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val ok = runCatching { context.startActivity(notif); true }.getOrDefault(false)
    if (!ok) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(android.net.Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
