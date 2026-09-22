package com.blackandblue.justshare.presentation.billing

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.billingclient.api.ProductDetails
import com.blackandblue.justshare.data.billing.BillingClientWrapper
import com.blackandblue.justshare.data.billing.PurchaseResult
import com.blackandblue.justshare.data.billing.PurchaseRepository
import com.blackandblue.justshare.data.billing.PurchaseVerificationResult
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
        const val SUPPORT_TIP_PRODUCT_ID = "student_developer_tip"
    }

    // ── Quota ────────────────────────────────────────────────────────────────

    val quotaState: StateFlow<QuotaState> = quotaRepository.quotaState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QuotaState.loading())

    // ── Products ─────────────────────────────────────────────────────────────

    private val _proProductDetails = MutableStateFlow<ProductDetails?>(null)
    val proProductDetails: StateFlow<ProductDetails?> = _proProductDetails.asStateFlow()

    private val _dataPackProductDetails = MutableStateFlow<ProductDetails?>(null)
    val dataPackProductDetails: StateFlow<ProductDetails?> = _dataPackProductDetails.asStateFlow()

    private val _supportTipProductDetails = MutableStateFlow<ProductDetails?>(null)
    val supportTipProductDetails: StateFlow<ProductDetails?> =
        _supportTipProductDetails.asStateFlow()

    // ── Purchase State ───────────────────────────────────────────────────────

    private val _purchaseState = MutableStateFlow<PurchaseState>(PurchaseState.Idle)
    val purchaseState: StateFlow<PurchaseState> = _purchaseState.asStateFlow()
    private var pendingProductId: String? = null

    init {
        loadProducts()
        observePurchaseResults()
        observeVerificationResults()
        quotaRepository.refresh()
    }

    // ── Actions ──────────────────────────────────────────────────────────────

    fun purchasePro(activity: Activity) {
        val details = _proProductDetails.value ?: return
        pendingProductId = PRO_PRODUCT_ID
        _purchaseState.value = PurchaseState.Loading
        billingClientWrapper.launchPurchaseFlow(activity, details)
    }

    fun purchaseDataPack(activity: Activity) {
        val details = _dataPackProductDetails.value ?: return
        pendingProductId = DATA_PACK_PRODUCT_ID
        _purchaseState.value = PurchaseState.Loading
        billingClientWrapper.launchPurchaseFlow(activity, details)
    }

    fun purchaseSupportTip(activity: Activity) {
        val details = _supportTipProductDetails.value ?: return
        pendingProductId = SUPPORT_TIP_PRODUCT_ID
        _purchaseState.value = PurchaseState.Loading
        billingClientWrapper.launchPurchaseFlow(activity, details)
    }

    fun resetPurchaseState() {
        pendingProductId = null
        _purchaseState.value = PurchaseState.Idle
    }

    // ── Private Helpers ──────────────────────────────────────────────────────

    private fun loadProducts() {
        viewModelScope.launch {
            repeat(5) { attempt ->
                val products = billingClientWrapper.queryProducts(
                    listOf(PRO_PRODUCT_ID, DATA_PACK_PRODUCT_ID, SUPPORT_TIP_PRODUCT_ID)
                )
                _proProductDetails.value = products.firstOrNull { it.productId == PRO_PRODUCT_ID }
                _dataPackProductDetails.value = products.firstOrNull { it.productId == DATA_PACK_PRODUCT_ID }
                _supportTipProductDetails.value =
                    products.firstOrNull { it.productId == SUPPORT_TIP_PRODUCT_ID }
                if (products.isNotEmpty()) return@launch
                if (attempt < 4) delay(1_000)
            }
        }
    }

    private fun observePurchaseResults() {
        billingClientWrapper.purchaseResults
            .onEach { result ->
                val nextState = when (result) {
                    is PurchaseResult.Success -> {
                        val expected = pendingProductId
                        if (expected == null || expected !in result.purchase.products) return@onEach
                        PurchaseState.Verifying
                    }
                    is PurchaseResult.Cancelled -> {
                        pendingProductId = null
                        PurchaseState.Idle
                    }
                    is PurchaseResult.Error -> {
                        pendingProductId = null
                        PurchaseState.Error(result.message)
                    }
                }
                _purchaseState.value = nextState
            }
            .launchIn(viewModelScope)
    }

    private fun observeVerificationResults() {
        purchaseRepository.verificationResults
            .onEach { result ->
                if (result.productIdOrNull() != pendingProductId) return@onEach
                _purchaseState.value = when (result) {
                    is PurchaseVerificationResult.Verified -> PurchaseState.Success
                    is PurchaseVerificationResult.Error -> PurchaseState.Error(result.message)
                }
                pendingProductId = null
            }
            .launchIn(viewModelScope)
    }
}

sealed class PurchaseState {
    object Idle : PurchaseState()
    object Loading : PurchaseState()
    object Verifying : PurchaseState()
    object Success : PurchaseState()
    data class Error(val message: String) : PurchaseState()
}

private fun PurchaseVerificationResult.productIdOrNull(): String = when (this) {
    is PurchaseVerificationResult.Verified -> productId
    is PurchaseVerificationResult.Error -> productId
}
