package com.pidan.health.core.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * 食物模板表：食物库基础信息，所有营养数据强制统一为"每100g"标准。
 * @Serializable 供 T11 备份导出/导入序列化。
 */
@Serializable
@Entity(tableName = "food_template")
data class FoodTemplate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,                     // 食物名称
    val coverImagePath: String? = null,   // 封面图片相对路径（沙盒内）

    // --- 计量单位 ---
    val unitType: Int,                    // 0: 按克(g), 1: 按份(serving)
    val weightPerServing: Float? = null,  // 单份克重（仅当 unitType=1 时有效）

    // --- 营养成分 (严格基于每100g) ---
    val carbsPer100g: Float = 0f,         // 碳水
    val proteinPer100g: Float = 0f,       // 蛋白质
    val fatPer100g: Float = 0f,           // 脂肪
    val fiberPer100g: Float = 0f,         // 膳食纤维
    val saltPer100g: Float = 0f,          // 盐分

    // --- 价格信息 (用于计算单价) ---
    val totalPrice: Float? = null,        // 购买总价 (元)
    val totalAmount: Float? = null,       // 购买总量 (g 或 份数)

    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
