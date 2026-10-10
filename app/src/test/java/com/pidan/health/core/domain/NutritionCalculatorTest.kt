package com.pidan.health.core.domain

import com.pidan.health.core.data.entity.UserPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * 营养目标计算引擎（《需求梳理》1.1）。
 */
class NutritionCalculatorTest {

    private val delta = 0.001f

    @Test
    fun `默认系数 - 70kg - 目标值正确`() {
        val targets = NutritionCalculator.computeTargets(70f, UserPreferences().toNutritionFactors())

        assertEquals(210f, targets.carbsGrams, delta)          // 70 × 3
        assertEquals(91f, targets.proteinMinGrams, delta)      // 70 × 1.3
        assertEquals(119f, targets.proteinMaxGrams, delta)     // 70 × 1.7
        assertEquals(35f, targets.fatGrams, delta)             // 70 × 0.5
        assertEquals(25f, targets.fiberMinGrams, delta)
        assertEquals(30f, targets.fiberMaxGrams, delta)
        assertEquals(5f, targets.saltMaxGrams, delta)
    }

    @Test
    fun `自定义碳蛋脂系数 - 按新系数计算`() {
        val prefs = UserPreferences(
            carbsFactor = 2.5f,
            proteinMinFactor = 1.0f,
            proteinMaxFactor = 2.0f,
            fatFactor = 0.8f
        )
        val targets = NutritionCalculator.computeTargets(60f, prefs.toNutritionFactors())

        assertEquals(150f, targets.carbsGrams, delta)          // 60 × 2.5
        assertEquals(60f, targets.proteinMinGrams, delta)      // 60 × 1.0
        assertEquals(120f, targets.proteinMaxGrams, delta)     // 60 × 2.0
        assertEquals(48f, targets.fatGrams, delta)             // 60 × 0.8
    }

    @Test
    fun `纤维自定义开关关闭 - 使用默认区间25到30`() {
        val prefs = UserPreferences(isFiberCustom = false, fiberMin = 10f, fiberMax = 99f)
        val targets = NutritionCalculator.computeTargets(70f, prefs.toNutritionFactors())

        assertEquals(25f, targets.fiberMinGrams, delta)
        assertEquals(30f, targets.fiberMaxGrams, delta)
    }

    @Test
    fun `纤维自定义开关开启 - 使用自定义区间`() {
        val prefs = UserPreferences(isFiberCustom = true, fiberMin = 20f, fiberMax = 35f)
        val targets = NutritionCalculator.computeTargets(70f, prefs.toNutritionFactors())

        assertEquals(20f, targets.fiberMinGrams, delta)
        assertEquals(35f, targets.fiberMaxGrams, delta)
    }

    @Test
    fun `盐自定义开关关闭 - 使用默认上限5g`() {
        val prefs = UserPreferences(isSaltCustom = false, saltMax = 8f)
        val targets = NutritionCalculator.computeTargets(70f, prefs.toNutritionFactors())

        assertEquals(5f, targets.saltMaxGrams, delta)
    }

    @Test
    fun `盐自定义开关开启 - 使用自定义上限`() {
        val prefs = UserPreferences(isSaltCustom = true, saltMax = 6f)
        val targets = NutritionCalculator.computeTargets(70f, prefs.toNutritionFactors())

        assertEquals(6f, targets.saltMaxGrams, delta)
    }

    @Test
    fun `体重非正数 - 抛出异常拒绝计算`() {
        assertThrows(IllegalArgumentException::class.java) {
            NutritionCalculator.computeTargets(0f, UserPreferences().toNutritionFactors())
        }
        assertThrows(IllegalArgumentException::class.java) {
            NutritionCalculator.computeTargets(-3f, UserPreferences().toNutritionFactors())
        }
    }
}
