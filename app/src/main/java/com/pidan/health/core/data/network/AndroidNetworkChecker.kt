package com.pidan.health.core.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * ConnectivityManager 实现：自动备份需感知网络类型与可用性（VPN/无网场景不误传）。
 * 需要 ACCESS_NETWORK_STATE 普通权限（安装即授予，不弹窗，符合权限最小化）。
 */
class AndroidNetworkChecker(private val context: Context) : NetworkChecker {

    override fun hasUsableNetwork(requireWifi: Boolean): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        if (!hasInternet) return false
        if (!requireWifi) return true
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
}
