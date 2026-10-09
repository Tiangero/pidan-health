package com.pidan.health.core.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 用户配置表：存储自定义系数和开关。单行数据（id 固定为 1）。
 */
@Entity(tableName = "user_preferences")
data class UserPreferences(
    @PrimaryKey val id: Int = 1,          // 固定为1，单例模式
    val carbsFactor: Float = 3.0f,        // 碳水系数
    val proteinMinFactor: Float = 1.3f,   // 蛋白质下限系数
    val proteinMaxFactor: Float = 1.7f,   // 蛋白质上限系数
    val fatFactor: Float = 0.5f,          // 脂肪系数
    val isFiberCustom: Boolean = false,   // 是否开放膳食纤维自定义
    val fiberMin: Float = 25f,            // 自定义膳食纤维下限 (isFiberCustom=true 时生效，默认25)
    val fiberMax: Float = 30f,            // 自定义膳食纤维上限 (isFiberCustom=true 时生效，默认30)
    val isSaltCustom: Boolean = false,    // 是否开放盐分自定义
    val saltMax: Float = 5f               // 自定义盐分上限 (isSaltCustom=true 时生效，默认5)
)
