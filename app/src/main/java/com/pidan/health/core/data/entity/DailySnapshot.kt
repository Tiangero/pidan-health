package com.pidan.health.core.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 每日快照表（核心防坑设计）：解决"历史数据永不回溯重算"的需求。
 * 每天首次打开 APP 或修改体重时，生成/更新当天的快照，锁定当天的体重和营养目标。
 */
@Entity(tableName = "daily_snapshot")
data class DailySnapshot(
    @PrimaryKey val date: String,         // 日期 "YYYY-MM-DD"

    // --- 锁定当天的身体数据 ---
    val weight: Float,                    // 当天记录时的体重 (kg)

    // --- 锁定当天的营养目标 (基于当天体重和系数计算后存入) ---
    val targetCarbs: Float,               // 碳水目标
    val targetProteinMin: Float,          // 蛋白质下限
    val targetProteinMax: Float,          // 蛋白质上限
    val targetFat: Float,                 // 脂肪目标
    val targetFiberMin: Float = 25f,      // 膳食纤维下限 (默认25)
    val targetFiberMax: Float = 30f,      // 膳食纤维上限 (默认30)
    val targetSaltMax: Float = 5f,        // 盐分上限 (默认5)

    val updatedAt: Long = System.currentTimeMillis()
)
