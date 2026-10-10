package com.pidan.health.core.domain

/**
 * 营养素达标判定结果（三段式）。
 *
 * UI 映射约定（由 T4 首页营养卡片执行，此处不含任何 UI 概念）：
 * - [UNDER] 未达标 → 橙色
 * - [OK]    合理/达标 → 绿色
 * - [OVER]  超标/预警 → 红色
 *
 * 盐分无下限要求，判定只会返回 [OK] / [OVER]，永不返回 [UNDER]。
 */
enum class NutritionStatus {
    UNDER,
    OK,
    OVER
}
