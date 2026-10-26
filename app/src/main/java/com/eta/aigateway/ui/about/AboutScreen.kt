package com.eta.aigateway.ui.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eta.aigateway.R
import com.eta.aigateway.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val version = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
    } catch (e: Exception) { "?" }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent
                ),
                title = { Text("关于", fontWeight = FontWeight.SemiBold, fontSize = Fonts.title1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.pagePadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(Dimens.gap24))
            Text("AI Gateway", fontSize = Fonts.display, fontWeight = FontWeight.Bold)
            Text(
                "v$version",
                fontSize = Fonts.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TextAlpha.secondary)
            )
            Spacer(Modifier.height(Dimens.gap8))
            Text(
                "多来源模型聚合 · 失败自动切换",
                fontSize = Fonts.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TextAlpha.secondary)
            )

            Spacer(Modifier.height(Dimens.gap24))
            AuthorCard()

            Spacer(Modifier.height(Dimens.gap16))
            DisclaimerCard()
        }
    }
}

@Composable
private fun AuthorCard() {
    Column(
        Modifier.fillMaxWidth().glass(Dimens.cardRadius).padding(Dimens.cardPadding),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("作者", fontSize = Fonts.micro, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TextAlpha.tertiary))
        Spacer(Modifier.height(Dimens.gap4))
        Text("Alone2235", fontSize = Fonts.title2, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(Dimens.gap12))
        Text(
            "扫码到酷安找我",
            fontSize = Fonts.micro,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TextAlpha.secondary)
        )
        Spacer(Modifier.height(Dimens.gap8))
        Image(
            painter = painterResource(R.drawable.coolapk_qr),
            contentDescription = "酷安主页二维码",
            modifier = Modifier
                .size(180.dp)
                .clip(RoundedCornerShape(Dimens.controlRadius)),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
private fun DisclaimerCard() {
    Column(Modifier.fillMaxWidth().glass(Dimens.cardRadius).padding(Dimens.cardPadding)) {
        Text("声明", fontSize = Fonts.micro, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(Dimens.gap8))
        listOf(
            "本应用为本地工具，不含任何服务端；所有 API Key 仅保存在本机。",
            "请自行遵守各模型服务商的使用条款与当地法规。",
            "作者不对因使用本应用导致的账号封禁、额度损失承担责任。",
            "禁止用于任何违法违规用途。",
            "界面与实现由作者原创，未经许可请勿二次分发盈利版本。"
        ).forEach { line ->
            Text(
                "· $line",
                fontSize = Fonts.micro,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TextAlpha.secondary),
                modifier = Modifier.padding(bottom = Dimens.gap4)
            )
        }
    }
}
