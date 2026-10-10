package com.pidan.health.core.domain

import com.pidan.health.core.data.entity.FoodTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 快捷录入固化计算验证（《数据库表结构》场景 B）。
 * 核心用例：米饭（单份 150g、碳水 25g/100g、10 元/1000g）录入 1.5 份
 * → 克数 225g、碳水 56.25g、花费 2.25 元，数值必须完全一致。
 */
class DietIntakeCalculatorTest {

    private val today = "2026-10-10"

    /** 场景 B 原样模板：米饭 */
    private fun riceTemplate() = FoodTemplate(
        id = 1L,
        name = "米饭",
        unitType = FoodUnit.BY_SERVING,
        weightPerServing = 150f,
        carbsPer100g = 25f,
        proteinPer100g = 2.6f,
        fatPer100g = 0.3f,
        fiberPer100g = 0.7f,
        saltPer100g = 0f,
        totalPrice = 10f,
        totalAmount = 1000f
    )

    // ---- 场景 B 主链路 ----

    @Test
    fun `场景B - 米饭录入1_5份 - 克数225g碳水56_25g花费2_25元`() {
        val record = DietIntakeCalculator.buildDietRecord(riceTemplate(), 1.5f, today)!!

        assertEquals(225f, record.amount * 150f, 0.001f) // 1.5 × 150
        assertEquals(56.25f, record.intakeCarbs, 0.001f) // 225 / 100 × 25
        assertEquals(2.25f, record.cost, 0.001f)         // 225 × (10 / 1000)
        assertEquals(today, record.date)
        assertEquals(1L, record.foodId)
        assertEquals("米饭", record.foodName)
        assertEquals(FoodUnit.BY_SERVING, record.unitType)
        assertEquals(1.5f, record.amount, 0.001f)
    }

    @Test
    fun `场景B - 米饭录入1_5份 - 其余营养素按同一比例固化`() {
        val record = DietIntakeCalculator.buildDietRecord(riceTemplate(), 1.5f, today)!!
        val ratio = 225f / 100f

        assertEquals(2.6f * ratio, record.intakeProtein, 0.001f)
        assertEquals(0.3f * ratio, record.intakeFat, 0.001f)
        assertEquals(0.7f * ratio, record.intakeFiber, 0.001f)
        assertEquals(0f, record.intakeSalt, 0.001f)
    }

    // ---- 按克录入 ----

    @Test
    fun `按克录入 - 克数即amount - 摄入与花费按克数折算`() {
        val chicken = FoodTemplate(
            id = 2L,
            name = "鸡胸肉",
            unitType = FoodUnit.BY_GRAM,
            carbsPer100g = 20f,
            totalPrice = 30f,
            totalAmount = 1500f
        )

        val record = DietIntakeCalculator.buildDietRecord(chicken, 180f, today)!!

        assertEquals(FoodUnit.BY_GRAM, record.unitType)
        assertEquals(180f, record.amount, 0.001f)
        assertEquals(36f, record.intakeCarbs, 0.001f)   // 180 / 100 × 20
        assertEquals(3.6f, record.cost, 0.001f)         // 180 × (30 / 1500)
    }

    // ---- 无价格 / 价格数据异常 ----

    @Test
    fun `模板未填价格 - 花费记0 - 营养素照常固化`() {
        val noPrice = riceTemplate().copy(totalPrice = null, totalAmount = null)

        val record = DietIntakeCalculator.buildDietRecord(noPrice, 1f, today)!!

        assertEquals(0f, record.cost, 0.001f)
        assertEquals(37.5f, record.intakeCarbs, 0.001f) // 150 / 100 × 25
    }

    @Test
    fun `购买总量为0或负数 - 花费记0 - 不产生除零`() {
        val badTotal = riceTemplate().copy(totalAmount = 0f)

        val record = DietIntakeCalculator.buildDietRecord(badTotal, 1f, today)!!

        assertEquals(0f, record.cost, 0.001f)
    }

    // ---- 非法输入拒绝写入 ----

    @Test
    fun `数量为0或负数 - 返回null拒绝写入`() {
        assertNull(DietIntakeCalculator.buildDietRecord(riceTemplate(), 0f, today))
        assertNull(DietIntakeCalculator.buildDietRecord(riceTemplate(), -1.5f, today))
    }

    @Test
    fun `按份但模板缺失单份克重 - 返回null拒绝写入`() {
        val broken = riceTemplate().copy(weightPerServing = null)
        assertNull(DietIntakeCalculator.buildDietRecord(broken, 1.5f, today))
    }

    @Test
    fun `computeGrams - 按份与按克分别返回正确克数`() {
        assertEquals(225f, DietIntakeCalculator.computeGrams(riceTemplate(), 1.5f)!!, 0.001f)
        assertEquals(80f, DietIntakeCalculator.computeGrams(
            riceTemplate().copy(unitType = FoodUnit.BY_GRAM), 80f
        )!!, 0.001f)
    }

    // ---- T6 价格区通用入口（表单单价预览与落库计费共用） ----

    @Test
    fun `unitPricePer100Grams - 设计稿示例 25元600g - 4_17元每100g`() {
        val price = DietIntakeCalculator.unitPricePer100Grams(25f, 600f)!!
        assertEquals(4.17f, price, 0.01f)
    }

    @Test
    fun `unitPricePer100Grams - 场景B米饭 10元1000g - 1元每100g`() {
        assertEquals(1f, DietIntakeCalculator.unitPricePer100Grams(10f, 1000f)!!, 0.001f)
    }

    @Test
    fun `unitPricePer100Grams - 价格或总量缺失无效 - 返回null`() {
        assertNull(DietIntakeCalculator.unitPricePer100Grams(null, 600f))
        assertNull(DietIntakeCalculator.unitPricePer100Grams(25f, null))
        assertNull(DietIntakeCalculator.unitPricePer100Grams(25f, 0f))
        assertNull(DietIntakeCalculator.unitPricePer100Grams(-1f, 600f))
    }

    @Test
    fun `purchaseAmountToGrams - 按克即克数`() {
        assertEquals(600f, DietIntakeCalculator.purchaseAmountToGrams(600f, FoodUnit.BY_GRAM, null)!!, 0.001f)
    }

    @Test
    fun `purchaseAmountToGrams - 收集箱场景 鸡蛋10个8元 - 10份x50g=500g`() {
        val grams = DietIntakeCalculator.purchaseAmountToGrams(10f, FoodUnit.BY_SERVING, 50f)!!
        assertEquals(500f, grams, 0.001f)
        // 8 元 / 500g = 1.6 元/100g
        assertEquals(1.6f, DietIntakeCalculator.unitPricePer100Grams(8f, grams)!!, 0.001f)
    }

    @Test
    fun `purchaseAmountToGrams - 按份缺单份克重或数量无效 - 返回null`() {
        assertNull(DietIntakeCalculator.purchaseAmountToGrams(10f, FoodUnit.BY_SERVING, null))
        assertNull(DietIntakeCalculator.purchaseAmountToGrams(0f, FoodUnit.BY_GRAM, null))
        assertNull(DietIntakeCalculator.purchaseAmountToGrams(-1f, FoodUnit.BY_SERVING, 50f))
    }

    // ---- T6 营养基准量归一化（包装按非 100g 标注的场景） ----

    @Test
    fun `normalizeToPer100g - 基准80g碳水25g - 归一化为31_25g`() {
        assertEquals(31.25f, DietIntakeCalculator.normalizeToPer100g(25f, 80f)!!, 0.001f)
    }

    @Test
    fun `normalizeToPer100g - 默认基准100 - 数值不变`() {
        assertEquals(25f, DietIntakeCalculator.normalizeToPer100g(25f, 100f)!!, 0.001f)
    }

    @Test
    fun `normalizeToPer100g - 基准无效或数值为负 - 返回null`() {
        assertNull(DietIntakeCalculator.normalizeToPer100g(25f, 0f))
        assertNull(DietIntakeCalculator.normalizeToPer100g(-1f, 80f))
    }

    @Test
    fun `场景B补充 - 收集箱鸡蛋场景按份计价 - 落库花费正确`() {
        // 鸡蛋：按份（1个=50g），10个 8 元 → 表单折算入库 totalAmount=500g
        val eggs = riceTemplate().copy(
            id = 3L,
            name = "鸡蛋",
            unitType = FoodUnit.BY_SERVING,
            weightPerServing = 50f,
            carbsPer100g = 1.1f,
            totalPrice = 8f,
            totalAmount = 500f
        )

        val record = DietIntakeCalculator.buildDietRecord(eggs, 1f, today)!!

        assertEquals(0.8f, record.cost, 0.001f) // 50g × (8 / 500)
        assertEquals(0.55f, record.intakeCarbs, 0.001f)
    }
}
