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
 * OCR 识别用图规则（T8）：百度 OCR 限制 base64 后 ≤8MB、长边 ≤4096px，
 * 此处留足余量（原图 ≤4MB、长边 ≤2048px）——印刷体营养表该分辨率精度足够，
 * 且请求体积小识别速度快（实测原图 11MB base64 直传被拒 216205 input oversize）。
 */
object OcrImageRules {
    const val MAX_RAW_BYTES = 4 * 1024 * 1024
    const val MAX_LONG_EDGE_PX = 2048
    const val START_QUALITY = 90
    const val MIN_QUALITY = 60
    const val QUALITY_STEP = 15
}

/**
 * 封面图片压缩：任意来源（相册 / 相机）强制压到 50KB 内。
 * 压缩结果先落 cache 临时目录，保存食物时经 [FoodImageStorage.adopt] 收编进沙盒，
 * 用户放弃保存时临时文件随 cache 回收，不产生孤儿文件。
 */
interface CoverImageCompressor {

    /**
     * 压缩来源图片，成功返回 cache 临时文件，失败返回 null（调用方提示后可继续手动录入）。
     * 来源若是本应用的相机临时原图（cover_tmp/camera_*），压缩结束后（无论成败）即删除，
     * 避免相机原图（单张可达数 MB）长期滞留 cache 撑爆缓存（2026-10-11 实测 20 张积到 107MB）。
     */
    fun compressToCache(sourceUri: String): File?

    /** 准备相机拍摄输出文件并返回可授权给相机 App 的 Uri（TakePicture 契约用） */
    fun prepareCameraCapture(): Uri

    /**
     * 准备 OCR 识别用图片字节（T8）：
     * 原图 ≤4MB 且长边 ≤2048px 时原样直传保精度；否则降采样重编码 JPEG。
     * 失败返回 null（调用方降级手动录入）。
     */
    fun prepareOcrBytes(sourceUri: String): ByteArray?
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
    } finally {
        // 相机原图用完即弃（成败都删），只删本应用 cover_tmp 下的 camera_* 文件
        deleteOwnTempSource(sourceUri)
    }

    override fun prepareCameraCapture(): Uri {
        // 安全网：清掉 24h 前残留的临时文件（相机原图/压缩封面），兜底进程被杀等异常场景
        purgeStaleTempFiles()
        val file = File(tempDir, "camera_${System.currentTimeMillis()}.jpg")
        file.createNewFile()
        return androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    override fun prepareOcrBytes(sourceUri: String): ByteArray? {
        return try {
        val uri = Uri.parse(sourceUri) ?: return null
        val raw = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // 注意：inJustDecodeBounds = true 时 decodeByteArray 返回 null（只填充 bounds），
        // 不能把返回值接进 elvis 链，判断要用 bounds.outWidth/Height
        BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val longEdge = maxOf(bounds.outWidth, bounds.outHeight)

        // 原图够小且长边不超限：原样直传，保精度
        if (raw.size <= OcrImageRules.MAX_RAW_BYTES && longEdge <= OcrImageRules.MAX_LONG_EDGE_PX) {
            raw
        } else {
            // 粗缩（inSampleSize 2 的幂）到长边 ≤2048，JPEG q90 重编码；仍超 4MB 则阶梯降质量
            var sample = 1
            while (longEdge / (sample * 2) >= OcrImageRules.MAX_LONG_EDGE_PX) sample *= 2
            val decoded = BitmapFactory.decodeByteArray(
                raw, 0, raw.size, BitmapFactory.Options().apply { inSampleSize = sample }
            ) ?: return null
            val scaled = if (longEdgeOf(decoded) > OcrImageRules.MAX_LONG_EDGE_PX) {
                scaleBitmap(decoded, OcrImageRules.MAX_LONG_EDGE_PX)
            } else {
                decoded
            }
            var quality = OcrImageRules.START_QUALITY
            var encoded = encode(scaled, Bitmap.CompressFormat.JPEG, quality)
            while (encoded.size > OcrImageRules.MAX_RAW_BYTES && quality > OcrImageRules.MIN_QUALITY) {
                quality -= OcrImageRules.QUALITY_STEP
                encoded = encode(scaled, Bitmap.CompressFormat.JPEG, quality)
            }
            if (scaled !== decoded) scaled.recycle()
            encoded
        }
    } catch (e: Exception) {
        Log.e(TAG, "OCR 图片准备失败 uri=$sourceUri", e)
        null
    } finally {
        // 原图已整体读入内存（或读取失败），相机临时原图用完即弃
        deleteOwnTempSource(sourceUri)
    }
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

    /**
     * 仅当来源指向本应用 cover_tmp 目录下的相机临时原图（camera_*）时才删除。
     * FileProvider Uri 的 path 相对于声明根（cacheDir，见 file_paths.xml），file:// 则直接取 path。
     * 相册/外部存储等外部来源一律不动。
     */
    private fun deleteOwnTempSource(sourceUri: String) {
        runCatching {
            val uri = Uri.parse(sourceUri) ?: return
            val path = when (uri.scheme) {
                "file" -> uri.path
                "content" -> if (uri.authority == "${context.packageName}.fileprovider") uri.path else null
                else -> null
            } ?: return
            val file = File(context.cacheDir, path)
            if (file.isFile &&
                file.parentFile?.canonicalPath == tempDir.canonicalPath &&
                file.name.startsWith(CAMERA_TEMP_PREFIX)
            ) {
                file.delete()
            }
        }
    }

    /** 清理超过 24h 的临时文件（camera_* / cover_*），兜底崩溃或进程被杀留下的孤儿文件 */
    private fun purgeStaleTempFiles() {
        runCatching {
            val deadline = System.currentTimeMillis() - STALE_TEMP_MS
            tempDir.listFiles()?.forEach { if (it.lastModified() < deadline) it.delete() }
        }
    }

    private companion object {
        const val TAG = "CoverImageCompressor"
        const val TEMP_DIR_NAME = "cover_tmp"
        const val MIN_LONG_EDGE_PX = 200
        const val SHRINK_FACTOR = 0.8f
        const val CAMERA_TEMP_PREFIX = "camera_"
        const val STALE_TEMP_MS = 24 * 60 * 60 * 1000L
    }
}
