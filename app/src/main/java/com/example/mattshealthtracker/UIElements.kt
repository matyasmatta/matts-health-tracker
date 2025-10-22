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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope

data class StatItem(
    val title: String,
    val icon: ImageVector,
    val valueString: String?,
    val isLoading: Boolean = false,
    val iconContentDescription: String? = null,
    val valueStyle: TextStyle = TextStyle.Default,
    val valueColor: Color = Color.Unspecified
)
object AppUiElements {
    @Composable
    fun QuickStatsCard(
        stat1: StatItem,
        stat2: StatItem,
        stat3: StatItem,
        modifier: Modifier = Modifier
    ) {
        Card(
            modifier = modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Column 1
                StatColumn(
                    stat = stat1,
                    valueStyle = if (stat1.valueStyle != TextStyle.Default) stat1.valueStyle else MaterialTheme.typography.headlineSmall,
                    valueColor = if (stat1.valueColor != Color.Unspecified) stat1.valueColor else MaterialTheme.colorScheme.primary
                )

                // Vertical divider
                Divider(
                    modifier = Modifier
                        .height(90.dp) // Adjust height as needed
                        .width(1.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )

                // Column 2
                StatColumn(
                    stat = stat2,
                    valueStyle = if (stat2.valueStyle != TextStyle.Default) stat2.valueStyle else MaterialTheme.typography.headlineSmall,
                    valueColor = if (stat2.valueColor != Color.Unspecified) stat2.valueColor else MaterialTheme.colorScheme.primary
                )

                // Vertical divider
                Divider(
                    modifier = Modifier
                        .height(90.dp)
                        .width(1.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )

                // Column 3
                StatColumn(
                    stat = stat3,
                    valueStyle = if (stat3.valueStyle != TextStyle.Default) stat3.valueStyle else MaterialTheme.typography.headlineSmall,
                    valueColor = if (stat3.valueColor != Color.Unspecified) stat3.valueColor else MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    /**
     * A private helper composable to draw a single stat column.
     * It uses RowScope to be able to apply .weight(1f).
     */
    @Composable
    private fun RowScope.StatColumn(
        stat: StatItem,
        valueStyle: TextStyle,
        valueColor: Color,
        modifier: Modifier = Modifier
    ) {
        Column(
            modifier = modifier
                .weight(1f)
                .padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Icon
            Icon(
                imageVector = stat.icon,
                contentDescription = stat.iconContentDescription ?: stat.title,
                modifier = Modifier.size(28.dp),
                tint = valueColor // Tint the icon to match the value
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Value or Loader
            // We use a fixed-height Box to prevent the layout from "jiggling"
            // when the content changes from a loader to text.
            Box(
                modifier = Modifier.height(32.dp), // Height of headlineSmall
                contentAlignment = Alignment.Center
            ) {
                if (stat.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    Text(
                        text = stat.valueString ?: "N/A",
                        style = valueStyle,
                        color = valueColor
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))

            // Title
            Text(
                text = stat.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    @Composable
    fun ValueTile(
        title: String,
        icon: ImageVector,
        iconContentDescription: String,
        isLoading: Boolean,
        valueString: String?, // The formatted value to display (e.g., "10,532" or "7.5 hrs")
        modifier: Modifier = Modifier,
        valueStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.headlineSmall, // Allow customization
        valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary // Allow customization
    ) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = iconContentDescription,
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.primary // Icon tint, can also be a parameter
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    } else {
                        Text(
                            text = valueString ?: "N/A",
                            style = valueStyle,
                            color = valueColor
                        )
                    }
                }
            }
        }
    }

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