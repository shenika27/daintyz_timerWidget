package com.daintyz.timerwidget.billing

import android.content.Context
import com.daintyz.timerwidget.data.TimerPreferences
import com.daintyz.timerwidget.model.RemoteSkinEntry
import com.daintyz.timerwidget.model.Skin
import com.daintyz.timerwidget.model.TimerData
import com.daintyz.timerwidget.skin.SkinAvailabilityChecker
import com.daintyz.timerwidget.skin.SkinDownloader
import com.daintyz.timerwidget.skin.SkinRepository

object EntitlementCleanup {

    data class Result(
        val deletedSkinIds: Set<String>,
        val characterFallbackApplied: Boolean,
        val timerFallbackApplied: Boolean,
    )

    fun cleanup(context: Context, catalog: List<RemoteSkinEntry>): Result {
        val appContext = context.applicationContext
        val prefs = TimerPreferences.get(appContext)
        val catalogById = catalog.associateBy { it.skinId }
        val data = prefs.load()
        val skins = SkinRepository.loadAllSkins(appContext)
        val revokedSkinIds = revokedDownloadedSkinIds(
            downloadedSkins = skins.filterNot { it.bundled },
            catalogById = catalogById,
            data = data,
        )
        val deletedSkinIds = revokedSkinIds
            .filter { SkinDownloader.deleteDownloaded(appContext, it) }
            .toSet()
        if (deletedSkinIds.isNotEmpty()) SkinRepository.clearCache()

        val refreshedSkins = SkinRepository.loadAllSkins(appContext)
        val fallbackSkinId = bundledFreeFallbackSkinId(SkinRepository.loadBundledSkins(appContext))
        val sanitized = sanitizeSelections(
            data = data,
            localSkins = refreshedSkins,
            catalogById = catalogById,
            fallbackSkinId = fallbackSkinId,
        )
        if (sanitized != data) prefs.save(sanitized)

        return Result(
            deletedSkinIds = deletedSkinIds,
            characterFallbackApplied = sanitized.selectedCharacterSkinId != data.selectedCharacterSkinId,
            timerFallbackApplied = sanitized.selectedTimerSkinId != data.selectedTimerSkinId,
        )
    }

    internal fun revokedDownloadedSkinIds(
        downloadedSkins: List<Skin>,
        catalogById: Map<String, RemoteSkinEntry>,
        data: TimerData,
    ): Set<String> = downloadedSkins
        .filter { skin ->
            val entry = catalogById[skin.skinId]
            val isFree = entry?.isFree ?: skin.isFree
            val prestige = entry?.prestige ?: skin.prestige
            !isFree && !SkinAvailabilityChecker.isSkinAvailable(
                skinId = skin.skinId,
                isFree = isFree,
                prestige = prestige,
                purchasedSkinIds = data.purchasedSkinIds,
                hasLifetimePass = data.hasLifetimePass,
                giftUnlockedSkinIds = data.giftUnlockedSkinIds,
            )
        }
        .map { it.skinId }
        .toSet()

    internal fun sanitizeSelections(
        data: TimerData,
        localSkins: List<Skin>,
        catalogById: Map<String, RemoteSkinEntry>,
        fallbackSkinId: String,
    ): TimerData {
        fun sanitize(skinId: String): String =
            if (isSelectionUsable(skinId, localSkins, catalogById, data)) skinId else fallbackSkinId

        return data.copy(
            selectedCharacterSkinId = sanitize(data.selectedCharacterSkinId),
            selectedTimerSkinId = sanitize(data.selectedTimerSkinId),
        )
    }

    internal fun bundledFreeFallbackSkinId(bundledSkins: List<Skin>): String {
        val bundledFree = bundledSkins.filter { it.bundled && it.isFree }
        return bundledFree.firstOrNull { it.skinId == TimerPreferences.DEFAULT_SKIN_ID }?.skinId
            ?: bundledFree.firstOrNull()?.skinId
            ?: TimerPreferences.DEFAULT_SKIN_ID
    }

    private fun isSelectionUsable(
        skinId: String,
        localSkins: List<Skin>,
        catalogById: Map<String, RemoteSkinEntry>,
        data: TimerData,
    ): Boolean {
        val skin = localSkins.firstOrNull { it.skinId == skinId } ?: return false
        val entry = catalogById[skinId]
        return SkinAvailabilityChecker.isSkinAvailable(
            skinId = skinId,
            isFree = entry?.isFree ?: skin.isFree,
            prestige = entry?.prestige ?: skin.prestige,
            purchasedSkinIds = data.purchasedSkinIds,
            hasLifetimePass = data.hasLifetimePass,
            giftUnlockedSkinIds = data.giftUnlockedSkinIds,
        )
    }
}
