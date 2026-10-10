package com.pidan.health.core.domain

/**
 * 三段式达标判定引擎（《需求梳理》1.2 判定表）。
 *
 * - 碳水/脂肪：固定值目标，±5g 绝对容差（非百分比）
 * - 蛋白/纤维：区间目标，严格按下限/上限判定
 * - 盐：只判上限，无下限（≤ 上限即绿色，纯记录 + 超限预警）
 *
 * 无状态纯函数，T4 营养卡片着色、T10 统计偏差分析均以此为准。
 */
object NutritionJudge {

    /** 固定值目标判定（碳水/脂肪）：[target-容差, target+容差] 内为 OK */
    fun judgeFixedValue(intakeGrams: Float, targetGrams: Float): NutritionStatus {
        val tolerance = NutritionConstants.CARBS_FAT_TOLERANCE_GRAMS
        return when {
            intakeGrams < targetGrams - tolerance -> NutritionStatus.UNDER
            intakeGrams > targetGrams + tolerance -> NutritionStatus.OVER
            else -> NutritionStatus.OK
        }
    }

    /** 区间目标判定（蛋白/膳食纤维）：下限 ≤ 值 ≤ 上限 为 OK */
    fun judgeRange(intakeGrams: Float, minGrams: Float, maxGrams: Float): NutritionStatus = when {
        intakeGrams < minGrams -> NutritionStatus.UNDER
        intakeGrams > maxGrams -> NutritionStatus.OVER
        else -> NutritionStatus.OK
    }

    /** 上限目标判定（盐）：只判上限无下限，永不返回 UNDER */
    fun judgeUpperLimit(intakeGrams: Float, maxGrams: Float): NutritionStatus =
        if (intakeGrams > maxGrams) NutritionStatus.OVER else NutritionStatus.OK
}
