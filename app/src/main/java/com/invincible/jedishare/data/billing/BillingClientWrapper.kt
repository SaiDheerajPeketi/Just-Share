package com.invincible.jedishare.data.billing

import android.app.Activity
import android.util.Log
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.math.min

/**
 * Wraps the Google Play Billing Library 9.x [BillingClient], managing its lifecycle,
 * reconnection, and purchase events.
 *
 * Callers subscribe to [purchaseResults] to receive purchase updates; they must
 * forward tokens to the server for verification before granting any entitlement.
 *
 * Thread-safety: all BillingClient calls are dispatched on the Main thread as required
 * by the library; state updates are emitted as SharedFlow to allow collection anywhere.
 */
@Singleton
class BillingClientWrapper @Inject constructor(
    private val applicationContext: android.content.Context
) {
    companion object {
        private const val TAG = "BillingClientWrapper"
        private const val MAX_RETRY_DELAY_MS = 30_000L
    }

    private val wrapperScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _purchaseResults = MutableSharedFlow<PurchaseResult>(extraBufferCapacity = 8)
    /** Emits whenever a purchase completes (success or failure). Observe in [PurchaseRepository]. */
    val purchaseResults: SharedFlow<PurchaseResult> = _purchaseResults.asSharedFlow()

    private var retryDelayMs = 1_000L

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { purchase ->
                    _purchaseResults.tryEmit(PurchaseResult.Success(purchase))
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                _purchaseResults.tryEmit(PurchaseResult.Cancelled)
            }
            else -> {
                _purchaseResults.tryEmit(
                    PurchaseResult.Error(billingResult.responseCode, billingResult.debugMessage)
                )
            }
        }
    }

    val billingClient: BillingClient = BillingClient.newBuilder(applicationContext)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .build()

    // ── Connection ───────────────────────────────────────────────────────────

    /** Connect to Play and auto-reconnect on disconnect with exponential backoff. */
    fun startConnection() {
        if (billingClient.isReady) return
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    retryDelayMs = 1_000L
                    Log.d(TAG, "BillingClient connected")
                    // Recover any unfinished purchases from previous sessions
                    recoverPendingPurchases()
                } else {
                    Log.w(TAG, "Billing setup failed: ${result.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Billing service disconnected — retrying in ${retryDelayMs}ms")
                wrapperScope.launch {
                    delay(retryDelayMs)
                    retryDelayMs = min(retryDelayMs * 2, MAX_RETRY_DELAY_MS)
                    startConnection()
                }
            }
        })
    }

    // ── Product Details ──────────────────────────────────────────────────────

    /**
     * Query Play for product details for the given [productIds].
     * Returns an empty list on any error — UI should handle gracefully.
     */
    suspend fun queryProducts(productIds: List<String>): List<ProductDetails> =
        withContext(Dispatchers.IO) {
            if (!billingClient.isReady) return@withContext emptyList()
            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(
                    productIds.map { id ->
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(id)
                            .setProductType(
                                if (id.endsWith("_monthly")) BillingClient.ProductType.SUBS
                                else BillingClient.ProductType.INAPP
                            )
                            .build()
                    }
                )
                .build()
            val result = billingClient.queryProductDetails(params)
            if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                result.productDetailsList ?: emptyList()
            } else {
                Log.w(TAG, "queryProducts failed: ${result.billingResult.debugMessage}")
                emptyList()
            }
        }

    // ── Launch Flow ─────────────────────────────────────────────────────────

    /**
     * Launch the Play billing sheet for [productDetails].
     * Must be called from the UI thread with a valid [Activity].
     * Purchase result will be emitted via [purchaseResults].
     */
    fun launchPurchaseFlow(activity: Activity, productDetails: ProductDetails) {
        val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken
        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .apply { offerToken?.let { setOfferToken(it) } }
            .build()
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()
        billingClient.launchBillingFlow(activity, params)
    }

    // ── Pending Purchase Recovery ────────────────────────────────────────────

    /**
     * Query for any purchases that were completed but not yet acknowledged/consumed
     * (e.g., app crashed after purchase but before acknowledgement).
     * Emits them through [purchaseResults] so [PurchaseRepository] can re-process them.
     */
    private fun recoverPendingPurchases() {
        wrapperScope.launch {
            val inappResult = billingClient.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            )
            if (inappResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                inappResult.purchasesList.filter {
                    it.purchaseState == Purchase.PurchaseState.PURCHASED
                }.forEach { purchase ->
                    Log.d(TAG, "Recovering pending purchase: ${purchase.products}")
                    _purchaseResults.emit(PurchaseResult.Success(purchase))
                }
            }
        }
    }
}

/** Sealed result emitted by [BillingClientWrapper.purchaseResults]. */
sealed class PurchaseResult {
    data class Success(val purchase: Purchase) : PurchaseResult()
    object Cancelled : PurchaseResult()
    data class Error(val responseCode: Int, val message: String) : PurchaseResult()
}
