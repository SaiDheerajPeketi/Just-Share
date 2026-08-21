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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
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
    }

    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Start observing purchase results. Call once from the DI graph initializer or Application. */
    fun startObserving() {
        billingClientWrapper.purchaseResults
            .onEach { result ->
                if (result is PurchaseResult.Success) {
                    handlePurchase(result.purchase)
                }
            }
            .launchIn(repoScope)
    }

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
                    // Server confirmed — now safe to finalize on the client
                    finalizeOnClient(purchase, productId)
                    quotaRepository.refresh()
                    reportPurchaseTelemetry(deviceId, productId)
                    Log.d(TAG, "Purchase verified and finalized: $productId")
                    return
                } else if (code == 400) {
                    // Bad token — don't retry, token is invalid
                    Log.e(TAG, "Purchase verification rejected by server (400): $productId")
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
    }

    /**
     * Acknowledge (non-consumable Pro) or Consume (consumable Data Pack) the purchase.
     * Called only after server-side verification succeeds.
     */
    private suspend fun finalizeOnClient(purchase: Purchase, productId: String) {
        val billingClient = billingClientWrapper.billingClient
        if (!billingClient.isReady) return

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
                }
            }
        } else if (productId == DATA_PACK_PRODUCT_ID) {
            // Consume so the user can purchase another data pack in the future
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
            }
        }
    }

    private fun reportPurchaseTelemetry(deviceId: String, productId: String) {
        repoScope.launch {
            val eventName = when (productId) {
                PRO_PRODUCT_ID -> TelemetryEvent.PRO_PURCHASED
                DATA_PACK_PRODUCT_ID -> TelemetryEvent.PACK_PURCHASED
                else -> return@launch
            }
            apiService.reportTelemetry(TelemetryEvent(deviceId = deviceId, name = eventName))
        }
    }
}
