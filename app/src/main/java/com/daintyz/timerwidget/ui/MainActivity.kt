package com.daintyz.timerwidget.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.core.content.ContextCompat
import com.daintyz.timerwidget.R
import com.daintyz.timerwidget.skin.SkinRepoUrls
import com.daintyz.timerwidget.ui.compose.AppColors
import com.daintyz.timerwidget.ui.compose.AppTheme
import com.daintyz.timerwidget.ui.compose.SettingsScreen
import com.daintyz.timerwidget.ui.compose.StoreScreen
import com.daintyz.timerwidget.ui.compose.VaultScreen

/**
 * 앱 셸 (Compose). 하단 탭(보유/상점/설정)으로 세 화면을 전환한다. 첫 화면은 보유(창고).
 * 위젯은 RemoteViews이므로 이 화면과 무관하다.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_NAV = "nav_tab"
        const val NAV_VAULT = "vault"
        const val NAV_STORE = "store"
        const val NAV_SETTINGS = "settings"

        /** 다른 화면에서 보유(창고) 탭으로 돌아오게 하는 인텐트. */
        fun vaultIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_NAV, NAV_VAULT)
            }
    }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    /** 외부 인텐트가 요청한 탭 인덱스. onNewIntent로 갱신되면 Compose가 반영. */
    private val requestedTab = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedTab.intValue = tabFromIntent(intent)

        setContent {
            AppTheme {
                AppShell(
                    requestedTab = requestedTab.intValue,
                    onOpenDetail = ::openDetail,
                )
            }
        }

        requestNotificationPermissionIfNeeded()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedTab.intValue = tabFromIntent(intent)
    }

    private fun tabFromIntent(intent: Intent): Int = when (intent.getStringExtra(EXTRA_NAV)) {
        NAV_STORE -> 1
        NAV_SETTINGS -> 2
        else -> 0
    }

    /** VaultItem 상세/미리보기 화면(Compose 캐러셀)으로 이동. 보유=상태 스와이프, 미보유=prev 스와이프. */
    private fun openDetail(item: VaultItem) {
        startActivity(Intent(this, DetailActivity::class.java).apply {
            putExtra(DetailActivity.EXTRA_SKIN_ID, item.id)
            putExtra(DetailActivity.EXTRA_NAME, item.name)
            putExtra(DetailActivity.EXTRA_OWNED, item.owned)
            putExtra(DetailActivity.EXTRA_IS_FREE, item.isFree)
            putExtra(DetailActivity.EXTRA_PRICE, item.price)
            putExtra(DetailActivity.EXTRA_PRESTIGE, item.prestige)
            if (item is VaultItem.Remote) {
                putExtra(DetailActivity.EXTRA_ZIP_URL, item.entry.zipUrl)
                putExtra(DetailActivity.EXTRA_PREVIEW_BASE, item.entry.baseUrl)
            } else {
                putExtra(DetailActivity.EXTRA_PREVIEW_BASE, SkinRepoUrls.ASSET_BASE)
            }
        })
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

@Composable
private fun AppShell(requestedTab: Int, onOpenDetail: (VaultItem) -> Unit) {
    var selected by remember { mutableStateOf(requestedTab) }
    LaunchedEffect(requestedTab) { selected = requestedTab }

    Scaffold(
        containerColor = AppColors.Background,
        bottomBar = {
            NavigationBar(containerColor = AppColors.Surface) {
                NavTab(0, selected, R.drawable.ic_nav_vault, R.string.nav_vault) { selected = 0 }
                NavTab(1, selected, R.drawable.ic_nav_store, R.string.nav_store) { selected = 1 }
                NavTab(2, selected, R.drawable.ic_nav_settings, R.string.nav_settings) { selected = 2 }
            }
        },
    ) { padding ->
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().padding(padding)) {
            when (selected) {
                1 -> StoreScreen(onOpenDetail = onOpenDetail)
                2 -> SettingsScreen()
                else -> VaultScreen(onOpenDetail = onOpenDetail)
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.NavTab(
    index: Int,
    selected: Int,
    iconRes: Int,
    labelRes: Int,
    onClick: () -> Unit,
) {
    NavigationBarItem(
        selected = selected == index,
        onClick = onClick,
        icon = { Icon(painterResource(iconRes), contentDescription = null) },
        label = { Text(androidx.compose.ui.res.stringResource(labelRes)) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = AppColors.Primary,
            selectedTextColor = AppColors.Primary,
            unselectedIconColor = AppColors.Brown,
            unselectedTextColor = AppColors.Brown,
            indicatorColor = AppColors.CardCream,
        ),
    )
}
