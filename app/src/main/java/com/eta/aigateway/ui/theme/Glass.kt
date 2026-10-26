package com.eta.aigateway.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp

/** 深海鲸蓝玻璃底色 */
val GlassTint = Color(0xFF9EC5FF)

/**
 * 纯 Compose 玻璃（HyperOS 安全）。
 * 白/蓝半透明渐变 + 1px 高光描边，视觉与折射玻璃统一。
 */
fun Modifier.glassStatic(
    radius: Dp = Dimens.cardRadius,
    level: GlassLevel = GlassLevel.Weak,
    tint: Color = GlassTint
): Modifier {
    val shape = RoundedCornerShape(radius)
    val baseAlpha = if (level == GlassLevel.Weak) 0.10f else 0.16f
    val topAlpha = baseAlpha * 1.5f
    return this
        .clip(shape)
        .background(
            Brush.verticalGradient(
                listOf(
                    tint.copy(alpha = topAlpha),
                    tint.copy(alpha = baseAlpha)
                )
            )
        )
        .border(
            width = 1.dp,
            color = Color.White.copy(alpha = if (level == GlassLevel.Weak) 0.14f else 0.22f),
            shape = shape
        )
}

/** 强调色玻璃（用于主要操作 / 选中态） */
fun Modifier.glassAccent(
    radius: Dp = Dimens.cardRadius,
    accent: Color
): Modifier {
    val shape = RoundedCornerShape(radius)
    return this
        .clip(shape)
        .background(
            Brush.verticalGradient(
                listOf(accent.copy(alpha = 0.26f), accent.copy(alpha = 0.14f))
            )
        )
        .border(1.dp, accent.copy(alpha = 0.35f), shape)
}

/** 玻璃卡片容器 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    level: GlassLevel = GlassLevel.Weak,
    radius: Dp = Dimens.cardRadius,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    androidx.compose.foundation.layout.Column(
        modifier = modifier
            .fillMaxWidth()
            .glassStatic(radius, level)
            .padding(Dimens.cardPadding),
        content = content
    )
}
