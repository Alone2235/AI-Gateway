package com.eta.aigateway.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eta.aigateway.ui.theme.*

/** 思考过程展示，默认折叠 */
@Composable
fun ReasoningBlock(text: String, modifier: Modifier = Modifier) {
    if (text.isBlank()) return
    var expanded by remember { mutableStateOf(true) }

    Column(
        modifier
            .fillMaxWidth()
            .glass(radius = 14.dp, level = GlassLevel.Weak)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "思考过程",
                fontSize = Fonts.micro,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.weight(1f))
            Text(
                "${text.length} 字",
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TextAlpha.tertiary)
            )
            androidx.compose.material3.Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        AnimatedVisibility(visible = expanded) {
            Text(
                text,
                fontSize = Fonts.micro,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TextAlpha.secondary),
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}
