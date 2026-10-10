package com.pidan.health.core.domain

/**
 * 营养素目标计算引擎（《需求梳理》1.1）。
 *
 * 基准变量为当日体重(kg)：
 * - 碳水 = 体重 × carbsFactor（默认 3g）
 * - 蛋白 = 体重 × [proteinMinFactor, proteinMaxFactor]（默认 [1.3, 1.7] 区间）
 * - 脂肪 = 体重 × fatFactor（默认 0.5g）
 * - 纤维 = 固定区间（默认 [25, 30]，可自定义）
 * - 盐   = 固定上限（默认 5g，可自定义，无下限）
 *
 * 无状态纯计算，可被首页预览（T4 编辑弹窗实时预览）与快照生成共用。
 */
object NutritionCalculator {

    /** 基于体重与生效系数计算当日五大营养素目标 */
    fun computeTargets(weightKg: Float, factors: NutritionFactors): NutritionTargets {
        require(weightKg > 0f) { "体重必须为正数，实际值: $weightKg" }
        return NutritionTargets(
            carbsGrams = weightKg * factors.carbsFactor,
            proteinMinGrams = weightKg * factors.proteinMinFactor,
            proteinMaxGrams = weightKg * factors.proteinMaxFactor,
            fatGrams = weightKg * factors.fatFactor,
            fiberMinGrams = factors.fiberMinGrams,
            fiberMaxGrams = factors.fiberMaxGrams,
            saltMaxGrams = factors.saltMaxGrams
        )
    }
}
