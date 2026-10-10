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

    /**
     * 把压缩好的临时封面文件（cache 目录）收编进封面目录，返回相对路径；
     * 移动失败返回 null（调用方保存时降级为无封面，不阻断保存）。
     */
    fun adopt(tempFile: File): String?
}

class FoodImageStorageImpl(context: Context) : FoodImageStorage {

    private val dir = File(context.filesDir, COVER_DIR_NAME)

    override fun resolve(relativePath: String?): File? {
        val path = relativePath?.takeIf { it.isNotBlank() } ?: return null
        return dir.resolve(path).takeIf { it.isFile }
    }

    override fun coverDir(): File = dir.apply { mkdirs() }

    override fun adopt(tempFile: File): String? {
        if (!tempFile.isFile) return null
        val target = File(coverDir(), adoptFileName(tempFile.extension))
        return try {
            if (tempFile.renameTo(target) || (tempFile.copyTo(target, overwrite = true).isFile && tempFile.delete())) {
                target.name
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /** 时间戳 + 随机数命名，避免同秒保存多个封面时互相覆盖 */
    private fun adoptFileName(extension: String): String {
        val ext = extension.takeIf { it.isNotBlank() }?.let { ".$it" } ?: ".webp"
        return "cover_${System.currentTimeMillis()}_${(0..999).random()}$ext"
    }

    private companion object {
        const val COVER_DIR_NAME = "food_covers"
    }
}
