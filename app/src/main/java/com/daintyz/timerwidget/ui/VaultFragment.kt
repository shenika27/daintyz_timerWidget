package com.daintyz.timerwidget.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.daintyz.timerwidget.R
import com.daintyz.timerwidget.controller.TimerController
import com.daintyz.timerwidget.data.TimerPreferences
import com.daintyz.timerwidget.model.RemoteSkinEntry
import com.daintyz.timerwidget.model.Skin
import com.daintyz.timerwidget.model.TimerState
import com.daintyz.timerwidget.skin.FrameAnimationController
import com.daintyz.timerwidget.skin.SkinAvailabilityChecker
import com.daintyz.timerwidget.skin.SkinDownloader
import com.daintyz.timerwidget.skin.SkinRepository
import com.google.android.material.button.MaterialButton

/**
 * 보유(테마) 화면 — 보유/미보유를 합친 단일 커버플로우 캐러셀.
 *
 * 상단 필터: `구매한 항목만` / `즐겨찾기` 두 토글(독립 체크, 기본 off=전체). 정렬은 즐겨찾기 → 보유 → 미보유.
 * 보유 카드는 포커스 시 run 프레임으로 움직이고(위젯 진행 모습), 미보유는 정적 썸네일 + 딤 + 자물쇠.
 * 적용=캐릭터+타이머 동시 적용. 미리보기=상점과 동일한 상세([SkinDetailActivity]).
 *
 * 즐겨찾기 주의: ★토글이 켜진 상태에서 개별 카드의 별을 끄더라도 **그 자리에서 즉시 사라지지 않는다**.
 * 별 토글은 저장 + 그 카드의 별 아이콘만 갱신할 뿐, 표시 목록은 스냅샷이라 **다음 재조회(탭 재진입/
 * 토글 변경/구매·다운 완료) 때** 비로소 빠진다.
 */
class VaultFragment : Fragment(R.layout.fragment_vault) {

    private companion object {
        const val FRAME_TICK_MS = 100L
        const val PEEK_DP = 52
        const val CATALOG_URL = "https://raw.githubusercontent.com/shenika27/daintyz_timer_characterList/main/catalog.json"
        const val TAG = "VaultFragment"
    }

    private lateinit var pager: ViewPager2
    private lateinit var tvName: TextView
    private lateinit var tvPosition: TextView
    private lateinit var btnApply: MaterialButton
    private lateinit var btnPreview: MaterialButton
    private lateinit var tvEmpty: TextView
    private lateinit var chipOwned: ImageView
    private lateinit var chipFav: ImageView

    // ---- 상태 ----
    private var ownedOnly = false
    private var favOnly = false
    private var favoriteIds: MutableSet<String> = mutableSetOf()
    private var localSkins: List<Skin> = emptyList()
    private var catalogEntries: List<RemoteSkinEntry> = emptyList()
    private var appliedSkinId: String? = null
    private val downloadingIds = mutableSetOf<String>()

    /** 현재 화면에 표시 중인 스냅샷 목록. */
    private var displayItems: List<VaultItem> = emptyList()
    private var adapter: VaultPagerAdapter? = null
    private var lastFocus = -1

    private val handler = Handler(Looper.getMainLooper())
    private val frameTick = object : Runnable {
        override fun run() {
            animateFocused()
            handler.postDelayed(this, FRAME_TICK_MS)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        pager = view.findViewById(R.id.vp_carousel)
        tvName = view.findViewById(R.id.tv_theme_name)
        tvPosition = view.findViewById(R.id.tv_position)
        btnApply = view.findViewById(R.id.btn_apply)
        btnPreview = view.findViewById(R.id.btn_preview)
        tvEmpty = view.findViewById(R.id.tv_vault_empty)
        chipOwned = view.findViewById(R.id.chip_owned)
        chipFav = view.findViewById(R.id.chip_fav)

        setupPagerOnce()

        chipOwned.isSelected = ownedOnly
        chipFav.isSelected = favOnly
        chipOwned.setOnClickListener {
            ownedOnly = !ownedOnly
            chipOwned.isSelected = ownedOnly
            rebuild(currentFocusId())
        }
        chipFav.setOnClickListener {
            favOnly = !favOnly
            chipFav.isSelected = favOnly
            rebuild(currentFocusId())
        }

        fetchCatalog()
    }

    override fun onResume() {
        super.onResume()
        // 재진입/상세화면 복귀 = '새로 조회'. 여기서 스냅샷이 갱신되며 즐겨찾기 해제분도 반영된다.
        reloadAndRebuild()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(frameTick)
    }

    private fun setupPagerOnce() {
        pager.offscreenPageLimit = 1
        pager.setPageTransformer(CoverFlowTransformer())
        (pager.getChildAt(0) as? RecyclerView)?.apply {
            val peek = dp(PEEK_DP)
            setPadding(peek, 0, peek, 0)
            clipToPadding = false
        }
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) = onFocusChanged(position)
        })
        handler.removeCallbacks(frameTick)
        handler.post(frameTick)
    }

    // ---- 데이터 로드/조회 ----

    private fun reloadAndRebuild() {
        val prefs = TimerPreferences.get(requireContext())
        favoriteIds = prefs.loadFavoriteSkinIds().toMutableSet()
        val data = prefs.load()
        localSkins = SkinRepository.loadAllSkins(requireContext())
        appliedSkinId = if (data.selectedCharacterSkinId == data.selectedTimerSkinId)
            data.selectedCharacterSkinId else null
        rebuild(currentFocusId() ?: appliedSkinId)
    }

    private fun fetchCatalog() {
        Thread {
            runCatching { SkinDownloader.fetchCatalog(CATALOG_URL) }
                .onSuccess { entries ->
                    activity?.runOnUiThread {
                        if (!isAdded || view == null) return@runOnUiThread
                        catalogEntries = entries
                        rebuild(currentFocusId() ?: appliedSkinId)
                    }
                }
                .onFailure { Log.w(TAG, "카탈로그 로드 실패 (오프라인이거나 URL 미설정)", it) }
        }.start()
    }

    /** 현재 토글 조건으로 표시 목록(스냅샷)을 만든다. 정렬: 즐겨찾기 → 보유 → 미보유. */
    private fun buildDisplayList(): List<VaultItem> {
        val data = TimerPreferences.get(requireContext()).load()
        val localIds = localSkins.map { it.skinId }.toSet()

        val all = buildList {
            for (skin in localSkins) {
                val owned = SkinAvailabilityChecker.isSkinAvailable(skin, data.purchasedSkinIds, data.hasLifetimePass)
                add(VaultItem.Local(skin, owned))
            }
            for (entry in catalogEntries) {
                if (entry.skinId !in localIds) add(VaultItem.Remote(entry))
            }
        }

        var filtered = all
        if (ownedOnly) filtered = filtered.filter { it.owned }
        if (favOnly) filtered = filtered.filter { it.id in favoriteIds }

        // 안정 정렬: 즐겨찾기 먼저, 그 다음 보유.
        return filtered.sortedWith(
            compareByDescending<VaultItem> { it.id in favoriteIds }.thenByDescending { it.owned }
        )
    }

    private fun rebuild(preserveFocusId: String?) {
        displayItems = buildDisplayList()
        if (displayItems.isEmpty()) {
            showEmpty()
            return
        }
        hideEmpty()

        val a = VaultPagerAdapter(displayItems, favoriteIds, appliedSkinId, ::onStarClicked)
        adapter = a
        pager.adapter = a

        val pos = preserveFocusId
            ?.let { id -> displayItems.indexOfFirst { it.id == id } }
            ?.takeIf { it >= 0 } ?: 0
        lastFocus = -1
        pager.setCurrentItem(pos, false)
        onFocusChanged(pos)
    }

    private fun currentFocusId(): String? =
        if (displayItems.isEmpty()) null else displayItems.getOrNull(pager.currentItem)?.id

    // ---- 즐겨찾기 ----

    /**
     * 카드 별 토글. 저장 + favoriteIds만 갱신한다. 아이콘 교체/팝 애니메이션은 어댑터가 처리하므로
     * 여기서 notifyItemChanged로 재바인딩하지 않는다(애니메이션이 리바인드에 씹히는 것 방지).
     */
    private fun onStarClicked(position: Int) {
        val item = displayItems.getOrNull(position) ?: return
        val nowFav = item.id !in favoriteIds
        if (nowFav) favoriteIds.add(item.id) else favoriteIds.remove(item.id)
        TimerPreferences.get(requireContext()).setFavorite(item.id, nowFav)
    }

    // ---- 포커스 갱신 ----

    private fun onFocusChanged(position: Int) {
        if (lastFocus >= 0 && lastFocus != position) resetIdle(lastFocus)
        lastFocus = position
        val item = displayItems.getOrNull(position) ?: return
        tvName.text = item.name
        tvPosition.text = getString(R.string.vault_position, position + 1, displayItems.size)
        updateActionButtons(item)
    }

    private fun updateActionButtons(item: VaultItem) {
        btnApply.visibility = View.VISIBLE
        if (item.owned) {
            val applied = item.id == appliedSkinId
            // 적용중이면 회색 비활성 '적용중' 상태로 그대로 둔다.
            btnApply.text = getString(if (applied) R.string.vault_in_use else R.string.vault_apply)
            btnApply.isEnabled = !applied
            btnApply.setOnClickListener(if (applied) null else View.OnClickListener { applyTheme(item) })
        } else when {
            item.id in downloadingIds -> {
                btnApply.text = getString(R.string.skin_btn_downloading)
                btnApply.isEnabled = false
                btnApply.setOnClickListener(null)
            }
            item is VaultItem.Remote && item.isFree -> {
                btnApply.text = getString(R.string.skin_btn_download)
                btnApply.isEnabled = true
                btnApply.setOnClickListener { startDownload(item.entry) }
            }
            else -> {
                btnApply.text = getString(R.string.vault_buy)
                btnApply.isEnabled = true
                btnApply.setOnClickListener { buyStub() }
            }
        }
        btnPreview.setOnClickListener { openDetail(item) }
    }

    private fun applyTheme(item: VaultItem) {
        TimerController.selectCharacterSkin(requireContext(), item.id)
        TimerController.selectTimerSkin(requireContext(), item.id)
        appliedSkinId = item.id
        adapter?.setApplied(item.id)
        updateActionButtons(item)
        Toast.makeText(
            requireContext(), getString(R.string.vault_applied_toast, item.name), Toast.LENGTH_SHORT
        ).show()
    }

    // ---- 캐릭터 프레임 애니메이션 (보유 카드 한정) ----

    private fun animateFocused() {
        val recycler = pager.getChildAt(0) as? RecyclerView ?: return
        val pos = pager.currentItem
        val item = displayItems.getOrNull(pos) ?: return
        if (item !is VaultItem.Local || !item.owned) return  // 미보유는 정적 썸네일 유지
        val holder = recycler.findViewHolderForAdapterPosition(pos) as? VaultPagerAdapter.CardHolder ?: return
        val ctx = FrameAnimationController.FrameContext(remainingMs = 0L, elapsedMs = 0L, totalMs = 0L)
        val file = FrameAnimationController.currentFrameFile(
            item.skin, TimerState.RUNNING, ctx, SystemClock.elapsedRealtime()
        )
        SkinRepository.loadFrameBitmap(requireContext(), item.id, file)
            ?.let { holder.character.setImageBitmap(it) }
    }

    private fun resetIdle(position: Int) {
        val item = displayItems.getOrNull(position) as? VaultItem.Local ?: return
        if (!item.owned) return
        val recycler = pager.getChildAt(0) as? RecyclerView ?: return
        val holder = recycler.findViewHolderForAdapterPosition(position) as? VaultPagerAdapter.CardHolder ?: return
        item.skin.character.stop.frames.firstOrNull()
            ?.let { SkinRepository.loadFrameBitmap(requireContext(), item.id, it) }
            ?.let { holder.character.setImageBitmap(it) }
    }

    // ---- 구매/다운로드/미리보기 ----

    private fun buyStub() {
        Toast.makeText(requireContext(), getString(R.string.store_buy_stub), Toast.LENGTH_SHORT).show()
    }

    private fun startDownload(entry: RemoteSkinEntry) {
        downloadingIds.add(entry.skinId)
        displayItems.getOrNull(pager.currentItem)?.let { if (it.id == entry.skinId) updateActionButtons(it) }
        SkinDownloader.download(
            context = requireContext().applicationContext,
            entry = entry,
            onProgress = { },
            onComplete = { success ->
                activity?.runOnUiThread {
                    if (!isAdded || view == null) return@runOnUiThread
                    downloadingIds.remove(entry.skinId)
                    val msg = if (success)
                        "${entry.name} ${getString(R.string.skin_download_complete)}"
                    else getString(R.string.skin_download_fail)
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                    // 완료 = 재조회: 로컬 목록 갱신 후 해당 테마로 복귀(보유로 전환).
                    localSkins = SkinRepository.loadAllSkins(requireContext())
                    rebuild(entry.skinId)
                }
            }
        )
    }

    private fun openDetail(item: VaultItem) {
        val intent = Intent(requireContext(), SkinDetailActivity::class.java).apply {
            putExtra(SkinDetailActivity.EXTRA_SKIN_ID, item.id)
            putExtra(SkinDetailActivity.EXTRA_NAME, item.name)
            putExtra(SkinDetailActivity.EXTRA_IS_FREE, item.isFree)
            putExtra(SkinDetailActivity.EXTRA_PRICE, item.price)
            putExtra(SkinDetailActivity.EXTRA_PRESTIGE, item.prestige)
            putExtra(SkinDetailActivity.EXTRA_OWNED, item.owned)
            if (item is VaultItem.Remote) {
                putExtra(SkinDetailActivity.EXTRA_ZIP_URL, item.entry.zipUrl)
                putExtra(SkinDetailActivity.EXTRA_PREVIEW_BASE, item.entry.baseUrl)
            }
        }
        startActivity(intent)
    }

    // ---- 빈 상태 ----

    private fun showEmpty() {
        tvEmpty.text = getString(if (favOnly) R.string.vault_fav_empty else R.string.vault_locked_empty)
        tvEmpty.visibility = View.VISIBLE
        for (v in listOf<View>(pager, tvName, tvPosition, btnApply, btnPreview)) v.visibility = View.GONE
    }

    private fun hideEmpty() {
        tvEmpty.visibility = View.GONE
        for (v in listOf<View>(pager, tvName, tvPosition, btnApply, btnPreview)) v.visibility = View.VISIBLE
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()
}
