package com.pidan.health.core.data.network

/**
 * 网络可用性检查（自动备份前置条件）。
 * 抽象成接口便于 JVM 单测注入 fake；实现依赖 ConnectivityManager。
 */
interface NetworkChecker {

    /**
     * 是否有可用网络。
     * @param requireWifi true 时仅 Wi-Fi 可用才算通过（用户选了「仅 Wi-Fi 同步」）
     */
    fun hasUsableNetwork(requireWifi: Boolean): Boolean
}
