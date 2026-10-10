package com.blackandblue.justshare.data.billing

import com.android.billingclient.api.BillingClient
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudentSupportProductsTest {
    @Test
    fun onlyKnownOneTimeProductsWithStorePricesAreAvailable() {
        assertTrue(StudentSupportProducts.isAvailable("support_developer_999", BillingClient.ProductType.INAPP, "$999.00"))
        assertTrue(StudentSupportProducts.isAvailable("support_developer_1", BillingClient.ProductType.INAPP, "₹95"))
        assertFalse(StudentSupportProducts.isAvailable("support_developer_999", BillingClient.ProductType.SUBS, "$999.00"))
        assertFalse(StudentSupportProducts.isAvailable("support_developer_999", BillingClient.ProductType.INAPP, " "))
        assertFalse(StudentSupportProducts.isAvailable("pro_unlock", BillingClient.ProductType.INAPP, "$1"))
    }

    @Test
    fun newAndLegacyTipsRemainConsumableWithoutProOrDataPackBenefits() {
        assertTrue("support_developer_999" in StudentSupportProducts.consumableIds)
        assertTrue("student_developer_tip" in StudentSupportProducts.consumableIds)
        assertFalse("pro_unlock" in StudentSupportProducts.consumableIds)
        assertFalse("data_pack_10gb" in StudentSupportProducts.consumableIds)
    }
}
