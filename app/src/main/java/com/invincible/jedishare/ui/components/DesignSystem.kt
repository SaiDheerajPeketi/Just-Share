package com.invincible.jedishare.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.invincible.jedishare.ui.theme.JediShareTheme

enum class PillButtonVariant { PRIMARY, OUTLINE, GHOST, DANGER, GOLD }
enum class PillButtonSize { SM, MD, LG }

@Composable
fun PillButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: PillButtonVariant = PillButtonVariant.PRIMARY,
    size: PillButtonSize = PillButtonSize.LG,
    disabled: Boolean = false,
    icon: @Composable (() -> Unit)? = null
) {
    val colors = JediShareTheme.colors

    val backgroundColor = when (variant) {
        PillButtonVariant.PRIMARY -> colors.red
        PillButtonVariant.DANGER -> colors.darkRed
        PillButtonVariant.GOLD -> colors.gold
        PillButtonVariant.OUTLINE, PillButtonVariant.GHOST -> Color.Transparent
    }
    
    val contentColor = when (variant) {
        PillButtonVariant.PRIMARY, PillButtonVariant.DANGER, PillButtonVariant.GOLD -> Color.White
        PillButtonVariant.OUTLINE -> colors.red
        PillButtonVariant.GHOST -> colors.mutedFg
    }

    val padding = when (size) {
        PillButtonSize.LG -> PaddingValues(horizontal = 32.dp, vertical = 16.dp)
        PillButtonSize.MD -> PaddingValues(horizontal = 24.dp, vertical = 12.dp)
        PillButtonSize.SM -> PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.95f else 1f, label = "button_scale")

    var buttonModifier = modifier.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }.clip(CircleShape)
    
    if (variant == PillButtonVariant.OUTLINE) {
        buttonModifier = buttonModifier.border(2.dp, colors.red, CircleShape)
    }
    
    // Figma Drop shadow for primary and gold
    val elevation = if (variant == PillButtonVariant.PRIMARY || variant == PillButtonVariant.GOLD) {
        ButtonDefaults.elevation(
            defaultElevation = 8.dp,
            pressedElevation = 4.dp
        )
    } else {
        ButtonDefaults.elevation(0.dp)
    }

    Button(
        onClick = onClick,
        modifier = buttonModifier,
        enabled = !disabled,
        interactionSource = interactionSource,
        colors = ButtonDefaults.buttonColors(
            backgroundColor = backgroundColor,
            contentColor = contentColor,
            disabledBackgroundColor = if (variant == PillButtonVariant.GHOST) Color.Transparent else backgroundColor.copy(alpha = 0.4f),
            disabledContentColor = contentColor.copy(alpha = 0.4f)
        ),
        shape = CircleShape,
        contentPadding = padding,
        elevation = elevation
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (icon != null) {
                icon()
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(text = label, style = MaterialTheme.typography.button)
        }
    }
}

@Composable
fun BackBar(
    title: String,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    rightEl: @Composable (() -> Unit)? = null
) {
    val colors = JediShareTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        if (onBack != null) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onBack
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = colors.black)
            }
        } else {
            Box(modifier = Modifier.size(24.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.body1.copy(fontWeight = FontWeight.Bold, color = colors.black)
        )
        Box(modifier = Modifier.size(24.dp)) {
            rightEl?.invoke()
        }
    }
}

@Composable
fun StatusDot(status: String) {
    val colors = JediShareTheme.colors
    val color = when (status) {
        "online" -> colors.green
        "busy" -> Color(0xFFFF9800)
        else -> colors.mutedFg
    }
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color)
    )
}
