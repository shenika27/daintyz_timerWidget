package com.daintyz.timerwidget.ui.compose

import android.os.SystemClock
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.daintyz.timerwidget.controller.TimerController
import com.daintyz.timerwidget.data.TimerPreferences
import com.daintyz.timerwidget.model.LayoutMode
import com.daintyz.timerwidget.model.TimerState
import com.daintyz.timerwidget.skin.GiftCodeRedeemer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 설정 탭 — 증감 단위, 현재 상태/남은시간, 위젯 레이아웃 모드. */
@Composable
fun SettingsScreen() {
    val context = LocalContext.current

    var stateLabel by remember { mutableStateOf("") }
    var remaining by remember { mutableStateOf("") }
    var step by remember { mutableStateOf(TextFieldValue("")) }
    var layoutMode by remember { mutableStateOf(LayoutMode.TOP) }
    var giftCode by remember { mutableStateOf(TextFieldValue("")) }
    var redeeming by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun sync() {
        val data = TimerPreferences.get(context).load()
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
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // 현재 상태.
        Column {
            Text("현재 상태", color = AppColors.Brown, fontSize = 13.sp)
            Text("$stateLabel · $remaining", color = AppColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        // 증감 단위.
        Column {
            Text("증감 단위 (분)", color = AppColors.Brown, fontSize = 13.sp)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = step,
                    onValueChange = { step = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(120.dp),
                )
                Button(
                    onClick = {
                        val v = step.text.toIntOrNull()
                        if (v == null || v < 1) {
                            Toast.makeText(context, "1 이상의 숫자를 입력하세요", Toast.LENGTH_SHORT).show()
                        } else {
                            TimerController.setStepMinutes(context, v)
                            Toast.makeText(context, "증감 단위 ${v}분으로 저장됨", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.Primary, contentColor = AppColors.OnPrimary,
                    ),
                ) { Text("저장") }
            }
        }

        // 위젯 레이아웃 모드.
        Column {
            Text("위젯 배치", color = AppColors.Brown, fontSize = 13.sp)
            LayoutModeOption("타이머 위 (캐릭터 아래)", layoutMode == LayoutMode.TOP) {
                layoutMode = LayoutMode.TOP
                TimerController.setLayoutMode(context, LayoutMode.TOP)
            }
            LayoutModeOption("타이머 아래 (캐릭터 위)", layoutMode == LayoutMode.BOTTOM) {
                layoutMode = LayoutMode.BOTTOM
                TimerController.setLayoutMode(context, LayoutMode.BOTTOM)
            }
        }

        // 기프트코드.
        Column {
            Text("기프트코드", color = AppColors.Brown, fontSize = 13.sp)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = giftCode,
                    onValueChange = { giftCode = it },
                    singleLine = true,
                    enabled = !redeeming,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        val code = giftCode.text
                        scope.launch {
                            redeeming = true
                            val result = withContext(Dispatchers.IO) {
                                GiftCodeRedeemer.redeem(context, code)
                            }
                            redeeming = false
                            val msg = when (result) {
                                is GiftCodeRedeemer.Result.Success -> {
                                    giftCode = TextFieldValue("")
                                    "${result.name} 해금 완료!"
                                }
                                is GiftCodeRedeemer.Result.AlreadyOwned ->
                                    "이미 보유한 테마예요 (${result.name})"
                                GiftCodeRedeemer.Result.Invalid -> "유효하지 않은 코드예요"
                                GiftCodeRedeemer.Result.Error -> "네트워크 오류 — 잠시 후 다시 시도하세요"
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = !redeeming && giftCode.text.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.Primary, contentColor = AppColors.OnPrimary,
                    ),
                ) { Text(if (redeeming) "확인 중…" else "해금") }
            }
        }
    }
}

@Composable
private fun LayoutModeOption(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label, color = AppColors.TextPrimary, modifier = Modifier.padding(start = 4.dp))
    }
}
