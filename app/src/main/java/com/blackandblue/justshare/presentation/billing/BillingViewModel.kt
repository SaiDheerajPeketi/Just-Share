package com.blackandblue.justshare.presentation.billing

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.blackandblue.justshare.data.billing.BillingClientWrapper
import com.blackandblue.justshare.data.billing.CheckoutStatus
import com.blackandblue.justshare.data.billing.PurchaseRepository
import com.blackandblue.justshare.data.billing.StudentSupportProducts
import com.blackandblue.justshare.data.repository.QuotaRepository
import com.blackandblue.justshare.domain.billing.QuotaState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import javax.inject.Inject

/**
 * ViewModel exposing billing + quota state for the Settings screen,
 * Pro/Upgrade screen, and the AlterSend Remote upsell dialog.
 *
 * Responsibilities:
 * - Holds [ProductDetails] fetched from Play for the Pro and Data Pack products
 * - Exposes [purchaseState] for button loading/error/success feedback
 * - Delegates to [BillingClientWrapper.launchPurchaseFlow] for actual purchases
 * - Surfaces [quotaState] from [QuotaRepository] for display
 */
@HiltViewModel
class BillingViewModel @Inject constructor(
    private val billingClientWrapper: BillingClientWrapper,
    private val quotaRepository: QuotaRepository,
    private val purchaseRepository: PurchaseRepository,
) : ViewModel() {

    companion object {
        const val PRO_PRODUCT_ID = "pro_unlock"
        const val DATA_PACK_PRODUCT_ID = "data_pack_10gb"
        val SUPPORT_PRODUCT_IDS = StudentSupportProducts.tiers
    }

    // ── Quota ────────────────────────────────────────────────────────────────

    val quotaState: StateFlow<QuotaState> = quotaRepository.quotaState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QuotaState.loading())

    // ── Products ─────────────────────────────────────────────────────────────

    private val _proProductDetails = MutableStateFlow<ProductDetails?>(null)
    val proProductDetails: StateFlow<ProductDetails?> = _proProductDetails.asStateFlow()

    private val _dataPackProductDetails = MutableStateFlow<ProductDetails?>(null)
    val dataPackProductDetails: StateFlow<ProductDetails?> = _dataPackProductDetails.asStateFlow()

    private val _supportProductDetails = MutableStateFlow<Map<String, ProductDetails>>(emptyMap())
    val supportProductDetails: StateFlow<Map<String, ProductDetails>> =
        _supportProductDetails.asStateFlow()

    // ── Purchase State ───────────────────────────────────────────────────────

    private val _purchaseState = MutableStateFlow<PurchaseState>(PurchaseState.Idle)
    val purchaseState: StateFlow<PurchaseState> = _purchaseState.asStateFlow()

    init {
        loadProducts()
        observeCheckoutStatus()
        quotaRepository.refresh()
    }

    // ── Actions ──────────────────────────────────────────────────────────────

    fun purchasePro(activity: Activity) {
        if (_purchaseState.value.inProgress) return
        val details = _proProductDetails.value ?: return
        billingClientWrapper.launchPurchaseFlow(activity, details)
    }

    fun purchaseDataPack(activity: Activity) {
        if (_purchaseState.value.inProgress) return
        val details = _dataPackProductDetails.value ?: return
        billingClientWrapper.launchPurchaseFlow(activity, details)
    }

    fun purchaseSupport(activity: Activity, productId: String) {
        if (productId !in SUPPORT_PRODUCT_IDS || _purchaseState.value.inProgress) return
        val details = _supportProductDetails.value[productId] ?: return
        if (!StudentSupportProducts.isAvailable(
                details.productId, details.productType, details.oneTimePurchaseOfferDetails?.formattedPrice,
            )) return
        billingClientWrapper.launchPurchaseFlow(activity, details)
    }

    fun resetPurchaseState() { billingClientWrapper.resetFeedback() }
    fun recheckPurchase() { billingClientWrapper.recoverPendingPurchases() }

    // ── Private Helpers ──────────────────────────────────────────────────────

    private fun loadProducts() {
        viewModelScope.launch {
            val requestedProductIds =
                listOf(PRO_PRODUCT_ID, DATA_PACK_PRODUCT_ID) + SUPPORT_PRODUCT_IDS.keys
            repeat(5) { attempt ->
                val products = billingClientWrapper.queryProducts(requestedProductIds)
                _proProductDetails.value = products.firstOrNull { it.productId == PRO_PRODUCT_ID }
                _dataPackProductDetails.value = products.firstOrNull { it.productId == DATA_PACK_PRODUCT_ID }
                _supportProductDetails.value = products
                    .filter { StudentSupportProducts.isAvailable(
                        it.productId, it.productType, it.oneTimePurchaseOfferDetails?.formattedPrice,
                    ) }
                    .groupBy { it.productId }.values.mapNotNull { it.singleOrNull() }
                    .associateBy { it.productId }
                if (requestedProductIds.all { id -> products.any { it.productId == id } }) {
                    return@launch
                }
                if (attempt < 4) delay(1_000)
            }
        }
    }

    private fun observeCheckoutStatus() {
        billingClientWrapper.checkoutStatus.onEach { status ->
            _purchaseState.value = when (status) {
                CheckoutStatus.Idle -> PurchaseState.Idle
                CheckoutStatus.Unavailable -> PurchaseState.Error(null, "Payment recovery is unavailable. Checkout stays paused to protect your orders.")
                is CheckoutStatus.Loading -> PurchaseState.Loading(status.product)
                is CheckoutStatus.Pending -> PurchaseState.Pending(status.product)
                is CheckoutStatus.Verifying -> PurchaseState.Verifying(status.product)
                is CheckoutStatus.Completed -> PurchaseState.Success(status.product)
                is CheckoutStatus.Failed -> PurchaseState.Error(status.product, status.message)
            }
        }.launchIn(viewModelScope)
    }

}

sealed class PurchaseState {
    val inProgress: Boolean
        get() = this is Loading || this is Verifying || this is Pending
    object Idle : PurchaseState()
    data class Loading(val productId: String) : PurchaseState()
    data class Verifying(val productId: String) : PurchaseState()
    data class Pending(val productId: String) : PurchaseState()
    data class Success(val productId: String) : PurchaseState()
    data class Error(val productId: String?, val message: String) : PurchaseState()
}

internal fun purchaseStateAfterStoreUpdate(productId: String, storeState: Int): PurchaseState =
    when (storeState) {
        Purchase.PurchaseState.PENDING -> PurchaseState.Pending(productId)
        Purchase.PurchaseState.PURCHASED -> PurchaseState.Verifying(productId)
        else -> PurchaseState.Error(productId, "Google Play has not confirmed this purchase.")
    }
