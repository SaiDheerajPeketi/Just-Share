package com.blackandblue.justshare.data.billing

import com.android.billingclient.api.BillingClient

internal object StudentSupportProducts {
    val tiers = linkedMapOf(
        "support_developer_1" to "Small",
        "support_developer_10" to "Generous",
        "support_developer_100" to "Major",
        "support_developer_999" to "Student support",
    )
    val consumableIds = tiers.keys + "student_developer_tip"

    fun isAvailable(productId: String, productType: String, localizedPrice: String?): Boolean =
        productId in tiers && productType == BillingClient.ProductType.INAPP &&
            !localizedPrice.isNullOrBlank()
}
