package com.pidan.health.core.data.storage

import android.content.Context
import java.io.File

/**
 * 食物封面图片存取：库中只存相对路径，物理文件落在应用沙盒。
 * UI / ViewModel 不接触路径拼接细节，统一经此解析。
 */
interface FoodImageStorage {

    /**
     * 相对路径 → 沙盒内文件。路径为空或文件不存在返回 null（UI 显示占位封面）。
     */
    fun resolve(relativePath: String?): File?

    /** 封面根目录（T6 写入压缩图时使用） */
    fun coverDir(): File
}

class FoodImageStorageImpl(context: Context) : FoodImageStorage {

    private val dir = File(context.filesDir, COVER_DIR_NAME)

    override fun resolve(relativePath: String?): File? {
        val path = relativePath?.takeIf { it.isNotBlank() } ?: return null
        return dir.resolve(path).takeIf { it.isFile }
    }

    override fun coverDir(): File = dir.apply { mkdirs() }

    private companion object {
        const val COVER_DIR_NAME = "food_covers"
    }
}
