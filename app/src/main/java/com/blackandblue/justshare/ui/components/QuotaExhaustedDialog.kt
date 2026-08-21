package com.blackandblue.justshare.ui.components

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.blackandblue.justshare.domain.billing.QuotaState
import com.blackandblue.justshare.presentation.billing.BillingViewModel
import com.blackandblue.justshare.presentation.billing.PurchaseState

private val BrandRed = Color(0xFFEC1C22)
private val BrandRedLight = Color(0xFFFFCDD2)
private val BrandRedDark = Color(0xFFB71C1C)

/**
 * Dialog shown when the relay server rejects a transfer due to quota exhaustion,
 * or when a mid-transfer abort occurs.
 *
 * Offers the [data_pack_10gb] consumable in-app purchase via [BillingViewModel].
 * All styling uses the established Just-Share theme tokens exactly.
 *
 * @param onDismiss Called when the user dismisses without purchasing.
 * @param onRestorePurchase Called when the user taps "Restore Purchase".
 * @param activity Required by the Play Billing flow to attach the purchase sheet.
 */
@Composable
fun QuotaExhaustedDialog(
    quotaState: QuotaState,
    billingViewModel: BillingViewModel,
    activity: Activity,
    onDismiss: () -> Unit,
    onRestorePurchase: () -> Unit = {}
) {
    val purchaseState by billingViewModel.purchaseState.collectAsState()
    val dataPackDetails by billingViewModel.dataPackProductDetails.collectAsState()

    val priceString = dataPackDetails
        ?.oneTimePurchaseOfferDetails
        ?.formattedPrice
        ?: "$2.99"

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon
            Icon(
                imageVector = Icons.Default.CloudOff,
                contentDescription = null,
                tint = BrandRed,
                modifier = Modifier.size(48.dp)
            )

            Spacer(Modifier.height(16.dp))

            // Headline
            Text(
                text = "Remote data allowance reached",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF212121),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            // Body copy
            Text(
                text = "You've used your ${quotaState.plan.displayName} monthly relay allowance " +
                        "(${quotaState.monthlyAllowanceGb} GB). Buy a 10 GB data pack " +
                        "to keep going — packs never expire.",
                fontSize = 14.sp,
                color = Color(0xFF616161),
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(Modifier.height(24.dp))
            Divider(color = Color(0xFFE0E0E0))
            Spacer(Modifier.height(20.dp))

            // Primary CTA
            Button(
                onClick = {
                    if (purchaseState !is PurchaseState.Loading) {
                        billingViewModel.purchaseDataPack(activity)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = BrandRed,
                    contentColor = Color.White,
                    disabledBackgroundColor = BrandRedLight
                ),
                enabled = purchaseState !is PurchaseState.Loading && dataPackDetails != null
            ) {
                if (purchaseState is PurchaseState.Loading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ShoppingCart,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Buy 10 GB Data Pack — $priceString",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Restore purchase link
            TextButton(onClick = onRestorePurchase) {
                Text(
                    text = "Restore Purchase",
                    color = BrandRedDark,
                    fontSize = 13.sp
                )
            }

            Spacer(Modifier.height(4.dp))

            // Dismiss
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Not now",
                    color = Color(0xFF9E9E9E),
                    fontSize = 13.sp
                )
            }
        }
    }
}
