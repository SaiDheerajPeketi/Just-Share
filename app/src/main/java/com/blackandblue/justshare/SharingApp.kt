package com.blackandblue.justshare

import timber.log.Timber

import android.app.Application
import com.blackandblue.justshare.data.billing.BillingClientWrapper
import com.blackandblue.justshare.data.billing.PurchaseRepository
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesAreCompletedBy
import com.revenuecat.purchases.PurchasesConfiguration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class SharingApp: Application() {
    @Inject lateinit var billingClientWrapper: BillingClientWrapper
    @Inject lateinit var purchaseRepository: PurchaseRepository

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        JustShareTelemetry.initialize(this)
        JustShareTelemetry.log("app_open")
        configureRevenueCat()
        purchaseRepository.startObserving()
        billingClientWrapper.startConnection()
    }

    private fun configureRevenueCat() {
        val apiKey = BuildConfig.REVENUECAT_API_KEY.trim()
        if (!apiKey.startsWith("goog_") || Purchases.isConfigured) return
        Purchases.configure(
            PurchasesConfiguration.Builder(this, apiKey)
                .purchasesAreCompletedBy(PurchasesAreCompletedBy.MY_APP)
                .diagnosticsEnabled(false)
                .build()
        )
    }
}
