package com.daintyz.timerwidget.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.daintyz.timerwidget.R
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * 앱 셸 (설계 문서 3-2, 2-1).
 *
 * 하단 탭 네비게이션(보유/상점/설정)으로 세 프래그먼트를 전환한다. 진입 첫 화면은 보유(창고) 캐러셀.
 * 첫 실행 시 알림 권한(POST_NOTIFICATIONS)을 요청한다(Android 13+).
 */
class MainActivity : AppCompatActivity() {

    companion object {
        /** 외부(상세 화면 등)에서 특정 탭으로 진입시킬 때 쓰는 인텐트 extra. 값은 메뉴 아이템 이름. */
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
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* 결과 무시: 거부해도 앱은 동작 */ }

    private lateinit var bottomNav: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bottomNav = findViewById(R.id.bottom_nav)
        bottomNav.setOnItemSelectedListener { item ->
            showFragment(item.itemId)
            true
        }

        if (savedInstanceState == null) {
            bottomNav.selectedItemId = navItemFromIntent(intent)
        }

        requestNotificationPermissionIfNeeded()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        bottomNav.selectedItemId = navItemFromIntent(intent)
    }

    private fun navItemFromIntent(intent: Intent): Int = when (intent.getStringExtra(EXTRA_NAV)) {
        NAV_STORE -> R.id.nav_store
        NAV_SETTINGS -> R.id.nav_settings
        else -> R.id.nav_vault
    }

    private fun showFragment(itemId: Int) {
        val fragment: Fragment = when (itemId) {
            R.id.nav_store -> StoreFragment()
            R.id.nav_settings -> SettingsFragment()
            else -> VaultFragment()
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.nav_host, fragment)
            .commit()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
