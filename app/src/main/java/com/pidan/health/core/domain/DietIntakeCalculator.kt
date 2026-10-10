package com.pidan.health.core.domain

import com.pidan.health.core.data.entity.DietRecord
import com.pidan.health.core.data.entity.FoodTemplate

/**
 * 食物计量单位类型（food_template.unitType / diet_record.unitType 的取值约定）。
 */
object FoodUnit {
    /** 按克 (g) */
    const val BY_GRAM = 0

    /** 按份 (serving)，须配合模板的单份克重使用 */
    const val BY_SERVING = 1
}

/**
 * 快捷录入固化计算（《数据库表结构》场景 B）。
 *
 * 录入时一次性算好本次的克数、五大营养实际摄入与花费，写入 diet_record 后即为冻结值：
 * 此后食物模板如何修改 / 删除，历史记录永不回溯重算（历史冻结原则）。
 *
 * 计价约定：模板的 totalAmount（购买总量）一律按【克】填写（与场景 B 一致，
 * T6 表单价格区以重量计并展示"元/100g"单价），单价 = totalPrice / totalAmount (元/g)。
 * 模板未填价格时本次花费记 0。
 */
object DietIntakeCalculator {

    /**
     * 本次录入的总克数。
     * - 按克：amount 即克数
     * - 按份：amount × 单份克重；模板缺失单份克重（数据异常，T6 表单已拦截）返回 null，拒绝写入
     * - amount <= 0 视为无效输入，返回 null
     */
    fun computeGrams(template: FoodTemplate, amount: Float): Float? {
        if (amount <= 0f) return null
        return when (template.unitType) {
            FoodUnit.BY_GRAM -> amount
            FoodUnit.BY_SERVING ->
                template.weightPerServing
                    ?.takeIf { it > 0f }
                    ?.let { servingGrams -> servingGrams * amount }
            else -> null
        }
    }

    /**
     * 构建"固化"饮食记录（场景 B 步骤 2-4）。
     * 返回 null 表示无法固化（数量非法 / 按份缺单份克重），调用方不得落库。
     */
    fun buildDietRecord(template: FoodTemplate, amount: Float, date: String): DietRecord? {
        val grams = computeGrams(template, amount) ?: return null
        val ratio = grams / PER_100_GRAMS
        return DietRecord(
            foodId = template.id,
            date = date,
            amount = amount,
            unitType = template.unitType,
            foodName = template.name,
            intakeCarbs = template.carbsPer100g * ratio,
            intakeProtein = template.proteinPer100g * ratio,
            intakeFat = template.fatPer100g * ratio,
            intakeFiber = template.fiberPer100g * ratio,
            intakeSalt = template.saltPer100g * ratio,
            cost = unitPricePerGram(template) * grams,
            createdAt = System.currentTimeMillis()
        )
    }

    /** 单价 (元/g)：totalAmount 无效或未填价格时返回 0，本次花费记 0 */
    private fun unitPricePerGram(template: FoodTemplate): Float {
        val price = template.totalPrice ?: return 0f
        val amountTotal = template.totalAmount ?: return 0f
        if (price < 0f || amountTotal <= 0f) return 0f
        return price / amountTotal
    }

    /** 每百克基准（营养素均按每 100g 标注） */
    private const val PER_100_GRAMS = 100f
}
