// UiElements.kt
package com.example.mattshealthtracker

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

object AppUiElements {

    @Composable
    fun CollapsibleCard(
        titleContent: @Composable () -> Unit,
        modifier: Modifier = Modifier.fillMaxWidth(),
        contentAreaPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 9.dp),
        isExpandable: Boolean = true,
        expanded: Boolean = false,
        onExpandedChange: ((Boolean) -> Unit)? = null,
        quickGlanceInfo: @Composable (() -> Unit)? = null,
        defaultContent: @Composable (() -> Unit)? = null,
        defaultContentModifier: Modifier = Modifier,
        expandableContent: @Composable (() -> Unit)? = null,
        expandableContentModifier: Modifier = Modifier,
        hideDefaultWhenExpanded: Boolean = false,
        trailingContent: @Composable (() -> Unit)? = null
    ) {
        Card(
            modifier = modifier,
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // Horizontal padding is now applied to child elements, not here
                    .animateContentSize(
                        animationSpec = tween(
                            durationMillis = 300,
                            easing = LinearEasing
                        )
                    )
            ) {
                // Title Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .let {
                            if (isExpandable && onExpandedChange != null) it.clickable {
                                onExpandedChange(
                                    !expanded
                                )
                            } else it
                        }
                        // Apply horizontal padding here
                        .padding(contentAreaPadding.calculateHorizontalPadding(LayoutDirection.Ltr))
                        // Apply top padding. Bottom padding is handled by spacers now.
                        .padding(top = contentAreaPadding.calculateTopPadding()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    titleContent.invoke()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        quickGlanceInfo?.invoke()
                        if (trailingContent != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            trailingContent()
                        }
                        if (quickGlanceInfo != null && isExpandable && trailingContent == null) {
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        if (isExpandable) {
                            IconButton(onClick = { onExpandedChange?.invoke(!expanded) }) {
                                Icon(
                                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                    contentDescription = if (expanded) "Collapse" else "Expand"
                                )
                            }
                        }
                    }
                }

                val showDefaultContent = defaultContent != null && (!isExpandable || !expanded || !hideDefaultWhenExpanded)
                val showExpandableContent = isExpandable && expanded && expandableContent != null

                // Default Content Logic
                if (showDefaultContent) {
                    // Add consistent spacer between title and content
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(
                        modifier = defaultContentModifier
                            // Apply horizontal padding to this content block
                            .padding(contentAreaPadding.calculateHorizontalPadding(LayoutDirection.Ltr))
                    ) {
                        defaultContent?.invoke()
                    }
                }

                // Expandable Content Logic
                if (showExpandableContent) {
                    if (showDefaultContent) {
                        // Divider between default and expandable content
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                    } else {
                        // If no default content, add consistent spacer between title and content
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Column(
                        modifier = expandableContentModifier
                            // Apply horizontal padding to this content block
                            .padding(contentAreaPadding.calculateHorizontalPadding(LayoutDirection.Ltr))
                    ) {
                        expandableContent?.invoke()
                    }
                }

                // Bottom padding
                if (showDefaultContent || showExpandableContent) {
                    // Add bottom padding if any content is shown
                    Spacer(modifier = Modifier.height(contentAreaPadding.calculateBottomPadding()))
                } else {
                    // Add bottom padding if card is collapsed (no content shown)
                    Spacer(modifier = Modifier.height(contentAreaPadding.calculateBottomPadding()))
                }
            }
        }
    }

    // Helper extension for PaddingValues
    fun PaddingValues.calculateHorizontalPadding(layoutDirection: LayoutDirection): PaddingValues {
        return PaddingValues(
            start = calculateStartPadding(layoutDirection),
            end = calculateEndPadding(layoutDirection)
        )
    }
}