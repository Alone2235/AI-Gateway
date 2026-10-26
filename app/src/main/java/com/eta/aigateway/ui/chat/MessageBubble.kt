package com.eta.aigateway.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.eta.aigateway.data.model.ChatMessage
import com.eta.aigateway.ui.theme.*
import io.noties.markwon.Markwon

@Composable
fun MessageBubble(msg: ChatMessage) {
    val isUser = msg.role == "user"

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier.widthIn(max = 320.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            val shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 6.dp,
                bottomEnd = if (isUser) 6.dp else 18.dp
            )

            if (isUser) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.88f),
                    shape = shape
                ) {
                    Text(
                        msg.content,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = Fonts.bodyStrong,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            } else {
                Column(
                    Modifier
                        .glassStatic(18.dp, GlassLevel.Weak)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    MarkdownText(msg.content, MaterialTheme.colorScheme.onSurface)
                }
            }

            if (!isUser && msg.model.isNotBlank()) {
                Text(
                    "${msg.providerName} · ${msg.model}",
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TextAlpha.tertiary),
                    modifier = Modifier.padding(top = 3.dp, start = 6.dp),
                    maxLines = 1
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
        android.widget.TextView(ctx).apply { setTextIsSelectable(true) }
    }
    LaunchedEffect(text, color) {
        markwon.setMarkdown(tv, text)
        tv.setTextColor(color.toArgb())
        tv.textSize = 15f
    }
    AndroidView(factory = { tv }, modifier = Modifier.wrapContentWidth())
}

private fun Color.toArgb(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt()
)
