package com.pidan.health.core.domain

import com.pidan.health.core.data.entity.DailySnapshot
import com.pidan.health.core.data.model.DatedDailySummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 统计聚合器验证：区间内「有记录日」的摄入均值 vs「有快照日」的冻结目标均值，
 * 状态判定与首页同一引擎（NutritionJudge）。
 */
class StatsAggregatorTest {

    private fun summary(
        date: String,
        carbs: Float = 0f,
        protein: Float = 0f,
        fat: Float = 0f,
        fiber: Float = 0f,
        cost: Float = 0f
    ) = DatedDailySummary(
        date = date,
        totalCarbs = carbs,
        totalProtein = protein,
        totalFat = fat,
        totalFiber = fiber,
        totalCost = cost
    )

    private fun snapshot(
        date: String,
        carbsTarget: Float,
        proteinMin: Float,
        proteinMax: Float,
        fatTarget: Float
    ) = DailySnapshot(
        date = date,
        weight = 62.5f,
        targetCarbs = carbsTarget,
        targetProteinMin = proteinMin,
        targetProteinMax = proteinMax,
        targetFat = fatTarget
    )

    @Test
    fun `均值摄入与均值目标的偏差计算正确`() {
        val summaries = listOf(
            summary("2026-10-05", carbs = 150f, protein = 88f, fat = 72f, fiber = 20f),
            summary("2026-10-06", carbs = 150f, protein = 92f, fat = 68f, fiber = 25f)
        )
        val snapshots = listOf(
            snapshot("2026-10-05", carbsTarget = 200f, proteinMin = 80f, proteinMax = 105f, fatTarget = 60f),
            snapshot("2026-10-06", carbsTarget = 100f, proteinMin = 80f, proteinMax = 105f, fatTarget = 60f)
        )

        val result = StatsAggregator.computeDeviation(summaries, snapshots)!!

        assertEquals(150f, result.carbs.avgIntakeGrams, 0.001f)
        assertEquals(145f, result.carbs.targetMinGrams, 0.001f) // 目标均值 150 - 容差 5
        assertEquals(155f, result.carbs.targetMaxGrams, 0.001f)
        assertEquals(NutritionStatus.OK, result.carbs.status)

        assertEquals(90f, result.protein.avgIntakeGrams, 0.001f)
        assertEquals(NutritionStatus.OK, result.protein.status)

        assertEquals(70f, result.fat.avgIntakeGrams, 0.001f)
        assertEquals(NutritionStatus.OVER, result.fat.status) // 70 > 60 + 5

        assertEquals(22.5f, result.fiber.avgIntakeGrams, 0.001f)
        assertEquals(NutritionStatus.UNDER, result.fiber.status) // 22.5 < 25
    }

    @Test
    fun `无记录或无快照返回null空态`() {
        val summaries = listOf(summary("2026-10-05", carbs = 100f))
        val snapshots = listOf(
            snapshot("2026-10-05", carbsTarget = 200f, proteinMin = 80f, proteinMax = 105f, fatTarget = 60f)
        )

        assertNull(StatsAggregator.computeDeviation(emptyList(), snapshots))
        assertNull(StatsAggregator.computeDeviation(summaries, emptyList()))
    }

    @Test
    fun `性价比为每元蛋白质且零花费不参与`() {
        assertEquals(2f, StatsAggregator.proteinPerYuan(totalProtein = 106.4f, totalCost = 53.2f)!!, 0.001f)
        assertNull(StatsAggregator.proteinPerYuan(totalProtein = 50f, totalCost = 0f))
    }
}
