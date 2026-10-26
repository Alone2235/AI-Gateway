package com.eta.aigateway.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eta.aigateway.ui.theme.*

@Composable
fun CompressSection(vm: SettingsViewModel) {
    val st by vm.state.collectAsState()
    var thresholdText by remember(st.compressThreshold) {
        mutableStateOf(st.compressThreshold.toString())
    }

    Column(
        Modifier
            .fillMaxWidth()
            .glass(Dimens.cardRadius)
            .padding(Dimens.cardPadding)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("上下文压缩", fontWeight = FontWeight.Medium, fontSize = Fonts.bodyStrong)
                Text(
                    "历史过长时用廉价模型分段摘要",
                    fontSize = Fonts.micro,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TextAlpha.secondary)
                )
            }
            Switch(checked = st.compressEnabled, onCheckedChange = { vm.setCompressEnabled(it) })
        }

        if (st.compressEnabled) {
            Spacer(Modifier.height(Dimens.gap12))
            OutlinedTextField(
                value = thresholdText,
                onValueChange = { t ->
                    val f = t.filter { it.isDigit() }.take(6)
                    thresholdText = f
                    f.toIntOrNull()?.let { if (it in 1000..200000) vm.setCompressThreshold(it) }
                },
                label = { Text("触发阈值（估算 token）", fontSize = Fonts.micro) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(Dimens.controlRadius)
            )
            Spacer(Modifier.height(Dimens.gap8))
            Text(
                "不指定摘要模型时，自动挑名字含 mini / flash / lite 的已启用模型",
                fontSize = Fonts.micro,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TextAlpha.tertiary)
            )
        }
    }
}
