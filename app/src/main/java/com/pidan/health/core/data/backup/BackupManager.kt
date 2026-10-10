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
 */
interface BackupManager {

    /** 导出备份，返回展示用保存路径（如 Downloads/皮蛋健康/皮蛋健康_backup_20261010.zip） */
    suspend fun export(): Result<String>

    /** 从 uri 导入备份（uri 来自系统文件选择器），事务失败/文件损坏时现有数据不受影响 */
    suspend fun import(uri: Uri): Result<ImportSummary>
}

class BackupManagerImpl(
    private val db: PidanDatabase,
    private val foodImageStorage: FoodImageStorage,
    private val context: Context
) : BackupManager {

    override suspend fun export(): Result<String> = runCatching {
        // 事务内一次性取齐五表快照，保证导出数据内部一致
        val (foods, dietRecords, snapshots, exerciseRecords, prefs) = db.withTransaction {
            BackupSnapshot(
                foods = db.foodTemplateDao().getAll(),
                dietRecords = db.dietDao().getAll(),
                snapshots = db.snapshotDao().getAll(),
                exerciseRecords = db.exerciseDao().getAll(),
                preferences = db.userPreferencesDao().get() ?: UserPreferences()
            )
        }

        val tempZip = File.createTempFile("pidan_backup", ".zip", context.cacheDir)
        try {
            writeZip(
                tempZip = tempZip,
                foods = foods,
                dietRecords = dietRecords,
                snapshots = snapshots,
                exerciseRecords = exerciseRecords,
                prefs = prefs,
                covers = foodImageStorage.coverDir().listFiles()?.filter { it.isFile } ?: emptyList()
            )
            publishZip(tempZip)
        } finally {
            tempZip.delete()
        }
    }

    override suspend fun import(uri: Uri): Result<ImportSummary> = runCatching {
        val parsed = readZip(uri).let { filesWithCovers ->
            BackupCodec.parse(filesWithCovers.first, filesWithCovers.second)
        }

        // 单事务清空五表 + 重插：中途失败整体回滚，现有数据不受影响
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

        ImportSummary(
            foodCount = parsed.foods.size,
            dietCount = parsed.dietRecords.size,
            snapshotCount = parsed.snapshots.size,
            exerciseCount = parsed.exerciseRecords.size
        )
    }

    // ---- 导出细节 ----

    private data class BackupSnapshot(
        val foods: List<com.pidan.health.core.data.entity.FoodTemplate>,
        val dietRecords: List<com.pidan.health.core.data.entity.DietRecord>,
        val snapshots: List<com.pidan.health.core.data.entity.DailySnapshot>,
        val exerciseRecords: List<com.pidan.health.core.data.entity.ExerciseRecord>,
        val preferences: UserPreferences
    )

    private fun writeZip(
        tempZip: File,
        foods: List<com.pidan.health.core.data.entity.FoodTemplate>,
        dietRecords: List<com.pidan.health.core.data.entity.DietRecord>,
        snapshots: List<com.pidan.health.core.data.entity.DailySnapshot>,
        exerciseRecords: List<com.pidan.health.core.data.entity.ExerciseRecord>,
        prefs: UserPreferences,
        covers: List<File>
    ) {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(tempZip))).use { zos ->
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

            covers.forEach { cover ->
                zos.putNextEntry(ZipEntry("${BackupCodec.ZIP_COVER_DIR}/${cover.name}"))
                cover.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
    }

    /** zip 落到系统下载目录（API 29+ 走 MediaStore 免权限；旧版本直写公开 Downloads，需运行时权限） */
    private fun publishZip(tempZip: File): String {
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
                tempZip.inputStream().use { it.copyTo(out) }
            } ?: throw IllegalStateException("写入下载目录文件失败")
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                EXPORT_DIR_NAME
            )
            if (!dir.exists() && !dir.mkdirs()) throw IllegalStateException("无法创建下载目录")
            tempZip.copyTo(File(dir, fileName), overwrite = true)
        }
        return "Downloads/$EXPORT_DIR_NAME/$fileName"
    }

    // ---- 导入细节 ----

    /** 解 zip：返回（数据 JSON 文本表, 封面文件名字节表） */
    private fun readZip(uri: Uri): Pair<Map<String, String>, Map<String, ByteArray>> {
        val files = mutableMapOf<String, String>()
        val covers = mutableMapOf<String, ByteArray>()
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(BufferedInputStream(input)).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val bytes = zis.readBytes()
                        when {
                            entry.name == BackupCodec.FILE_FOODS ||
                                entry.name == BackupCodec.FILE_DIET ||
                                entry.name == BackupCodec.FILE_SNAPSHOTS ||
                                entry.name == BackupCodec.FILE_EXERCISE ||
                                entry.name == BackupCodec.FILE_PREFERENCES ->
                                files[entry.name] = String(bytes, Charsets.UTF_8)

                            entry.name.startsWith("${BackupCodec.ZIP_COVER_DIR}/") ->
                                covers[sanitizeCoverName(entry.name)] = bytes
                        }
                    }
                    entry = zis.nextEntry
                }
            }
        } ?: throw IllegalStateException("无法读取所选文件")
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
