package com.blackandblue.justshare.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.runtime.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.blackandblue.justshare.domain.billing.Plan
import com.blackandblue.justshare.presentation.billing.BillingViewModel
import com.blackandblue.justshare.presentation.billing.PurchaseState
import com.blackandblue.justshare.ui.theme.JediShareTheme
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onNavigateToNavRoute: (String) -> Unit,
    transferViewModel: com.blackandblue.justshare.presentation.TransferViewModel,
    billingViewModel: BillingViewModel = hiltViewModel(),
) {
    val colors = JediShareTheme.colors
    val context = androidx.compose.ui.platform.LocalContext.current
    val dataStore = remember {
        dagger.hilt.android.EntryPointAccessors.fromApplication(
            context.applicationContext,
            com.blackandblue.justshare.di.DataStoreEntryPoint::class.java
        ).userPreferencesDataStore()
    }
    val coroutineScope = rememberCoroutineScope()
    val isDarkMode = !JediShareTheme.colors.isLight
    

    val savedTransferMethod by dataStore.defaultTransferMethod.collectAsStateWithLifecycle(initialValue = "wifi")
    val proProduct by billingViewModel.proProductDetails.collectAsStateWithLifecycle()
    val tipProduct by billingViewModel.supportTipProductDetails.collectAsStateWithLifecycle()
    val quotaState by billingViewModel.quotaState.collectAsStateWithLifecycle()
    val purchaseState by billingViewModel.purchaseState.collectAsStateWithLifecycle()
    val activity = context as? android.app.Activity
    val proPrice = proProduct?.oneTimePurchaseOfferDetails?.formattedPrice
    val tipPrice = tipProduct?.oneTimePurchaseOfferDetails?.formattedPrice
    val purchaseBusy = purchaseState is PurchaseState.Loading ||
        purchaseState is PurchaseState.Verifying
    val activeProductId = purchaseState.productIdOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface)
            .statusBarsPadding()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Transparent)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text("Settings", style = MaterialTheme.typography.h3, color = colors.black)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            // Appearance Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.cardBg, RoundedCornerShape(16.dp))
                    .padding(vertical = 16.dp)
            ) {
                Text(
                    text = "APPEARANCE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.mutedFg,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Dark Mode", style = MaterialTheme.typography.body1.copy(fontWeight = FontWeight.Medium), color = colors.black, modifier = Modifier.weight(1f))
                    CustomToggle(
                        on = isDarkMode,
                        onChange = {
                            coroutineScope.launch {
                                dataStore.setDarkMode(!isDarkMode)
                            }
                        },
                        trackOnColor = colors.red,
                        trackOffColor = colors.border,
                        thumbOnColor = Color.White,
                        thumbOffColor = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            // Transfer Defaults Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.cardBg, RoundedCornerShape(16.dp))
                    .padding(vertical = 16.dp)
            ) {
                Text(
                    text = "TRANSFER DEFAULTS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.mutedFg,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                )
                
                // Bluetooth Option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { transferViewModel.setMethod("bt", save = true) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Bluetooth, contentDescription = null, tint = colors.mutedFg, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(text = "Bluetooth", style = MaterialTheme.typography.body1.copy(fontWeight = FontWeight.Medium), color = colors.black, modifier = Modifier.weight(1f))
                    CustomRadioButton(selected = savedTransferMethod == "bt", color = colors.red)
                }
                
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(colors.border))
                
                // Wi-Fi Direct Option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { transferViewModel.setMethod("wifi", save = true) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Wifi, contentDescription = null, tint = colors.mutedFg, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(text = "Wi-Fi Direct", style = MaterialTheme.typography.body1.copy(fontWeight = FontWeight.Medium), color = colors.black, modifier = Modifier.weight(1f))
                    CustomRadioButton(selected = savedTransferMethod == "wifi", color = colors.red)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.cardBg, RoundedCornerShape(16.dp))
                    .padding(vertical = 16.dp),
            ) {
                Text(
                    text = "REMOTE PLAN",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.mutedFg,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            enabled = quotaState.plan != Plan.PRO && activity != null &&
                                proPrice != null && !purchaseBusy,
                        ) {
                            activity?.let(billingViewModel::purchasePro)
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = if (quotaState.plan == Plan.PRO) {
                            "Just Share Pro active"
                        } else {
                            "Upgrade to Just Share Pro"
                        },
                        style = MaterialTheme.typography.body1.copy(fontWeight = FontWeight.Medium),
                        color = colors.black,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = when {
                            quotaState.plan == Plan.PRO ->
                                "Your higher monthly Remote allowance is active"
                            activeProductId == BillingViewModel.PRO_PRODUCT_ID &&
                                purchaseState is PurchaseState.Loading ->
                                "Opening secure Google Play checkout…"
                            activeProductId == BillingViewModel.PRO_PRODUCT_ID &&
                                purchaseState is PurchaseState.Verifying ->
                                "Verifying your Pro purchase securely…"
                            proPrice != null ->
                                "One-time $proPrice purchase for a higher monthly Remote allowance"
                            else -> "Pro purchase unavailable right now"
                        },
                        style = MaterialTheme.typography.body2,
                        color = colors.mutedFg,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Local Bluetooth and Wi-Fi Direct transfers stay free and unlimited. Purchases restore automatically through Google Play.",
                        style = MaterialTheme.typography.caption,
                        color = colors.mutedFg,
                    )
                    when (val state = purchaseState) {
                        is PurchaseState.Success -> if (
                            state.productId == BillingViewModel.PRO_PRODUCT_ID
                        ) {
                            Text(
                                "Pro activated. Refreshing your Remote allowance…",
                                style = MaterialTheme.typography.caption,
                                color = colors.red,
                            )
                        }
                        is PurchaseState.Error -> if (
                            state.productId == BillingViewModel.PRO_PRODUCT_ID
                        ) {
                            Text(
                                "The Pro purchase wasn't completed. ${state.message}",
                                style = MaterialTheme.typography.caption,
                                color = colors.red,
                            )
                        }
                        else -> Unit
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.cardBg, RoundedCornerShape(16.dp))
                    .padding(vertical = 16.dp),
            ) {
                Text(
                    text = "SUPPORT",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.mutedFg,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            enabled = activity != null && tipPrice != null &&
                                !purchaseBusy,
                        ) {
                            activity?.let(billingViewModel::purchaseSupportTip)
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = "Donate to Student Developer",
                        style = MaterialTheme.typography.body1.copy(fontWeight = FontWeight.Medium),
                        color = colors.black,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = when {
                            activeProductId == BillingViewModel.SUPPORT_TIP_PRODUCT_ID &&
                                purchaseState is PurchaseState.Loading ->
                                "Opening secure Google Play checkout…"
                            activeProductId == BillingViewModel.SUPPORT_TIP_PRODUCT_ID &&
                                purchaseState is PurchaseState.Verifying ->
                                "Verifying your tip securely…"
                            tipPrice != null -> "Send a one-time $tipPrice tip; no features are unlocked"
                            else -> "Donation unavailable right now"
                        },
                        style = MaterialTheme.typography.body2,
                        color = colors.mutedFg,
                    )
                    when (val state = purchaseState) {
                        is PurchaseState.Success -> if (
                            state.productId == BillingViewModel.SUPPORT_TIP_PRODUCT_ID
                        ) {
                            Text(
                                "Thank you for supporting this student developer!",
                                style = MaterialTheme.typography.caption,
                                color = colors.red,
                            )
                        }
                        is PurchaseState.Error -> if (
                            state.productId == BillingViewModel.SUPPORT_TIP_PRODUCT_ID
                        ) {
                            Text(
                                "The tip wasn't completed. ${state.message}",
                                style = MaterialTheme.typography.caption,
                                color = colors.red,
                            )
                        }
                        else -> Unit
                    }
                }
            }
        }
    }
}

private fun PurchaseState.productIdOrNull(): String? = when (this) {
    PurchaseState.Idle -> null
    is PurchaseState.Loading -> productId
    is PurchaseState.Verifying -> productId
    is PurchaseState.Success -> productId
    is PurchaseState.Error -> productId
}

@Composable
fun CustomRadioButton(selected: Boolean, color: Color) {
    val colors = JediShareTheme.colors
    Box(
        modifier = Modifier
            .size(20.dp)
            .border(2.dp, if (selected) color else colors.border, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(color, CircleShape)
            )
        }
    }
}

@Composable
fun CustomToggle(
    on: Boolean, 
    onChange: () -> Unit,
    trackOnColor: Color,
    trackOffColor: Color,
    thumbOnColor: Color,
    thumbOffColor: Color,
    thumbIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    thumbIconTint: Color = Color.White
) {
    val animatedTrackColor by androidx.compose.animation.animateColorAsState(targetValue = if (on) trackOnColor else trackOffColor)
    val animatedThumbColor by androidx.compose.animation.animateColorAsState(targetValue = if (on) thumbOnColor else thumbOffColor)
    val thumbOffset by androidx.compose.animation.core.animateDpAsState(targetValue = if (on) 24.dp else 2.dp)

    Box(
        modifier = Modifier
            .size(width = 46.dp, height = 24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(animatedTrackColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onChange
            )
    ) {
        Box(
            modifier = Modifier
                .padding(start = thumbOffset)
                .align(Alignment.CenterStart)
                .size(20.dp)
                .shadow(1.dp, CircleShape)
                .background(animatedThumbColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (on && thumbIcon != null) {
                Icon(thumbIcon, contentDescription = null, tint = thumbIconTint, modifier = Modifier.size(14.dp))
            }
        }
    }
}
