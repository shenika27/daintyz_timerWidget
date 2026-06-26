package com.daintyz.timerwidget.ui.compose

import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.daintyz.timerwidget.controller.TimerController
import com.daintyz.timerwidget.data.TimerPreferences
import com.daintyz.timerwidget.model.LayoutMode
import com.daintyz.timerwidget.model.TimerState
import com.daintyz.timerwidget.notification.TimerNotifications
import com.daintyz.timerwidget.skin.GiftCodeRedeemer
import com.daintyz.timerwidget.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 설정 탭 — 섹션(타이머/알림/표시/기타)으로 묶은 행 리스트.
 * 토글/세그먼트/딥링크 등 행 성격에 맞는 컨트롤을 우측에 둔다.
 */
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { TimerPreferences.get(context) }

    var stateLabel by remember { mutableStateOf("") }
    var remaining by remember { mutableStateOf("") }
    var step by remember { mutableStateOf(TextFieldValue("")) }
    var layoutMode by remember { mutableStateOf(LayoutMode.TOP) }
    var completeSound by remember { mutableStateOf(prefs.isCompleteSoundEnabled()) }
    var vibrate by remember { mutableStateOf(prefs.isVibrateEnabled()) }
    var useSystemFont by remember { mutableStateOf(prefs.isUseSystemFont()) }
    var giftCode by remember { mutableStateOf(TextFieldValue("")) }
    var redeeming by remember { mutableStateOf(false) }

    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }

    fun sync() {
        val data = prefs.load()
        stateLabel = when (data.state) {
            TimerState.IDLE -> "정지"
            TimerState.RUNNING -> "진행 중"
            TimerState.PAUSED -> "일시정지"
            TimerState.COMPLETE -> "완료"
        }
        val ms = data.remainingMillis(SystemClock.elapsedRealtime())
        val totalSeconds = (ms + 999) / 1000
        remaining = "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)
        if (step.text.isBlank()) step = TextFieldValue(data.stepMinutes.toString())
        layoutMode = data.layoutMode
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) sync() }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
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

        // 현재 상태 카드.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(AppColors.CardCream)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text("현재 상태", color = AppColors.Brown, fontSize = 12.sp)
            Text(
                "$stateLabel · $remaining",
                color = AppColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold,
            )
        }

        // ── 타이머 ──
        SectionHeader("타이머")
        SettingRow("증감 단위") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = step,
                    onValueChange = { step = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(76.dp),
                )
                SmallButton("저장") {
                    val v = step.text.toIntOrNull()
                    if (v == null || v < 1) {
                        toast(context, "1 이상의 숫자를 입력하세요")
                    } else {
                        TimerController.setStepMinutes(context, v)
                        toast(context, "증감 단위 ${v}분으로 저장됨")
                    }
                }
            }
        }
        SettingRow("위젯 배치") {
            SegmentedToggle(
                options = listOf("타이머 위", "타이머 아래"),
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
            title = "위젯 강제 새로고침",
            subtitle = "위젯이 멈췄거나 옛 스킨을 보일 때",
            onClick = {
                TimerNotifications.ensureChannels(context)
                WidgetUpdater.updateAllWidgets(context)
                toast(context, "위젯 새로고침 완료")
            },
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = null, tint = AppColors.Brown)
        }
        SettingRow("기프트코드") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = giftCode,
                    onValueChange = { giftCode = it },
                    singleLine = true,
                    enabled = !redeeming,
                    modifier = Modifier.width(120.dp),
                )
                SmallButton(if (redeeming) "확인 중…" else "해금", enabled = !redeeming && giftCode.text.isNotBlank()) {
                    val code = giftCode.text
                    scope.launch {
                        redeeming = true
                        val result = withContext(Dispatchers.IO) { GiftCodeRedeemer.redeem(context, code) }
                        redeeming = false
                        val msg = when (result) {
                            is GiftCodeRedeemer.Result.Success -> {
                                giftCode = TextFieldValue("")
                                "${result.name} 해금 완료!"
                            }
                            is GiftCodeRedeemer.Result.AlreadyOwned -> "이미 보유한 테마예요 (${result.name})"
                            GiftCodeRedeemer.Result.Invalid -> "유효하지 않은 코드예요"
                            GiftCodeRedeemer.Result.Error -> "네트워크 오류 — 잠시 후 다시 시도하세요"
                        }
                        toast(context, msg)
                    }
                }
            }
        }
        SettingRow("앱 버전") {
            Text(if (versionName.isNotBlank()) "v$versionName" else "—", color = AppColors.Brown, fontSize = 13.sp)
        }
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
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (selected) AppColors.Primary else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { onSelect(idx) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            ) {
                Text(
                    label,
                    color = if (selected) AppColors.OnPrimary else AppColors.Brown,
                    fontSize = 13.sp,
                )
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
