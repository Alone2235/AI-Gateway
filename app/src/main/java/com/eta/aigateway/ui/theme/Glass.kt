package com.eta.aigateway.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.RoundedRectangle

/** 全局 backdrop：由 AppRoot 导出内容层提供 */
val LocalBackdrop = staticCompositionLocalOf<Backdrop?> { null }

val GlassTint = Color(0xFF9EC5FF)

/**
 * 真·液态玻璃：离屏导出内容层 + 实时折射。
 *
 * 三层效果：
 *   vibrancy  色彩增强
 *   blur      背景模糊
 *   lens      边缘折射（透镜畸变，这是"液态"的来源）
 *
 * 若 backdrop 未提供则自动降级为静态玻璃，不会崩。
 */
@Composable
fun Modifier.glass(
    radius: Dp = Dimens.cardRadius,
    level: GlassLevel = GlassLevel.Weak,
    tint: Color = GlassTint
): Modifier {
    val backdrop = LocalBackdrop.current ?: return this.glassStatic(radius, level, tint)
    val shape = RoundedRectangle(radius)

    return this.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            when (level) {
                GlassLevel.Weak -> {
                    blur(6.dp.toPx())
                    lens(10.dp.toPx(), 18.dp.toPx())
                }
                GlassLevel.Strong -> {
                    blur(10.dp.toPx())
                    lens(16.dp.toPx(), 28.dp.toPx())
                }
            }
        },
        highlight = {
            Highlight(alpha = if (level == GlassLevel.Weak) 0.5f else 0.68f)
        },
        shadow = { Shadow(alpha = 1f) },
        innerShadow = { InnerShadow(radius = 12.dp, alpha = 0.32f) },
        onDrawSurface = {
            val base = if (level == GlassLevel.Weak) 0.10f else 0.15f
            drawRect(tint.copy(alpha = base))
        }
    )
}

/** 静态玻璃兜底（无折射），视觉与折射玻璃接近 */
fun Modifier.glassStatic(
    radius: Dp = Dimens.cardRadius,
    level: GlassLevel = GlassLevel.Weak,
    tint: Color = GlassTint
): Modifier {
    val shape = RoundedCornerShape(radius)
    val baseAlpha = if (level == GlassLevel.Weak) 0.10f else 0.16f
    return this
        .clip(shape)
        .background(
            Brush.verticalGradient(
                listOf(tint.copy(alpha = baseAlpha * 1.5f), tint.copy(alpha = baseAlpha))
            )
        )
        .border(1.dp, Color.White.copy(alpha = if (level == GlassLevel.Weak) 0.14f else 0.22f), shape)
}
