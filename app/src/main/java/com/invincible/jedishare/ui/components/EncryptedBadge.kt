package com.invincible.jedishare.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.invincible.jedishare.ui.theme.JediShareTheme

@Composable
fun EncryptedBadge(
    modifier: Modifier = Modifier,
    label: String = "Encrypted"
) {
    val colors = JediShareTheme.colors
    Row(
        modifier = modifier
            .background(colors.green.copy(alpha = 0.1f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Lock,
            contentDescription = null,
            tint = colors.green,
            modifier = Modifier.size(11.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            color = colors.green,
            style = MaterialTheme.typography.caption.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        )
    }
}
