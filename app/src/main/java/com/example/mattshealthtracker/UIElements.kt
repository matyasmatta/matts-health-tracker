// UiElements.kt
package com.example.mattshealthtracker

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle // Still needed if you use TextStyle for default values, or remove if not.
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

object AppUiElements {

    @Composable
    fun CollapsibleCard(
        titleContent: @Composable () -> Unit,
        modifier: Modifier = Modifier.fillMaxWidth(),
        // Let's make the default padding more explicit for its purpose
        // This padding is for the overall card content area.
        contentAreaPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp), // Symmetrical default
        isExpandable: Boolean = true,
        expanded: Boolean = false,
        onExpandedChange: ((Boolean) -> Unit)? = null,
        quickGlanceInfo: @Composable (() -> Unit)? = null,
        defaultContent: @Composable (() -> Unit)? = null, // Make it nullable to check if it exists
        defaultContentModifier: Modifier = Modifier,
        expandableContent: @Composable (() -> Unit)? = null,
        expandableContentModifier: Modifier = Modifier,
        hideDefaultWhenExpanded: Boolean = false,
        trailingContent: @Composable (() -> Unit)? = null
    ) {
        Card(
            modifier = modifier,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(0.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
                    // Apply only HORIZONTAL padding here. Vertical padding will be handled by content.
                    .padding(contentAreaPadding.calculateHorizontalPadding(LayoutDirection.Ltr))
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
                        // Add TOP padding for the title row from contentAreaPadding
                        .padding(top = contentAreaPadding.calculateTopPadding(), bottom = 2.dp), // Small bottom padding for title itself
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
                    // Spacer ABOVE default content if it's the first content element below title
                    // This is handled by the overall column's bottom padding now.
                    // Spacer(modifier = Modifier.height(contentAreaPadding.calculateTopPadding() / 2)) // Or a fixed value like 8.dp

                    Column(modifier = defaultContentModifier) {
                        defaultContent?.invoke() // defaultContent is nullable now
                    }
                    // Add BOTTOM padding for the default content section
                    // This ensures space at the bottom if it's the last thing shown.
                    if (!showExpandableContent) { // Only add if expandable content isn't going to be shown after it
                        Spacer(modifier = Modifier.height(contentAreaPadding.calculateBottomPadding()))
                    }
                }

                // Expandable Content Logic
                if (showExpandableContent) {
                    if (showDefaultContent) { // Show divider only if default content was also shown above
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                    } else {
                        // If no default content, but expandable content, add some top space for it
                        Spacer(modifier = Modifier.height(contentAreaPadding.calculateTopPadding() / 2)) // Or a fixed 8.dp
                    }
                    Column(modifier = expandableContentModifier) {
                        expandableContent?.invoke()
                    }
                    // Add BOTTOM padding for the expandable content section
                    Spacer(modifier = Modifier.height(contentAreaPadding.calculateBottomPadding()))
                }

                // If NOTHING is shown below the title (e.g. collapsed, no default, no expandable)
                // The title row itself should get the bottom padding.
                // This is a bit tricky with the current structure.
                // A simpler approach is to ensure the main Column has a minimum bottom padding
                // if nothing else provides it.
                // However, the current logic relies on spacers *after* content.

                // If neither default nor expandable content is shown, but the title is,
                // we need to ensure the overall column's bottom padding is respected.
                // The current structure with spacers *after* content handles this for when content exists.
                // If there's NO content at all below title, the title row's bottom padding (2.dp) +
                // the main column's implicit 0 bottom padding (from its own padding parameter)
                // would be used.

                // Let's refine the main column padding to only apply horizontal,
                // and then add top/bottom padding explicitly around content blocks.

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