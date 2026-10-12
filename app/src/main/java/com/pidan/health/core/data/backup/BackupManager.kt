package com.pidan.health.core.data.backup

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.room.withTransaction
import com.pidan.health.core.data.PidanDatabase
import com.pidan.health.core.data.entity.UserPreferences
import com.pidan.health.core.data.storage.FoodImageStorage
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** 导入结果概览（UI 提示用） */
data class ImportSummary(
    val foodCount: Int,
    val dietCount: Int,
    val snapshotCount: Int,
    val exerciseCount: Int
)

/**
 * 本地备份管理（T11 方案 A：zip = 五表 JSON + 封面图片，仅覆盖导入不合并）。
 *
 * 导出：五表序列化 + covers 打包 zip → 系统下载目录 Downloads/皮蛋健康/
 * 导入：系统文件选择器选 zip → 解析校验（失败不落库）→ 单事务清空五表重插 → 恢复封面。
 *
 * T13 新增字节级入口：[exportZipBytes] / [importBytes] 供云同步复用同一套编解码，
 * 本地导出/导入与云端上传/下载共用一条数据链路。
 */
interface BackupManager {

    /** 导出备份，返回展示用保存路径（如 Downloads/皮蛋健康/皮蛋健康_backup_20261010.zip） */
    suspend fun export(): Result<String>

    /** 导出备份为 zip 字节（云端上传用，不落 Downloads） */
    suspend fun exportZipBytes(): Result<ByteArray>

    /** 从 uri 导入备份（uri 来自系统文件选择器），事务失败/文件损坏时现有数据不受影响 */
    suspend fun import(uri: Uri): Result<ImportSummary>

    /** 从 zip 字节导入（云端恢复用），失败整体回滚 */
    suspend fun importBytes(bytes: ByteArray): Result<ImportSummary>
}

class BackupManagerImpl(
    private val db: PidanDatabase,
    private val foodImageStorage: FoodImageStorage,
    private val context: Context
) : BackupManager {

    override suspend fun export(): Result<String> = runCatching {
        publishBytes(buildZip())
    }

    override suspend fun exportZipBytes(): Result<ByteArray> = runCatching {
        buildZip()
    }

    override suspend fun import(uri: Uri): Result<ImportSummary> = runCatching {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("无法读取所选文件")
        importFromBytes(bytes)
    }

    override suspend fun importBytes(bytes: ByteArray): Result<ImportSummary> = runCatching {
        importFromBytes(bytes)
    }

    // ---- zip 构建/解析（本地与云端共用） ----

    private data class BackupSnapshot(
        val foods: List<com.pidan.health.core.data.entity.FoodTemplate>,
        val dietRecords: List<com.pidan.health.core.data.entity.DietRecord>,
        val snapshots: List<com.pidan.health.core.data.entity.DailySnapshot>,
        val exerciseRecords: List<com.pidan.health.core.data.entity.ExerciseRecord>,
        val preferences: UserPreferences
    )

    /** 事务内一次性取齐五表快照（保证导出数据内部一致）→ zip 字节 */
    private suspend fun buildZip(): ByteArray {
        val (foods, dietRecords, snapshots, exerciseRecords, prefs) = db.withTransaction {
            BackupSnapshot(
                foods = db.foodTemplateDao().getAll(),
                dietRecords = db.dietDao().getAll(),
                snapshots = db.snapshotDao().getAll(),
                exerciseRecords = db.exerciseDao().getAll(),
                preferences = db.userPreferencesDao().get() ?: UserPreferences()
            )
        }

        val buffer = ByteArrayOutputStream()
        ZipOutputStream(BufferedOutputStream(buffer)).use { zos ->
            fun putText(name: String, text: String) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(text.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }

            putText(BackupCodec.FILE_FOODS, BackupCodec.serializeFoods(foods))
            putText(BackupCodec.FILE_DIET, BackupCodec.serializeDietRecords(dietRecords))
            putText(BackupCodec.FILE_SNAPSHOTS, BackupCodec.serializeSnapshots(snapshots))
            putText(BackupCodec.FILE_EXERCISE, BackupCodec.serializeExercise(exerciseRecords))
            putText(BackupCodec.FILE_PREFERENCES, BackupCodec.serializePreferences(prefs))

            foodImageStorage.coverDir().listFiles()?.filter { it.isFile }?.forEach { cover ->
                zos.putNextEntry(ZipEntry("${BackupCodec.ZIP_COVER_DIR}/${cover.name}"))
                cover.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
        return buffer.toByteArray()
    }

    /** zip 字节 → 校验 → 单事务清空五表重插（失败整体回滚）→ 恢复封面 */
    private suspend fun importFromBytes(bytes: ByteArray): ImportSummary {
        val (files, covers) = readZipBytes(bytes)
        val parsed = BackupCodec.parse(files, covers)

        db.withTransaction {
            db.foodTemplateDao().deleteAll()
            db.dietDao().deleteAll()
            db.snapshotDao().deleteAll()
            db.exerciseDao().deleteAll()

            db.foodTemplateDao().insertAll(parsed.foods)
            db.dietDao().insertAll(parsed.dietRecords)
            db.snapshotDao().insertAll(parsed.snapshots)
            db.exerciseDao().insertAll(parsed.exerciseRecords)
            db.userPreferencesDao().upsert(parsed.preferences ?: UserPreferences())
        }

        restoreCovers(parsed.coverFiles)

        return ImportSummary(
            foodCount = parsed.foods.size,
            dietCount = parsed.dietRecords.size,
            snapshotCount = parsed.snapshots.size,
            exerciseCount = parsed.exerciseRecords.size
        )
    }

    /** zip 字节落盘到系统下载目录（API 29+ 走 MediaStore 免权限；旧版本直写公开 Downloads） */
    private fun publishBytes(bytes: ByteArray): String {
        val fileName = "皮蛋健康_backup_" +
            LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".zip"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/" + EXPORT_DIR_NAME
                )
            }
            val savedUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("创建下载目录文件失败")
            resolver.openOutputStream(savedUri)?.use { out ->
                ByteArrayInputStream(bytes).copyTo(out)
            } ?: throw IllegalStateException("写入下载目录文件失败")
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                EXPORT_DIR_NAME
            )
            if (!dir.exists() && !dir.mkdirs()) throw IllegalStateException("无法创建下载目录")
            File(dir, fileName).writeBytes(bytes)
        }
        return "Downloads/$EXPORT_DIR_NAME/$fileName"
    }

    // ---- zip 解析细节 ----

    /** 解 zip 字节：返回（数据 JSON 文本表, 封面文件名字节表） */
    private fun readZipBytes(bytes: ByteArray): Pair<Map<String, String>, Map<String, ByteArray>> {
        val files = mutableMapOf<String, String>()
        val covers = mutableMapOf<String, ByteArray>()
        ZipInputStream(BufferedInputStream(ByteArrayInputStream(bytes))).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val entryBytes = zis.readBytes()
                    when {
                        entry.name == BackupCodec.FILE_FOODS ||
                            entry.name == BackupCodec.FILE_DIET ||
                            entry.name == BackupCodec.FILE_SNAPSHOTS ||
                            entry.name == BackupCodec.FILE_EXERCISE ||
                            entry.name == BackupCodec.FILE_PREFERENCES ->
                            files[entry.name] = String(entryBytes, Charsets.UTF_8)

                        entry.name.startsWith("${BackupCodec.ZIP_COVER_DIR}/") ->
                            covers[sanitizeCoverName(entry.name)] = entryBytes
                    }
                }
                entry = zis.nextEntry
            }
        }
        return files to covers
    }

    /** 只取文件名本身，拒绝路径穿越 */
    private fun sanitizeCoverName(entryName: String): String =
        entryName.substringAfterLast('/').substringAfterLast('\\')

    /** 恢复封面到沙盒：先清空封面目录再整批写入（覆盖语义，与五表一致） */
    private fun restoreCovers(coverFiles: Map<String, ByteArray>) {
        val coverDir = foodImageStorage.coverDir()
        coverDir.listFiles()?.forEach { it.delete() }
        coverFiles.forEach { (name, bytes) ->
            val safeName = name.substringAfterLast('/').substringAfterLast('\\')
            if (safeName.isBlank() || safeName == "." || safeName == "..") return@forEach
            runCatching { File(coverDir, safeName).writeBytes(bytes) }
        }
    }

    private companion object {
        const val EXPORT_DIR_NAME = "皮蛋健康"
    }
}
