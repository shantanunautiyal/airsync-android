package com.sameerasw.airsync.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A rounded card container that wraps child cards with a unified rounded corner appearance.
 * This creates the Android Settings-style grouped card effect.
 *
 * @param modifier Modifier to apply to the container
 * @param spacing Vertical spacing between child cards (default: 2.dp)
 * @param cornerRadius Corner radius for the entire container (default: 24.dp)
 * @param containerColor Background color for the container
 * @param content The content to be placed inside the container
 */
@Composable
fun RoundedCardContainer(
    modifier: Modifier = Modifier,
    spacing: Dp = 2.dp,
    cornerRadius: Dp = 24.dp,
    containerColor: Color = Color.Transparent,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(containerColor),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content
    )
}


