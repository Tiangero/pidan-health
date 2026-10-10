package com.pidan.health.core.data.backup

import com.pidan.health.core.data.entity.DailySnapshot
import com.pidan.health.core.data.entity.DietRecord
import com.pidan.health.core.data.entity.ExerciseRecord
import com.pidan.health.core.data.entity.FoodTemplate
import com.pidan.health.core.data.entity.UserPreferences
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * 备份编解码验证：五表序列化 → 解析往返一致；必选文件缺失/损坏抛异常（导入防呆前置校验）。
 */
class BackupCodecTest {

    private fun sampleParsedFiles(): Map<String, String> {
        val foods = listOf(
            FoodTemplate(
                id = 1, name = "米饭", coverImagePath = "cover_1.webp",
                unitType = 1, weightPerServing = 150f,
                carbsPer100g = 25f, proteinPer100g = 2.6f, fatPer100g = 0.3f,
                totalPrice = 10f, totalAmount = 1000f
            )
        )
        val diet = listOf(
            DietRecord(
                id = 7, foodId = 1, date = "2026-10-10", amount = 1.5f, unitType = 1,
                foodName = "米饭", intakeCarbs = 56.25f, intakeProtein = 5.85f,
                intakeFat = 0.675f, intakeFiber = 0f, intakeSalt = 0f, cost = 2.25f
            )
        )
        val snapshots = listOf(
            DailySnapshot(
                date = "2026-10-10", weight = 62.5f, targetCarbs = 187.5f,
                targetProteinMin = 81.25f, targetProteinMax = 106.25f, targetFat = 31.25f
            )
        )
        val exercise = listOf(ExerciseRecord(date = "2026-10-09", type = 1))
        val prefs = UserPreferences(carbsFactor = 3.0f, isSaltCustom = true, saltMax = 6f)

        return mapOf(
            BackupCodec.FILE_FOODS to BackupCodec.serializeFoods(foods),
            BackupCodec.FILE_DIET to BackupCodec.serializeDietRecords(diet),
            BackupCodec.FILE_SNAPSHOTS to BackupCodec.serializeSnapshots(snapshots),
            BackupCodec.FILE_EXERCISE to BackupCodec.serializeExercise(exercise),
            BackupCodec.FILE_PREFERENCES to BackupCodec.serializePreferences(prefs)
        )
    }

    @Test
    fun `五表JSON往返解析一致`() {
        val parsed = BackupCodec.parse(sampleParsedFiles())

        assertEquals(1, parsed.foods.size)
        assertEquals("米饭", parsed.foods[0].name)
        assertEquals(150f, parsed.foods[0].weightPerServing)

        assertEquals(1, parsed.dietRecords.size)
        assertEquals(56.25f, parsed.dietRecords[0].intakeCarbs)
        assertEquals(2.25f, parsed.dietRecords[0].cost)
        assertEquals(7L, parsed.dietRecords[0].id) // 主键随备份保留

        assertEquals(1, parsed.snapshots.size)
        assertEquals(62.5f, parsed.snapshots[0].weight)

        assertEquals(1, parsed.exerciseRecords.size)
        assertEquals(1, parsed.exerciseRecords[0].type)

        assertEquals(6f, parsed.preferences?.saltMax)
        assertEquals(true, parsed.preferences?.isSaltCustom)
    }

    @Test
    fun `缺user_preferences可解析为可选兼容`() {
        val files = sampleParsedFiles().toMutableMap()
        files.remove(BackupCodec.FILE_PREFERENCES)

        val parsed = BackupCodec.parse(files)
        assertNull(parsed.preferences)
        assertEquals(1, parsed.foods.size)
    }

    @Test
    fun `缺必选文件抛异常`() {
        val files = sampleParsedFiles().toMutableMap()
        files.remove(BackupCodec.FILE_DIET)

        assertThrows(SerializationException::class.java) { BackupCodec.parse(files) }
    }

    @Test
    fun `损坏JSON抛SerializationException`() {
        val files = mapOf(BackupCodec.FILE_FOODS to "{ not json")

        assertThrows(SerializationException::class.java) { BackupCodec.parse(files) }
    }

    @Test
    fun `封面文件名透传`() {
        val parsed = BackupCodec.parse(sampleParsedFiles(), mapOf("cover_1.webp" to byteArrayOf(1, 2)))
        assertEquals(listOf("cover_1.webp"), parsed.coverFiles.keys.toList())
    }
}
