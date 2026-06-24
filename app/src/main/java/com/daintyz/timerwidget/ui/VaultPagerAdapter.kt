package com.daintyz.timerwidget.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.daintyz.timerwidget.R
import com.daintyz.timerwidget.skin.RemoteImageLoader
import com.daintyz.timerwidget.skin.SkinRepository

/**
 * 통합 창고 캐러셀 어댑터. 카드 1장 = 테마([VaultItem]) 1개.
 *
 * 보유 카드는 정지 1번 프레임을 보여주고(포커스되면 [VaultFragment]가 run 프레임으로 덮어씀),
 * 미보유 카드는 정적 썸네일 + 딤 + 자물쇠 + 가격칩. 좌상단 별은 즐겨찾기 토글.
 */
class VaultPagerAdapter(
    private val items: List<VaultItem>,
    private val favoriteIds: Set<String>,
    private var appliedSkinId: String?,
    private val onStarClick: (position: Int) -> Unit,
) : RecyclerView.Adapter<VaultPagerAdapter.CardHolder>() {

    class CardHolder(view: View) : RecyclerView.ViewHolder(view) {
        val character: ImageView = view.findViewById(R.id.iv_card_character)
        val dim: View = view.findViewById(R.id.v_lock_dim)
        val lock: ImageView = view.findViewById(R.id.iv_lock)
        val star: ImageView = view.findViewById(R.id.iv_card_star)
        val applied: TextView = view.findViewById(R.id.tv_card_applied)
        val price: TextView = view.findViewById(R.id.tv_card_price)
    }

    fun itemAt(position: Int): VaultItem = items[position]

    /** 적용 상태가 바뀌면 배지를 다시 그린다. */
    fun setApplied(skinId: String?) {
        if (appliedSkinId == skinId) return
        appliedSkinId = skinId
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_vault_card, parent, false)
        return CardHolder(view)
    }

    override fun onBindViewHolder(holder: CardHolder, position: Int) {
        val item = items[position]
        val ctx = holder.itemView.context

        // 캐릭터/썸네일.
        when (item) {
            is VaultItem.Local -> {
                val frame = item.skin.character.stop.frames.firstOrNull()
                    ?.let { SkinRepository.loadFrameBitmap(ctx, item.id, it) }
                if (frame != null) holder.character.setImageBitmap(frame)
                else holder.character.setImageResource(R.drawable.frame_placeholder)
            }
            is VaultItem.Remote -> RemoteImageLoader.load(
                holder.character, item.entry.thumbnailUrl, R.drawable.frame_placeholder
            )
        }

        // 보유/미보유 표시.
        if (item.owned) {
            holder.dim.visibility = View.GONE
            holder.lock.visibility = View.GONE
            holder.price.visibility = View.GONE
            holder.applied.visibility =
                if (item.id == appliedSkinId) View.VISIBLE else View.GONE
        } else {
            holder.dim.visibility = View.VISIBLE
            holder.lock.visibility = View.VISIBLE
            holder.applied.visibility = View.GONE
            holder.price.visibility = View.VISIBLE
            holder.price.text = priceLabel(holder, item)
        }

        // 즐겨찾기 별.
        holder.star.setImageResource(
            if (item.id in favoriteIds) R.drawable.ic_star_filled else R.drawable.ic_star_outline
        )
        holder.star.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
            // 1) 상태 저장(favoriteIds 갱신). 목록 재구성 없이 이 카드의 별만 갱신한다.
            onStarClick(pos)
            // 2) 새 상태로 아이콘 교체 + 팝 애니메이션(귀여운 감성). 색은 교체로, 크기는 오버슈트로.
            holder.star.setImageResource(
                if (items[pos].id in favoriteIds) R.drawable.ic_star_filled
                else R.drawable.ic_star_outline
            )
            popStar(holder.star)
        }
    }

    /** 살짝 작아졌다가 오버슈트로 튀어 원래 크기로 — 가벼운 팝 효과. */
    private fun popStar(star: View) {
        star.animate().cancel()
        star.scaleX = 0.6f
        star.scaleY = 0.6f
        star.alpha = 0.6f
        star.animate()
            .scaleX(1f).scaleY(1f).alpha(1f)
            .setInterpolator(OvershootInterpolator(3.0f))
            .setDuration(260)
            .start()
    }

    /** 가격 칩 라벨: 무료 / 1,200원, 프리스티지면 ✦ 접두. */
    private fun priceLabel(holder: CardHolder, item: VaultItem): String {
        val ctx = holder.itemView.context
        val base = if (item.isFree || item.price <= 0) ctx.getString(R.string.skin_badge_free)
        else ctx.getString(R.string.skin_price_won, "%,d".format(item.price))
        return if (item.prestige) "✦ $base" else base
    }

    override fun getItemCount(): Int = items.size
}
