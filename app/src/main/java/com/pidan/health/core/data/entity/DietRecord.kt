package com.pidan.health.core.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 饮食记录表：录入时即固化本次的实际营养摄入值与花费（历史数据完全冻结，永不回溯重算）；
 * 首页展示时直接对 diet_record 自身聚合，无需 JOIN 模板。
 *
 * 注意：不设外键。删除食物模板时，历史饮食记录必须保留。
 */
@Entity(tableName = "diet_record")
data class DietRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val foodId: Long,                     // 弱关联食物模板ID（仅统计参考，模板删除后仍保留）
    val date: String,                     // 日期 "YYYY-MM-DD" (用于按天分组查询)

    // --- 实际摄入记录 ---
    val amount: Float,                    // 实际摄入数量 (克数 或 份数)
    val unitType: Int,                    // 冗余存储当时的单位类型，防止模板修改影响历史

    // --- 冗余快照 (录入时一次算好并固化，之后模板如何修改/删除均不影响本条记录) ---
    val foodName: String,                 // 记录当时的食物名称
    val intakeCarbs: Float,               // 本次实际摄入碳水 (g)
    val intakeProtein: Float,             // 本次实际摄入蛋白质 (g)
    val intakeFat: Float,                 // 本次实际摄入脂肪 (g)
    val intakeFiber: Float,               // 本次实际摄入膳食纤维 (g)
    val intakeSalt: Float,                // 本次实际摄入盐分 (g)
    val cost: Float = 0f,                 // 本次记录的实际花费 (根据单价和数量算出)

    val createdAt: Long = System.currentTimeMillis()
)
