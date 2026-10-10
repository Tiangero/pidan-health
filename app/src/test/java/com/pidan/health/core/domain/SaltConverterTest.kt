package com.pidan.health.core.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 钠 ⇄ 盐换算验证（1g 盐 = 400mg 钠）。
 * 场景：营养成分表标注"钠 600mg/100g" → 入库盐应为 1.5g/100g。
 */
class SaltConverterTest {

    private val delta = 0.001f

    @Test
    fun `钠600mg - 换算为盐1点5g`() {
        assertEquals(1.5f, SaltConverter.sodiumMgToSaltGrams(600f), delta)
    }

    @Test
    fun `盐5g - 反算回钠2000mg`() {
        assertEquals(2000f, SaltConverter.saltGramsToSodiumMg(5f), delta)
    }

    @Test
    fun `往返换算 - 保值`() {
        val saltGrams = 2.3f
        assertEquals(saltGrams, SaltConverter.sodiumMgToSaltGrams(SaltConverter.saltGramsToSodiumMg(saltGrams)), delta)
    }

    @Test
    fun `零值与边界 - 换算不产生异常值`() {
        assertEquals(0f, SaltConverter.sodiumMgToSaltGrams(0f), delta)
        assertEquals(0f, SaltConverter.saltGramsToSodiumMg(0f), delta)
    }
}
