package com.blackandblue.justshare.data.billing

import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.Purchase
import com.blackandblue.justshare.data.UserPreferencesDataStore
import com.blackandblue.justshare.data.remote.QuotaApiService
import com.blackandblue.justshare.data.remote.TelemetryEvent
import com.blackandblue.justshare.data.repository.QuotaRepository
import com.revenuecat.purchases.Purchases
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Orchestrates the purchase verification flow:
 *
 * 1. Observes [BillingClientWrapper.purchaseResults]
 * 2. On a [PurchaseResult.Success], sends the purchase token to the backend for verification
 * 3. **Only after** server confirmation: acknowledges (Pro) or consumes (Data Pack) the purchase
 * 4. Triggers [QuotaRepository.refresh] to update the UI
 *
 * Granting entitlements locally before server verification is intentionally prevented —
 * the server is the only source of truth for both Pro status and data-pack credits.
 *
 * Retry logic: if the backend call fails, it is retried up to [MAX_RETRIES] times with
 * exponential backoff. Google auto-refunds unacknowledged purchases after 3 days, so
 * persistent failures on app reopen are also covered by [BillingClientWrapper]'s
 * pending-purchase recovery.
 */
@Singleton
class PurchaseRepository @Inject constructor(
    private val billingClientWrapper: BillingClientWrapper,
    private val quotaRepository: QuotaRepository,
    private val apiService: QuotaApiService,
    private val dataStore: UserPreferencesDataStore,
    @Named("quotaApiBaseUrl") private val baseUrl: String
) {
    companion object {
        private const val TAG = "PurchaseRepository"
        private const val MAX_RETRIES = 5
        private const val PRO_PRODUCT_ID = "pro_unlock"
        private const val DATA_PACK_PRODUCT_ID = "data_pack_10gb"
        private val SUPPORT_PRODUCT_IDS = setOf(
            "student_developer_tip", // Legacy purchases remain consumable.
            "support_developer_1",
            "support_developer_10",
            "support_developer_100",
        )
    }

    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _verificationResults = MutableSharedFlow<PurchaseVerificationResult>(extraBufferCapacity = 8)
    val verificationResults: SharedFlow<PurchaseVerificationResult> =
        _verificationResults.asSharedFlow()

    /** Start observing purchase results. Call once from the DI graph initializer or Application. */
    fun startObserving() {
        if (observing) return
        observing = true
        billingClientWrapper.purchaseResults
            .onEach { result ->
                if (result is PurchaseResult.Success) {
                    handlePurchase(result.purchase)
                }
            }
            .launchIn(repoScope)
    }

    @Volatile
    private var observing = false

    private suspend fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return

        val deviceId = dataStore.ensureDeviceId()
        val productId = purchase.products.firstOrNull() ?: return

        Log.d(TAG, "Handling purchase: productId=$productId")

        // Retry with exponential backoff — must not silently abandon (3-day Google refund window)
        var attempt = 0
        var delayMs = 2_000L
        while (attempt < MAX_RETRIES) {
            try {
                val body = JSONObject().apply {
                    put("deviceId", deviceId)
                    put("productId", productId)
                    put("purchaseToken", purchase.purchaseToken)
                }.toString()

                val (code, _) = apiService.postForJson(
                    "$baseUrl/purchase/verify", deviceId, body
                )

                if (code in 200..299) {
                    if (productId in SUPPORT_PRODUCT_IDS && productId != "student_developer_tip") {
                        // Save the promised acknowledgement before consumption; a process death
                        // after consumption would otherwise lose the only UI success event.
                        dataStore.markSupportPurchaseVerified(productId)
                    }
                    if (Purchases.isConfigured) {
                        Purchases.sharedInstance.syncPurchases()
                    }
                    // Server confirmed — now safe to finalize on the client
                    check(finalizeOnClient(purchase, productId)) {
                        "Google Play could not finalize the verified purchase"
                    }
                    quotaRepository.refresh()
                    reportPurchaseTelemetry(deviceId, productId)
                    _verificationResults.emit(PurchaseVerificationResult.Verified(productId))
                    Log.d(TAG, "Purchase verified and finalized: $productId")
                    return
                } else if (code == 400) {
                    // Bad token — don't retry, token is invalid
                    Log.e(TAG, "Purchase verification rejected by server (400): $productId")
                    _verificationResults.emit(
                        PurchaseVerificationResult.Error(
                            productId,
                            "Google Play purchase verification was rejected.",
                        ),
                    )
                    return
                }
                // 5xx or network error — retry below
            } catch (e: Exception) {
                Log.w(TAG, "Purchase verification attempt ${attempt + 1} failed", e)
            }

            attempt++
            if (attempt < MAX_RETRIES) {
                delay(delayMs)
                delayMs = minOf(delayMs * 2, 60_000L)
            }
        }
        Log.e(TAG, "Purchase verification exhausted retries for $productId — will retry on next app open via pending-purchase recovery")
        _verificationResults.emit(
            PurchaseVerificationResult.Error(
                productId,
                "Purchase verification is delayed. Google Play will retry safely.",
            ),
        )
    }

    /**
     * Acknowledge (non-consumable Pro) or Consume (consumable Data Pack) the purchase.
     * Called only after server-side verification succeeds.
     */
    private suspend fun finalizeOnClient(purchase: Purchase, productId: String): Boolean {
        val billingClient = billingClientWrapper.billingClient
        if (!billingClient.isReady) return false

        if (productId == PRO_PRODUCT_ID) {
            if (!purchase.isAcknowledged) {
                val params = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                val result = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
                    billingClient.acknowledgePurchase(params) { billingResult ->
                        if (continuation.isActive) continuation.resume(billingResult)
                    }
                }
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    Log.w(TAG, "Acknowledge failed: ${result.debugMessage}")
                    return false
                }
            }
        } else if (productId == DATA_PACK_PRODUCT_ID || productId in SUPPORT_PRODUCT_IDS) {
            // Consume repeatable data packs and support purchases after verification.
            val params = ConsumeParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            val result = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
                billingClient.consumeAsync(params) { billingResult, _ ->
                    if (continuation.isActive) continuation.resume(billingResult)
                }
            }
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.w(TAG, "Consume failed: ${result.debugMessage}")
                return false
            }
        }
        return true
    }

    private fun reportPurchaseTelemetry(deviceId: String, productId: String) {
        repoScope.launch {
            val eventName = when (productId) {
                PRO_PRODUCT_ID -> TelemetryEvent.PRO_PURCHASED
                DATA_PACK_PRODUCT_ID -> TelemetryEvent.PACK_PURCHASED
                in SUPPORT_PRODUCT_IDS -> TelemetryEvent.SUPPORT_TIP_PURCHASED
                else -> return@launch
            }
            apiService.reportTelemetry(TelemetryEvent(deviceId = deviceId, name = eventName))
        }
    }
}

sealed class PurchaseVerificationResult {
    data class Verified(val productId: String) : PurchaseVerificationResult()
    data class Error(val productId: String, val message: String) : PurchaseVerificationResult()
}
