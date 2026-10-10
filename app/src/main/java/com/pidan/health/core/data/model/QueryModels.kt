package com.pidan.health.core.data.model

/**
 * 按天聚合的营养摄入与花费（对应首页营养卡片 + 开销卡片）。
 * 普通 data class，非 Entity。聚合用 COALESCE 兜底，空数据日返回全 0。
 */
data class DailySummary(
    val totalCarbs: Float = 0f,
    val totalProtein: Float = 0f,
    val totalFat: Float = 0f,
    val totalFiber: Float = 0f,
    val totalSalt: Float = 0f,
    val totalCost: Float = 0f
)

/**
 * 带日期的按天聚合（统计页：区间内逐日开销曲线 / 达标率均值计算用）。
 */
data class DatedDailySummary(
    val date: String,
    val totalCarbs: Float = 0f,
    val totalProtein: Float = 0f,
    val totalFat: Float = 0f,
    val totalFiber: Float = 0f,
    val totalSalt: Float = 0f,
    val totalCost: Float = 0f
)

/**
 * 月度食物排行项（按次数分组排序的 Top5）。
 * totalProtein 供性价比分析（蛋白质/元）使用，全部来自 diet_record 快照字段聚合。
 */
data class FoodRankItem(
    val foodName: String,
    val eatCount: Int,
    val totalCost: Float,
    val totalProtein: Float = 0f
)

/**
 * 体重走势项（近30天）。
 */
data class WeightTrendItem(
    val date: String,
    val weight: Float
)
