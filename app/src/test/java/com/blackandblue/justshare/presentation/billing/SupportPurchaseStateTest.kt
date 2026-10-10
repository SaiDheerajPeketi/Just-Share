package com.blackandblue.justshare.presentation.billing

import com.android.billingclient.api.Purchase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportPurchaseStateTest {
    @Test
    fun pendingPaymentPreventsAnotherCheckoutAndDoesNotClaimVerification() {
        val pending = purchaseStateAfterStoreUpdate("support_developer_999", Purchase.PurchaseState.PENDING)
        assertEquals(PurchaseState.Pending("support_developer_999"), pending)
        assertTrue(pending.inProgress)
        assertEquals(
            PurchaseState.Verifying("support_developer_999"),
            purchaseStateAfterStoreUpdate("support_developer_999", Purchase.PurchaseState.PURCHASED),
        )
    }
}
