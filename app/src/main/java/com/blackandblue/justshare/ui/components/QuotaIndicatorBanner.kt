package com.blackandblue.justshare.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackandblue.justshare.domain.billing.Plan
import com.blackandblue.justshare.domain.billing.QuotaState

/** Just-Share brand red, matching the established theme. */
private val BrandRed = Color(0xFFEC1C22)
private val BrandRedLight = Color(0xFFFFCDD2)
private val BrandRedDark = Color(0xFFB71C1C)

/**
 * A compact banner shown above the remote-transfer start button when the
 * selected transport is likely to need the relay.
 *
 * Colour-coded to remaining fraction:
 * - Green  > 50% remaining
 * - Amber  20–50% remaining
 * - Red    < 20% remaining (matches brand red)
 *
 * Tapping the banner invokes [onTap], which should navigate to the Settings
 * quota sub-section.
 */
@Composable
fun QuotaIndicatorBanner(
    quotaState: QuotaState,
    modifier: Modifier = Modifier,
    onTap: () -> Unit = {}
) {
    val total = quotaState.monthlyAllowanceGb + quotaState.packBalanceGb
    val fraction = if (total > 0f) (quotaState.remainingGb / total).coerceIn(0f, 1f) else 0f

    val trackColour by animateColorAsState(
        targetValue = when {
            fraction > 0.50f -> Color(0xFF4CAF50)
            fraction > 0.20f -> Color(0xFFFFA726)
            else -> BrandRed
        },
        animationSpec = tween(400),
        label = "quota_track_colour"
    )

    val isExhausted = quotaState.isExhausted

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(if (isExhausted) BrandRedLight else Color(0xFFF5F5F5))
            .clickable { onTap() }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isExhausted) Icons.Default.Warning else Icons.Default.Cloud,
                        contentDescription = null,
                        tint = if (isExhausted) BrandRedDark else trackColour,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (isExhausted) "Remote data allowance reached" else "Remote data",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isExhausted) BrandRedDark else Color(0xFF212121)
                    )
                }
                Text(
                    text = quotaState.run {
                        "%.1f / %.1f GB".format(remainingGb, total)
                    },
                    fontSize = 12.sp,
                    color = Color(0xFF757575)
                )
            }

            if (!isExhausted) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = fraction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = trackColour,
                    backgroundColor = Color(0xFFE0E0E0)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${quotaState.plan.displayName} plan · Tap for details",
                    fontSize = 11.sp,
                    color = Color(0xFF9E9E9E)
                )
            } else {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Buy a data pack to continue remote transfers",
                    fontSize = 12.sp,
                    color = BrandRedDark
                )
            }
        }
    }
}
