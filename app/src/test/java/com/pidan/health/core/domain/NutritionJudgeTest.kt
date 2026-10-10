package com.pidan.health.core.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 《需求梳理》1.2 达标判定表全例覆盖。
 */
class NutritionJudgeTest {

    // ---- 碳水/脂肪：固定值 ±5g 绝对容差 ----
    // 文档示例：碳水目标 188g（达标区间 183–193g）：
    // 182g→橙、187g/188g/189g→绿、195g→红

    @Test
    fun `碳水188g目标 - 182g低于容差下沿 - 未达标`() {
        assertEquals(NutritionStatus.UNDER, NutritionJudge.judgeFixedValue(182f, 188f))
    }

    @Test
    fun `碳水188g目标 - 183g恰在容差下沿 - 达标`() {
        assertEquals(NutritionStatus.OK, NutritionJudge.judgeFixedValue(183f, 188f))
    }

    @Test
    fun `碳水188g目标 - 187g容差内 - 达标`() {
        assertEquals(NutritionStatus.OK, NutritionJudge.judgeFixedValue(187f, 188f))
    }

    @Test
    fun `碳水188g目标 - 188g精确命中 - 达标`() {
        assertEquals(NutritionStatus.OK, NutritionJudge.judgeFixedValue(188f, 188f))
    }

    @Test
    fun `碳水188g目标 - 189g容差内 - 达标`() {
        assertEquals(NutritionStatus.OK, NutritionJudge.judgeFixedValue(189f, 188f))
    }

    @Test
    fun `碳水188g目标 - 193g恰在容差上沿 - 达标`() {
        assertEquals(NutritionStatus.OK, NutritionJudge.judgeFixedValue(193f, 188f))
    }

    @Test
    fun `碳水188g目标 - 195g超出容差上沿 - 超标`() {
        assertEquals(NutritionStatus.OVER, NutritionJudge.judgeFixedValue(195f, 188f))
    }

    // ---- 蛋白质：区间严格判定 ----
    // 65kg × [1.3, 1.7] = [84.5, 110.5]

    @Test
    fun `蛋白65kg区间 - 低于下限 - 未达标`() {
        assertEquals(NutritionStatus.UNDER, NutritionJudge.judgeRange(80f, 84.5f, 110.5f))
    }

    @Test
    fun `蛋白65kg区间 - 恰在下限 - 达标`() {
        assertEquals(NutritionStatus.OK, NutritionJudge.judgeRange(84.5f, 84.5f, 110.5f))
    }

    @Test
    fun `蛋白65kg区间 - 区间中段 - 达标`() {
        assertEquals(NutritionStatus.OK, NutritionJudge.judgeRange(100f, 84.5f, 110.5f))
    }

    @Test
    fun `蛋白65kg区间 - 恰在上限 - 达标`() {
        assertEquals(NutritionStatus.OK, NutritionJudge.judgeRange(110.5f, 84.5f, 110.5f))
    }

    @Test
    fun `蛋白65kg区间 - 超出上限 - 超标`() {
        assertEquals(NutritionStatus.OVER, NutritionJudge.judgeRange(120f, 84.5f, 110.5f))
    }

    // ---- 膳食纤维：区间严格判定 [25, 30] ----

    @Test
    fun `纤维默认区间 - 低于下限 - 未达标`() {
        assertEquals(NutritionStatus.UNDER, NutritionJudge.judgeRange(24f, 25f, 30f))
    }

    @Test
    fun `纤维默认区间 - 下限与上限边界 - 达标`() {
        assertEquals(NutritionStatus.OK, NutritionJudge.judgeRange(25f, 25f, 30f))
        assertEquals(NutritionStatus.OK, NutritionJudge.judgeRange(30f, 25f, 30f))
    }

    @Test
    fun `纤维默认区间 - 超出上限 - 超标`() {
        assertEquals(NutritionStatus.OVER, NutritionJudge.judgeRange(31f, 25f, 30f))
    }

    // ---- 盐：只判上限无下限（文档示例：4.9g→绿、5.1g→红）----

    @Test
    fun `盐5g上限 - 4点9g - 正常`() {
        assertEquals(NutritionStatus.OK, NutritionJudge.judgeUpperLimit(4.9f, 5f))
    }

    @Test
    fun `盐5g上限 - 恰好5g - 正常`() {
        assertEquals(NutritionStatus.OK, NutritionJudge.judgeUpperLimit(5f, 5f))
    }

    @Test
    fun `盐5g上限 - 5点1g - 超标`() {
        assertEquals(NutritionStatus.OVER, NutritionJudge.judgeUpperLimit(5.1f, 5f))
    }
}
