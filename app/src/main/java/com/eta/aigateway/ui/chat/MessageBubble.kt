package com.eta.aigateway.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import com.eta.aigateway.data.model.ChatMessage
import io.noties.markwon.Markwon

@Composable
fun MessageBubble(msg: ChatMessage) {
    val isUser = msg.role == "user"
    val bg = if (isUser)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.surfaceVariant
    val fg = if (isUser)
        MaterialTheme.colorScheme.onPrimary
    else
        MaterialTheme.colorScheme.onSurface

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier.widthIn(max = 320.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            Surface(
                color = bg,
                shape = RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 18.dp,
                    bottomStart = if (isUser) 18.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 18.dp
                )
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    if (isUser) {
                        Text(msg.content, color = fg, fontSize = 15.sp)
                    } else {
                        MarkdownText(msg.content, fg)
                    }
                }
            }
            if (!isUser && msg.model.isNotBlank()) {
                Text(
                    "${msg.providerName} · ${msg.model}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 3.dp, start = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun MarkdownText(text: String, color: Color) {
    val ctx = LocalContext.current
    val markwon = remember { Markwon.create(ctx) }
    val tv = remember {
        android.widget.TextView(ctx).apply {
            setTextIsSelectable(true)
            setPadding(0, 0, 0, 0)
        }
    }
    LaunchedEffect(text, color) {
        markwon.setMarkdown(tv, text)
        tv.setTextColor(color.toArgb())
        tv.textSize = 15f
    }
    AndroidView(
        factory = { tv },
        modifier = Modifier.wrapContentWidth()
    )
}

private fun Color.toArgb(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt()
)
