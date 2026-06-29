package com.daintyz.timerwidget.billing

import android.content.Context
import android.util.Log
import com.daintyz.timerwidget.skin.SkinDownloader
import com.daintyz.timerwidget.skin.SkinRepoUrls
import com.daintyz.timerwidget.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object EntitlementSync {

    private const val TAG = "EntitlementSync"

    data class Result(
        val synced: Boolean,
        val cleanup: EntitlementCleanup.Result? = null,
    )

    suspend fun syncFromPlayAndCleanup(context: Context): Result = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val catalog = runCatching { SkinDownloader.fetchCatalog(SkinRepoUrls.CATALOG_URL) }
            .onFailure { Log.w(TAG, "catalog fetch failed; skip entitlement cleanup", it) }
            .getOrNull()
            ?: return@withContext Result(synced = false)

        val productIdToSkinId = catalog
            .mapNotNull { entry -> entry.productId?.let { it to entry.skinId } }
            .toMap()
        val synced = BillingManager.syncEntitlements(appContext, productIdToSkinId)
        if (!synced) return@withContext Result(synced = false)

        val cleanup = EntitlementCleanup.cleanup(appContext, catalog)
        WidgetUpdater.updateAllWidgets(appContext)
        Result(synced = true, cleanup = cleanup)
    }
}
