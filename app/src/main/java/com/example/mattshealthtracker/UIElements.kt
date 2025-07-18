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
import androidx.compose.ui.unit.dp

object AppUiElements {

    @Composable
    fun CollapsibleCard(
        titleContent: @Composable () -> Unit, // CHANGED: Now a Composable lambda for the title
        modifier: Modifier = Modifier.fillMaxWidth(),
        cardPadding: PaddingValues = PaddingValues(top = 6.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
        isExpandable: Boolean = true,
        expanded: Boolean = false,
        onExpandedChange: ((Boolean) -> Unit)? = null,
        quickGlanceInfo: @Composable (() -> Unit)? = null,

        // Changed defaultContent to be non-nullable, implying it's always provided.
        // If it can be optional, change to @Composable (() -> Unit)? = null
        defaultContent: @Composable () -> Unit,
        defaultContentModifier: Modifier = Modifier,

        expandableContent: @Composable (() -> Unit)? = null, // Can be null if no expandable content
        expandableContentModifier: Modifier = Modifier,

        hideDefaultWhenExpanded: Boolean = false
        // Removed all TextStyle parameters as styling is now handled by the passed composables
    ) {
        Card(
            modifier = modifier,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(0.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
                    .padding(cardPadding)
                    .animateContentSize(animationSpec = tween(durationMillis = 300, easing = LinearEasing))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .let {
                            if (isExpandable && onExpandedChange != null) it.clickable { onExpandedChange(!expanded) } else it
                        }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    titleContent.invoke() // CHANGED: Invoke the titleContent lambda directly

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        quickGlanceInfo?.invoke()

                        if (quickGlanceInfo != null && isExpandable) {
                            Spacer(modifier = Modifier.width(4.dp))
                        }

                        if (isExpandable) {
                            IconButton(
                                onClick = { onExpandedChange?.invoke(!expanded) }
                            ) {
                                Icon(
                                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                    contentDescription = if (expanded) "Collapse" else "Expand"
                                )
                            }
                        }
                    }
                }

                // Default Content Logic
                if (!isExpandable || !expanded || !hideDefaultWhenExpanded) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(modifier = defaultContentModifier) {
                        defaultContent.invoke() // Invoke the lambda directly
                    }
                }

                // Expandable Content Logic
                if (isExpandable && expanded) {
                    // Decide if divider is needed (if any default content was actually shown)
                    // The divider is shown if defaultContent was provided AND (it's not hidden, OR it was hidden)
                    val showDivider = (!isExpandable || !expanded || !hideDefaultWhenExpanded) && (defaultContent != null)
                    if (showDivider) {
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                    }

                    Column(modifier = expandableContentModifier) {
                        expandableContent?.invoke() // Invoke the lambda directly
                    }
                }
            }
        }
    }
}