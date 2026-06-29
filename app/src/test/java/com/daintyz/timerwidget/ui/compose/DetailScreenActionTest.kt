package com.daintyz.timerwidget.ui.compose

import org.junit.Assert.assertEquals
import org.junit.Test

class DetailScreenActionTest {

    @Test
    fun local_renderable_without_entitlement_requires_purchase_instead_of_apply() {
        assertEquals(
            DetailPrimaryAction.BUY,
            detailPrimaryAction(
                localRenderable = true,
                entitled = false,
                applied = false,
                downloading = false,
                saleExpired = false,
            )
        )
    }

    @Test
    fun local_renderable_with_entitlement_can_apply() {
        assertEquals(
            DetailPrimaryAction.APPLY,
            detailPrimaryAction(
                localRenderable = true,
                entitled = true,
                applied = false,
                downloading = false,
                saleExpired = false,
            )
        )
    }

    @Test
    fun entitled_without_local_renderable_skin_downloads_first() {
        assertEquals(
            DetailPrimaryAction.DOWNLOAD,
            detailPrimaryAction(
                localRenderable = false,
                entitled = true,
                applied = false,
                downloading = false,
                saleExpired = false,
            )
        )
    }
}
