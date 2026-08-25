package com.github.garynasser.correction_notebook.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BottomBarTab(
    modifier: Modifier = Modifier,
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val selectedColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant
    val pillShape = RoundedCornerShape(8.dp)

    Column(
        modifier = modifier
            .fillMaxHeight()
            .selectable(
                selected = isSelected,
                onClick = onClick,
                role = Role.Tab
            ),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(
            space = 2.dp,
            alignment = androidx.compose.ui.Alignment.CenterVertically
        )
    ) {
        Box(
            modifier = Modifier
                .size(width = 38.dp, height = 24.dp)
                .clip(pillShape)
                .background(
                    if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.66f)
                    } else {
                        Color.Transparent
                    }
                ),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(19.dp),
                tint = if (isSelected) selectedColor else inactiveColor
            )
        }
        Text(
            text = label,
            fontSize = 11.sp,
            lineHeight = 12.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (isSelected) selectedColor else inactiveColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
