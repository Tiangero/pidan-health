package com.pidan.health.core.domain

import com.pidan.health.core.data.entity.DailySnapshot

/**
 * 某一天锁定的营养目标（domain 业务模型）。
 * 由 [NutritionCalculator] 基于当天体重 + 生效系数算出，写入 daily_snapshot 后即被冻结。
 */
data class NutritionTargets(
    val carbsGrams: Float,
    val proteinMinGrams: Float,
    val proteinMaxGrams: Float,
    val fatGrams: Float,
    val fiberMinGrams: Float,
    val fiberMaxGrams: Float,
    val saltMaxGrams: Float
)

/** 目标模型 → 快照实体（data 层持久化用，日期与体重由快照管理方补充） */
fun NutritionTargets.toSnapshot(date: String, weight: Float, updatedAt: Long): DailySnapshot =
    DailySnapshot(
        date = date,
        weight = weight,
        targetCarbs = carbsGrams,
        targetProteinMin = proteinMinGrams,
        targetProteinMax = proteinMaxGrams,
        targetFat = fatGrams,
        targetFiberMin = fiberMinGrams,
        targetFiberMax = fiberMaxGrams,
        targetSaltMax = saltMaxGrams,
        updatedAt = updatedAt
    )
