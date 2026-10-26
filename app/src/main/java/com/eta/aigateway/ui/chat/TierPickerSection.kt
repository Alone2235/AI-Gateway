package com.eta.aigateway.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eta.aigateway.data.model.ModelClassifier
import com.eta.aigateway.data.model.ModelGroup
import com.eta.aigateway.data.model.VirtualTier
import com.eta.aigateway.ui.theme.*

fun tierCandidates(groups: List<ModelGroup>, tier: VirtualTier): List<ModelGroup> {
    val usable = groups.filter { it.enabled && it.enabledSources.isNotEmpty() }
    return when (tier) {
        VirtualTier.LITE -> usable.filter { !ModelClassifier.isHeavy(it.modelId) }.ifEmpty { usable }
        VirtualTier.PRO -> usable
        VirtualTier.PRO_MAX, VirtualTier.ULTRA ->
            usable.filter { ModelClassifier.isHeavy(it.modelId) }.ifEmpty { usable }
    }
}

@Composable
fun TierPickerSection(
    groups: List<ModelGroup>,
    selected: VirtualTier?,
    onSelect: (VirtualTier) -> Unit
) {
    Column {
        Text(
            "智能档位",
            fontSize = Fonts.micro,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            "按强弱自动挑已启用的模型",
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TextAlpha.tertiary)
        )
        Spacer(Modifier.height(Dimens.gap8))
        VirtualTier.entries.forEach { tier ->
            TierRow(tier, groups, selected == tier, onSelect)
        }
        Spacer(Modifier.height(Dimens.gap8))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        Spacer(Modifier.height(Dimens.gap8))
        Text(
            "或者指定具体模型",
            fontSize = Fonts.micro,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TextAlpha.secondary)
        )
        Spacer(Modifier.height(Dimens.gap4))
    }
}

@Composable
private fun TierRow(
    tier: VirtualTier,
    groups: List<ModelGroup>,
    isSel: Boolean,
    onSelect: (VirtualTier) -> Unit
) {
    val cands = tierCandidates(groups, tier)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = cands.isNotEmpty()) { onSelect(tier) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (isSel) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ) {
            Text(
                tier.label,
                fontSize = Fonts.caption,
                fontWeight = FontWeight.SemiBold,
                color = if (isSel) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
        Spacer(Modifier.width(Dimens.gap12))
        Column(Modifier.weight(1f)) {
            Text(tier.desc, fontSize = Fonts.caption)
            Text(
                if (cands.isEmpty()) "无可用模型"
                else "${cands.size} 个候选 · ${ModelClassifier.prettyName(cands.first().modelId)}",
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TextAlpha.tertiary),
                maxLines = 1
            )
        }
        if (isSel) {
            Text("✓", color = MaterialTheme.colorScheme.primary, fontSize = Fonts.caption)
        }
    }
}
