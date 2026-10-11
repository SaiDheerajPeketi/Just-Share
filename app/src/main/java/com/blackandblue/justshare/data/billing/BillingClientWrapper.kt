package com.blackandblue.justshare.data.billing

import android.app.Activity
import android.util.Log
import com.blackandblue.justshare.data.UserPreferencesDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.math.min

/**
 * Wraps Google Play Billing Library 8.3 [BillingClient], managing its lifecycle,
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
    private val applicationContext: android.content.Context,
    private val dataStore: UserPreferencesDataStore,
) {
    companion object {
        private const val TAG = "BillingClientWrapper"
        private const val MAX_RETRY_DELAY_MS = 30_000L
    }

    private val wrapperScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _purchaseResults = MutableSharedFlow<PurchaseResult>(extraBufferCapacity = 8)
    /** Emits whenever a purchase completes (success or failure). Observe in [PurchaseRepository]. */
    val purchaseResults: SharedFlow<PurchaseResult> = _purchaseResults.asSharedFlow()

    private val protectedPurchase = ProtectedPurchase()
    private val _checkoutStatus = MutableStateFlow<CheckoutStatus>(
        if (!SupportCheckout.attach(applicationContext)) CheckoutStatus.Unavailable
        else SupportCheckout.pending?.let { CheckoutStatus.Pending(it.product) } ?: CheckoutStatus.Idle,
    )
    val checkoutStatus: StateFlow<CheckoutStatus> = _checkoutStatus.asStateFlow()
    private var launchTicket: Long? = null
    private var retryDelayMs = 1_000L

    private val purchasesUpdatedListener = PurchasesUpdatedListener { result, purchases ->
        wrapperScope.launch {
            val ticket = launchTicket
            launchTicket = null
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                purchases.orEmpty().forEach { acceptPurchase(it) }
            } else {
                // Cancellation is authoritative only for this still-active launch, before a token was bound.
                val cancelled = result.responseCode == BillingClient.BillingResponseCode.USER_CANCELED &&
                    ticket != null && SupportCheckout.gate.owns(ticket) && SupportCheckout.pending?.receipt == null &&
                    SupportCheckout.clear()
                if (cancelled) {
                    _checkoutStatus.value = CheckoutStatus.Idle
                    _purchaseResults.emit(PurchaseResult.Cancelled)
                } else publishPending()
            }
            ticket?.let { SupportCheckout.gate.finish(it) }
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
            kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
                billingClient.queryProductDetailsAsync(params) { result, details ->
                    if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                        Log.w(TAG, "queryProducts failed: ${result.debugMessage}")
                    }
                    if (continuation.isActive) {
                        continuation.resume(
                            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                                details.productDetailsList
                            } else {
                                emptyList()
                            }
                        )
                    }
                }
            }
        }

    // ── Launch Flow ─────────────────────────────────────────────────────────

    /**
     * Launch the Play billing sheet for [productDetails].
     * Must be called from the UI thread with a valid [Activity].
     * Purchase result will be emitted via [purchaseResults].
     */
    fun launchPurchaseFlow(activity: Activity, productDetails: ProductDetails) {
        wrapperScope.launch {
            if (productDetails.productId !in checkoutProductIds || productDetails.productType != BillingClient.ProductType.INAPP ||
                productDetails.oneTimePurchaseOfferDetails?.formattedPrice.isNullOrBlank()) return@launch
            val ticket = SupportCheckout.begin()
            if (ticket == null) { publishPending(); return@launch }
            _checkoutStatus.value = CheckoutStatus.Loading(productDetails.productId)
            var dispatched = false
            try {
                val user = dataStore.ensureDeviceId().receiptHash()
                val purchases = currentPurchases() ?: throw IllegalStateException("Play is unavailable")
                // Recover an unfinished order before allowing a different product or another same-price payment.
                val unfinished = purchases.filter { it.products.singleOrNull() in checkoutProductIds &&
                    (it.purchaseState == Purchase.PurchaseState.PENDING ||
                        (it.purchaseState == Purchase.PurchaseState.PURCHASED &&
                            (it.products.singleOrNull() != "pro_unlock" || !it.isAcknowledged))) }
                if (unfinished.isNotEmpty()) {
                    SupportCheckout.gate.finish(ticket)
                    unfinished.forEach { acceptPurchase(it) }
                    publishPending()
                    return@launch
                }
                if (activity.isFinishing || activity.isDestroyed || !SupportCheckout.gate.owns(ticket) ||
                    !SupportCheckout.remember(ticket, user, productDetails.productId,
                        purchases.map { it.purchaseToken.receiptHash() }.toSet())) {
                    throw IllegalStateException("Checkout could not be saved")
                }
                val params = BillingFlowParams.newBuilder().setObfuscatedAccountId(user)
                    .setProductDetailsParamsList(listOf(BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(productDetails).build())).build()
                launchTicket = ticket
                dispatched = true
                val result = billingClient.launchBillingFlow(activity, params)
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    // A synchronous non-OK result means this launch did not start a Play payment flow.
                    launchTicket = null
                    SupportCheckout.gate.finish(ticket)
                    if (SupportCheckout.clear()) {
                        _checkoutStatus.value = CheckoutStatus.Failed(productDetails.productId, "Google Play could not open checkout. Please try again.")
                    } else publishPending()
                }
            } catch (_: Exception) {
                launchTicket = null
                SupportCheckout.gate.finish(ticket)
                if (!dispatched && SupportCheckout.pending == null) {
                    _checkoutStatus.value = CheckoutStatus.Failed(productDetails.productId, "Checkout is unavailable. No payment was started.")
                } else publishPending()
            }
        }
    }

    /** Rechecks both pending orders and paid orders; absence alone never clears an uncertain payment. */
    fun recoverPendingPurchases() {
        wrapperScope.launch {
            if (!SupportCheckout.ready || SupportCheckout.gate.busy) { publishPending(); return@launch }
            val user = runCatching { dataStore.ensureDeviceId().receiptHash() }.getOrNull() ?: return@launch
            val pending = SupportCheckout.pending
            if (pending != null && pending.user != user) { publishPending(); return@launch }
            val current = currentPurchases().orEmpty()
            current.forEach { acceptPurchase(it) }
            // Re-verify the exact encrypted token with the backend even after Play consumed it.
            SupportCheckout.pending?.takeIf { it.user == user }?.let { attempt ->
                protectedPurchase.open(attempt)?.takeIf {
                    current.none { it.purchaseToken.receiptHash() == attempt.receipt }
                }?.let {
                    _purchaseResults.emit(PurchaseResult.Success(it))
                }
            }
            publishPending()
        }
    }

    private suspend fun currentPurchases(): List<Purchase>? {
        if (!billingClient.isReady) return null
        return kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            billingClient.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()) { result, purchases ->
                if (continuation.isActive) continuation.resume(if (result.responseCode == BillingClient.BillingResponseCode.OK) purchases else null)
            }
        }
    }

    private suspend fun acceptPurchase(purchase: Purchase) {
        val product = purchase.products.singleOrNull()?.takeIf { it in checkoutProductIds } ?: return
        if (purchase.purchaseState !in setOf(Purchase.PurchaseState.PENDING, Purchase.PurchaseState.PURCHASED)) return
        val user = runCatching { dataStore.ensureDeviceId().receiptHash() }.getOrNull() ?: return
        val receipt = purchase.purchaseToken.receiptHash()
        if (receipt in SupportCheckout.known(user)) return
        val existing = SupportCheckout.pending
        if (existing == null) {
            if (product == "pro_unlock" && purchase.isAcknowledged) {
                _purchaseResults.emit(PurchaseResult.Success(purchase)) // Preserve server entitlement restoration.
                return
            }
            val ticket = SupportCheckout.begin() ?: return
            val saved = SupportCheckout.remember(ticket, user, product, emptySet())
            SupportCheckout.gate.finish(ticket)
            if (!saved) { publishPending(); return }
        }
        if (SupportCheckout.pending?.let { it.user != user || it.product != product } != false) return
        val sealed = runCatching { protectedPurchase.seal(purchase, user, product, receipt) }.getOrNull()
        if (sealed == null || !SupportCheckout.bindReceipt(user, product, receipt, sealed)) { publishPending(); return }
        _checkoutStatus.value = if (purchase.purchaseState == Purchase.PurchaseState.PENDING) CheckoutStatus.Pending(product)
            else CheckoutStatus.Verifying(product)
        _purchaseResults.emit(PurchaseResult.Success(purchase))
    }

    /** Called only after fresh server proof and Play consumption/acknowledgement match this token. */
    suspend fun finishVerified(purchase: Purchase): Boolean = withContext(Dispatchers.Main) {
        val user = dataStore.ensureDeviceId().receiptHash()
        val product = purchase.products.singleOrNull() ?: return@withContext false
        val receipt = purchase.purchaseToken.receiptHash()
        val pending = SupportCheckout.pending ?: return@withContext true
        // A restored historical order cannot release a different live attempt.
        if (pending.user != user || pending.product != product || pending.receipt != receipt) return@withContext true
        if (!SupportCheckout.finalized(user, product, receipt)) return@withContext false
        launchTicket?.let { SupportCheckout.gate.finish(it) }
        launchTicket = null
        _checkoutStatus.value = CheckoutStatus.Completed(product)
        true
    }
    suspend fun verificationDelayed(purchase: Purchase) = withContext(Dispatchers.Main) {
        if (SupportCheckout.pending?.receipt == purchase.purchaseToken.receiptHash()) publishPending()
    }
    /** A fresh backend read can prove cancellation of the exact bound token after restart. */
    suspend fun finishCancelled(purchase: Purchase): Boolean = withContext(Dispatchers.Main) {
        val attempt = SupportCheckout.pending ?: return@withContext false
        val user = dataStore.ensureDeviceId().receiptHash()
        if (attempt.user != user || attempt.product != purchase.products.singleOrNull() ||
            attempt.receipt != purchase.purchaseToken.receiptHash() || !SupportCheckout.clear()) return@withContext false
        launchTicket?.let { SupportCheckout.gate.finish(it) }
        launchTicket = null
        _checkoutStatus.value = CheckoutStatus.Idle
        true
    }
    private fun publishPending() {
        _checkoutStatus.value = if (!SupportCheckout.ready) CheckoutStatus.Unavailable else
            SupportCheckout.pending?.let { CheckoutStatus.Pending(it.product) } ?: _checkoutStatus.value
    }
    fun resetFeedback() { if (SupportCheckout.ready && SupportCheckout.pending == null && !SupportCheckout.gate.busy) _checkoutStatus.value = CheckoutStatus.Idle }

}

/** Sealed result emitted by [BillingClientWrapper.purchaseResults]. */
sealed class PurchaseResult {
    data class Success(val purchase: Purchase) : PurchaseResult()
    object Cancelled : PurchaseResult()
    data class Error(val responseCode: Int, val message: String) : PurchaseResult()
}

sealed class CheckoutStatus {
    object Idle : CheckoutStatus()
    object Unavailable : CheckoutStatus()
    data class Loading(val product: String) : CheckoutStatus()
    data class Pending(val product: String) : CheckoutStatus()
    data class Verifying(val product: String) : CheckoutStatus()
    data class Completed(val product: String) : CheckoutStatus()
    data class Failed(val product: String, val message: String) : CheckoutStatus()
}
