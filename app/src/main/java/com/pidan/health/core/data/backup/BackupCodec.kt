package com.pidan.health.core.data.backup

import com.pidan.health.core.data.entity.DailySnapshot
import com.pidan.health.core.data.entity.DietRecord
import com.pidan.health.core.data.entity.ExerciseRecord
import com.pidan.health.core.data.entity.FoodTemplate
import com.pidan.health.core.data.entity.UserPreferences
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 备份编解码（纯 JVM）：五张表 ↔ zip 内五个 JSON 文件的互转。
 * zip 结构（对齐设计稿）：
 * ```
 * food_template.json / diet_record.json / daily_snapshot.json /
 * exercise_record.json / user_preferences.json
 * covers/<封面相对路径文件>
 * ```
 * user_preferences.json 缺失视为可选（旧备份兼容），其余四个缺失即校验失败。
 */
object BackupCodec {

    const val SCHEMA_VERSION = 1
    const val FILE_FOODS = "food_template.json"
    const val FILE_DIET = "diet_record.json"
    const val FILE_SNAPSHOTS = "daily_snapshot.json"
    const val FILE_EXERCISE = "exercise_record.json"
    const val FILE_PREFERENCES = "user_preferences.json"
    const val ZIP_COVER_DIR = "covers"

    /** 导入解析结果（五表数据 + 封面文件名字节表，恢复到沙盒用） */
    data class ParsedBackup(
        val foods: List<FoodTemplate>,
        val dietRecords: List<DietRecord>,
        val snapshots: List<DailySnapshot>,
        val exerciseRecords: List<ExerciseRecord>,
        val preferences: UserPreferences?,
        val coverFiles: Map<String, ByteArray>
    )

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // ---- 导出序列化 ----

    fun serializeFoods(foods: List<FoodTemplate>): String = json.encodeToString(foods)

    fun serializeDietRecords(records: List<DietRecord>): String = json.encodeToString(records)

    fun serializeSnapshots(snapshots: List<DailySnapshot>): String = json.encodeToString(snapshots)

    fun serializeExercise(records: List<ExerciseRecord>): String = json.encodeToString(records)

    fun serializePreferences(prefs: UserPreferences): String = json.encodeToString(prefs)

    // ---- 导入解析（任一必选文件缺失/损坏抛 SerializationException） ----

    fun parse(
        files: Map<String, String>,
        coverFiles: Map<String, ByteArray> = emptyMap()
    ): ParsedBackup {
        val foods = files[FILE_FOODS]
            ?.let { json.decodeFromString<List<FoodTemplate>>(it) }
            ?: throw SerializationException("缺少 $FILE_FOODS")
        val dietRecords = files[FILE_DIET]
            ?.let { json.decodeFromString<List<DietRecord>>(it) }
            ?: throw SerializationException("缺少 $FILE_DIET")
        val snapshots = files[FILE_SNAPSHOTS]
            ?.let { json.decodeFromString<List<DailySnapshot>>(it) }
            ?: throw SerializationException("缺少 $FILE_SNAPSHOTS")
        val exerciseRecords = files[FILE_EXERCISE]
            ?.let { json.decodeFromString<List<ExerciseRecord>>(it) }
            ?: throw SerializationException("缺少 $FILE_EXERCISE")
        val preferences = files[FILE_PREFERENCES]
            ?.let { json.decodeFromString<UserPreferences>(it) }

        return ParsedBackup(
            foods = foods,
            dietRecords = dietRecords,
            snapshots = snapshots,
            exerciseRecords = exerciseRecords,
            preferences = preferences,
            coverFiles = coverFiles
        )
    }
}
