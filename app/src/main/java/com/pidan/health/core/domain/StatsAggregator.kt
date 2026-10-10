package com.pidan.health.core.domain

import com.pidan.health.core.data.model.DatedDailySummary

/**
 * 单个营养素的「平均摄入 vs 目标区间」偏差（统计页达标率卡片用）。
 * targetMin/targetMax 即达标区间：碳水/脂肪 = 目标 ±5g 绝对容差，蛋白/纤维 = 区间目标。
 */
data class NutrientDeviation(
    val avgIntakeGrams: Float,
    val targetMinGrams: Float,
    val targetMaxGrams: Float,
    val status: NutritionStatus
)

data class NutritionDeviationSummary(
    val carbs: NutrientDeviation,
    val protein: NutrientDeviation,
    val fat: NutrientDeviation,
    val fiber: NutrientDeviation
)

/**
 * 统计页聚合器（纯 JVM，无 Android 依赖）。
 *
 * 历史冻结原则的统计口径：
 * - 摄入均值 = 区间内【有饮食记录】的各日摄入取平均（diet_record 固化值，无模板参与）；
 * - 目标均值 = 区间内【有快照】的各日冻结目标取平均（各日按当日体重/系数，不回溯）；
 * - 状态判定 = 平均摄入代入 [NutritionJudge]，与首页卡片同一判定引擎。
 *
 * 区间内无记录或无快照 → 返回 null（UI 显示空态）。
 */
object StatsAggregator {

    fun computeDeviation(
        summaries: List<DatedDailySummary>,
        snapshots: List<com.pidan.health.core.data.entity.DailySnapshot>
    ): NutritionDeviationSummary? {
        if (summaries.isEmpty() || snapshots.isEmpty()) return null

        val avgCarbsTarget = snapshots.averageOf { it.targetCarbs }
        val avgProteinMin = snapshots.averageOf { it.targetProteinMin }
        val avgProteinMax = snapshots.averageOf { it.targetProteinMax }
        val avgFatTarget = snapshots.averageOf { it.targetFat }
        val avgFiberMin = snapshots.averageOf { it.targetFiberMin }
        val avgFiberMax = snapshots.averageOf { it.targetFiberMax }

        val avgCarbs = summaries.averageOf { it.totalCarbs }
        val avgProtein = summaries.averageOf { it.totalProtein }
        val avgFat = summaries.averageOf { it.totalFat }
        val avgFiber = summaries.averageOf { it.totalFiber }

        val tolerance = NutritionConstants.CARBS_FAT_TOLERANCE_GRAMS

        return NutritionDeviationSummary(
            carbs = NutrientDeviation(
                avgIntakeGrams = avgCarbs,
                targetMinGrams = avgCarbsTarget - tolerance,
                targetMaxGrams = avgCarbsTarget + tolerance,
                status = NutritionJudge.judgeFixedValue(avgCarbs, avgCarbsTarget)
            ),
            protein = NutrientDeviation(
                avgIntakeGrams = avgProtein,
                targetMinGrams = avgProteinMin,
                targetMaxGrams = avgProteinMax,
                status = NutritionJudge.judgeRange(avgProtein, avgProteinMin, avgProteinMax)
            ),
            fat = NutrientDeviation(
                avgIntakeGrams = avgFat,
                targetMinGrams = avgFatTarget - tolerance,
                targetMaxGrams = avgFatTarget + tolerance,
                status = NutritionJudge.judgeFixedValue(avgFat, avgFatTarget)
            ),
            fiber = NutrientDeviation(
                avgIntakeGrams = avgFiber,
                targetMinGrams = avgFiberMin,
                targetMaxGrams = avgFiberMax,
                status = NutritionJudge.judgeRange(avgFiber, avgFiberMin, avgFiberMax)
            )
        )
    }

    /** 性价比：每元蛋白质 (g/元)。花费为 0 的食物不参与（防除零） */
    fun proteinPerYuan(totalProtein: Float, totalCost: Float): Float? =
        if (totalCost > 0f) totalProtein / totalCost else null

    private inline fun <T> List<T>.averageOf(selector: (T) -> Float): Float =
        sumOf { selector(it).toDouble() }.toFloat() / size
}
