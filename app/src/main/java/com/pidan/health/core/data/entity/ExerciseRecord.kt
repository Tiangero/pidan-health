package com.pidan.health.core.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * 运动记录表：存储月历视图中的运动状态。
 * @Serializable 供 T11 备份导出/导入序列化。
 */
@Serializable
@Entity(tableName = "exercise_record")
data class ExerciseRecord(
    @PrimaryKey val date: String,         // 日期 "YYYY-MM-DD"
    val type: Int,                        // 0: 休息(默认), 1: 有氧(红), 2: 无氧(蓝)
    val updatedAt: Long = System.currentTimeMillis()
)
