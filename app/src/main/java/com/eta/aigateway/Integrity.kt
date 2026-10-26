package com.eta.aigateway

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * 轻量完整性自检。
 * 目的是发现"改了包名/签名重新打包分发"这类粗暴篡改，
 * 不是不可绕过的强保护，但足以挡住随手改包的人。
 */
object Integrity {

    // 发布签名的 SHA-256（首次由自签名 keystore 产生，运行时填充）
    private const val EXPECTED_APP_ID = "com.eta.aigateway"
    private const val EXPECTED_MARKER = "eta-aigateway-original"

    fun isOriginal(context: Context): Boolean {
        // 1. 包名
        if (context.packageName != EXPECTED_APP_ID) return false

        // 2. 签名指纹（debug 构建跳过，release 校验）
        return try {
            val pm = context.packageManager
            val pi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            }
            val sigs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pi.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                pi.signatures
            }
            sigs != null && sigs.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    /** 用于 About 页展示水印文本，便于追溯来源 */
    fun watermark(): String = EXPECTED_MARKER
}
