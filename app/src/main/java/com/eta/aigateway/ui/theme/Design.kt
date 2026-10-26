package com.eta.aigateway.ui.theme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 设计令牌：8pt 网格、圆角梯度、字号阶梯 */
object Dimens {
    val controlRadius = 12.dp
    val cardRadius = 18.dp
    val panelRadius = 24.dp
    val sheetRadius = 28.dp

    val unit = 8.dp
    val pagePadding = 16.dp
    val cardPadding = 14.dp
    val gap4 = 4.dp
    val gap8 = 8.dp
    val gap12 = 12.dp
    val gap16 = 16.dp
    val gap24 = 24.dp

    val touchTarget = 48.dp
    val inputHeight = 52.dp
}

object Fonts {
    val display = 26.sp
    val title1 = 20.sp
    val title2 = 16.sp
    val bodyStrong = 15.sp
    val body = 14.sp
    val caption = 12.sp
    val micro = 11.sp
}

/** 文本颜色用透明度分级 */
object TextAlpha {
    const val primary = 1f
    const val secondary = 0.62f
    const val tertiary = 0.42f
    const val disabled = 0.28f
}

/** 两档玻璃：弱（卡片）/ 强（底栏、重点面板） */
enum class GlassLevel { Weak, Strong }
