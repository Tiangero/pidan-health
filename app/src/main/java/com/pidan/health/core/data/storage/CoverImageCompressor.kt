package com.pidan.health.core.data.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * 封面压缩规则（对齐设计稿："自动压缩：长边 ≤ 500px，WebP/JPEG，50KB 以内"）。
 */
object CoverImageRules {
    const val MAX_LONG_EDGE_PX = 500
    const val START_QUALITY = 80
    const val MIN_QUALITY = 40
    const val QUALITY_STEP = 10
    const val MAX_BYTES = 50 * 1024
}

/**
 * 封面图片压缩：任意来源（相册 / 相机）强制压到 50KB 内。
 * 压缩结果先落 cache 临时目录，保存食物时经 [FoodImageStorage.adopt] 收编进沙盒，
 * 用户放弃保存时临时文件随 cache 回收，不产生孤儿文件。
 */
interface CoverImageCompressor {

    /** 压缩来源图片，成功返回 cache 临时文件，失败返回 null（调用方提示后可继续手动录入） */
    fun compressToCache(sourceUri: String): File?

    /** 准备相机拍摄输出文件并返回可授权给相机 App 的 Uri（TakePicture 契约用） */
    fun prepareCameraCapture(): Uri
}

class CoverImageCompressorImpl(private val context: Context) : CoverImageCompressor {

    private val tempDir: File get() = File(context.cacheDir, TEMP_DIR_NAME).apply { mkdirs() }

    override fun compressToCache(sourceUri: String): File? = try {
        val source = Uri.parse(sourceUri) ?: return null
        val bitmap = decodeScaled(source) ?: return null
        val format = preferredFormat()
        val output = newTempFile(format)

        // 质量阶梯：80 起步，每步 -10，仍超标则继续降；40 仍超标说明长边还得缩，按 0.8 倍再压
        var quality = CoverImageRules.START_QUALITY
        var current = bitmap
        var bytes = encode(current, format, quality)
        while (bytes.size > CoverImageRules.MAX_BYTES && quality > CoverImageRules.MIN_QUALITY) {
            quality -= CoverImageRules.QUALITY_STEP
            bytes = encode(current, format, quality)
        }
        while (bytes.size > CoverImageRules.MAX_BYTES && longEdgeOf(current) > MIN_LONG_EDGE_PX) {
            current = scaleBitmap(current, (longEdgeOf(current) * SHRINK_FACTOR).toInt())
            quality = CoverImageRules.START_QUALITY
            bytes = encode(current, format, quality)
            while (bytes.size > CoverImageRules.MAX_BYTES && quality > CoverImageRules.MIN_QUALITY) {
                quality -= CoverImageRules.QUALITY_STEP
                bytes = encode(current, format, quality)
            }
        }

        FileOutputStream(output).use { it.write(bytes) }
        output
    } catch (e: Exception) {
        Log.e(TAG, "封面压缩失败 uri=$sourceUri", e)
        null
    }

    override fun prepareCameraCapture(): Uri {
        val file = File(tempDir, "camera_${System.currentTimeMillis()}.jpg")
        file.createNewFile()
        return androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    // ---- 内部实现 ----

    /** 两段解码：先读边界算 inSampleSize 粗缩，再精确缩放到长边 ≤ 500px */
    private fun decodeScaled(source: Uri): Bitmap? {
        // 注意：inJustDecodeBounds = true 时 decodeStream 返回 null（只填充 bounds），
        // 不能把它的返回值接进 ?: return null 链，否则永远提前返回 null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = context.contentResolver.openInputStream(source) ?: return null
        boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= CoverImageRules.MAX_LONG_EDGE_PX) {
            sample *= 2
        }
        val decoded = BitmapFactory.Options().apply { inSampleSize = sample }.let { opts ->
            context.contentResolver.openInputStream(source)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return null
        } ?: return null

        val scaled = if (longEdgeOf(decoded) > CoverImageRules.MAX_LONG_EDGE_PX) {
            scaleBitmap(decoded, CoverImageRules.MAX_LONG_EDGE_PX)
        } else {
            decoded
        }
        if (scaled !== decoded) decoded.recycle()
        return scaled
    }

    private fun scaleBitmap(source: Bitmap, targetLongEdge: Int): Bitmap {
        val longEdge = longEdgeOf(source)
        val ratio = targetLongEdge.toFloat() / longEdge
        val scaled = Bitmap.createScaledBitmap(
            source,
            (source.width * ratio).toInt().coerceAtLeast(1),
            (source.height * ratio).toInt().coerceAtLeast(1),
            true
        )
        if (scaled !== source) source.recycle()
        return scaled
    }

    /** WebP 优先（API 30+ 无损于兼容的 WEBP_LOSSY），低版本降级 JPEG */
    private fun preferredFormat(): Bitmap.CompressFormat =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            Bitmap.CompressFormat.JPEG
        }

    private fun encode(bitmap: Bitmap, format: Bitmap.CompressFormat, quality: Int): ByteArray =
        ByteArrayOutputStream().also { bitmap.compress(format, quality, it) }.toByteArray()

    private fun newTempFile(format: Bitmap.CompressFormat): File =
        File(tempDir, "cover_${System.currentTimeMillis()}.${if (format == Bitmap.CompressFormat.JPEG) "jpg" else "webp"}")

    private fun longEdgeOf(bitmap: Bitmap) = maxOf(bitmap.width, bitmap.height)

    private companion object {
        const val TAG = "CoverImageCompressor"
        const val TEMP_DIR_NAME = "cover_tmp"
        const val MIN_LONG_EDGE_PX = 200
        const val SHRINK_FACTOR = 0.8f
    }
}
