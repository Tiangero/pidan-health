package com.pidan.health.core.data.webdav

import android.util.Log

/**
 * 云同步日志（T13）：统一 tag 方便真机 `adb logcat -s CloudSync` 过滤。
 *
 * android.util.Log 在 JVM 单测环境会抛 "not mocked"，try/catch 吞掉即可（测试静默）。
 */
object WebDavLog {

    const val TAG: String = "CloudSync"

    fun d(message: String) {
        try {
            Log.d(TAG, message)
        } catch (_: Throwable) {
            // JVM 单测环境忽略
        }
    }
}
