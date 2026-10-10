package com.pidan.health.core.domain

import com.pidan.health.core.data.entity.UserPreferences

/**
 * 一次目标计算所用的全部系数（domain 业务模型）。
 * fiber/salt 字段已是"生效值"——是否开启自定义的开关逻辑在映射时消化掉，
 * 计算器与判定器不再感知开关的存在。
 */
data class NutritionFactors(
    val carbsFactor: Float,
    val proteinMinFactor: Float,
    val proteinMaxFactor: Float,
    val fatFactor: Float,
    val fiberMinGrams: Float,
    val fiberMaxGrams: Float,
    val saltMaxGrams: Float
)

/**
 * UserPreferences（data 层实体）→ domain 系数模型。
 * 纤维/盐的自定义开关在此处解析：关闭时使用《需求梳理》1.1 的默认区间/上限。
 */
fun UserPreferences.toNutritionFactors(): NutritionFactors = NutritionFactors(
    carbsFactor = carbsFactor,
    proteinMinFactor = proteinMinFactor,
    proteinMaxFactor = proteinMaxFactor,
    fatFactor = fatFactor,
    fiberMinGrams = if (isFiberCustom) fiberMin else NutritionConstants.DEFAULT_FIBER_MIN_GRAMS,
    fiberMaxGrams = if (isFiberCustom) fiberMax else NutritionConstants.DEFAULT_FIBER_MAX_GRAMS,
    saltMaxGrams = if (isSaltCustom) saltMax else NutritionConstants.DEFAULT_SALT_MAX_GRAMS
)
