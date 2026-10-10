package com.pidan.health.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 营养成分表 OCR 解析验证（T8）：标签命中、基准提取、干扰项排除、单位换算、失败判定。
 */
class NutritionTableParserTest {

    @Test
    fun `标准营养成分表 - 五项命中 - 钠mg基准100`() {
        val values = NutritionTableParser.parse(
            listOf(
                "营养成分表",
                "项目每100gNRV%",
                "能量1841千焦22%NRV",
                "蛋白质12.5克21%NRV",
                "脂肪3.0克5%NRV",
                "碳水化合物70.6克24%NRV",
                "膳食纤维0.2克1%NRV",
                "钠2530毫克126%NRV"
            )
        )!!
        assertEquals(100f, values.basisGrams, 0.001f)
        assertEquals(70.6f, values.carbsG!!, 0.001f)
        assertEquals(12.5f, values.proteinG!!, 0.001f)
        assertEquals(3.0f, values.fatG!!, 0.001f)
        assertEquals(0.2f, values.fiberG!!, 0.001f)
        assertEquals(2530f, values.sodiumMg!!, 0.001f)
        assertEquals(5, values.matchedCount)
    }

    @Test
    fun `基准每80g - 正确提取基准量`() {
        val values = NutritionTableParser.parse(
            listOf("项目每80gNRV%", "碳水化合物25克")
        )!!
        assertEquals(80f, values.basisGrams, 0.001f)
        assertEquals(25f, values.carbsG!!, 0.001f)
    }

    @Test
    fun `基准每100克与每100ml写法 - 均可识别`() {
        assertEquals(100f, NutritionTableParser.parse(listOf("每100克", "蛋白质1g"))!!.basisGrams, 0.001f)
        assertEquals(100f, NutritionTableParser.parse(listOf("每100ml", "蛋白质1g"))!!.basisGrams, 0.001f)
    }

    @Test
    fun `反式脂肪与饱和脂肪行 - 不误判为脂肪`() {
        val values = NutritionTableParser.parse(
            listOf("脂肪2克", "反式脂肪0克", "饱和脂肪1克")
        )!!
        assertEquals(2f, values.fatG!!, 0.001f)
    }

    @Test
    fun `反式脂肪行在前 - 脂肪取后续正确行`() {
        val values = NutritionTableParser.parse(
            listOf("反式脂肪(酸)0克", "脂肪3.5克")
        )!!
        assertEquals(3.5f, values.fatG!!, 0.001f)
    }

    @Test
    fun `钠标克单位 - 换算为mg`() {
        assertEquals(600f, NutritionTableParser.parse(listOf("钠0.6克"))!!.sodiumMg!!, 0.001f)
        assertEquals(600f, NutritionTableParser.parse(listOf("钠0.6g"))!!.sodiumMg!!, 0.001f)
    }

    @Test
    fun `钠标mg单位 - 原值保留`() {
        assertEquals(600f, NutritionTableParser.parse(listOf("钠(mg)600"))!!.sodiumMg!!, 0.001f)
        assertEquals(600f, NutritionTableParser.parse(listOf("钠600毫克"))!!.sodiumMg!!, 0.001f)
    }

    @Test
    fun `OCR空格混排 - 去空格后命中`() {
        val values = NutritionTableParser.parse(
            listOf("碳水化合物 25 g", "蛋 白 质 3 g")
        )!!
        assertEquals(25f, values.carbsG!!, 0.001f)
        assertEquals(3f, values.proteinG!!, 0.001f)
    }

    @Test
    fun `能量千焦行与NRV百分比 - 不干扰取值`() {
        val values = NutritionTableParser.parse(
            listOf("能量1841kJ", "蛋白质12.5g21%NRV")
        )!!
        assertNull(values.carbsG)
        assertNull(values.fatG)
        assertEquals(12.5f, values.proteinG!!, 0.001f)
    }

    @Test
    fun `部分命中 - 仅识别到的项返回`() {
        val values = NutritionTableParser.parse(
            listOf("某某食品", "蛋白质3.2克")
        )!!
        assertEquals(1, values.matchedCount)
        assertEquals(3.2f, values.proteinG!!, 0.001f)
        assertNull(values.carbsG)
        assertNull(values.sodiumMg)
    }

    @Test
    fun `无任何营养素关键词 - 返回null走降级`() {
        assertNull(NutritionTableParser.parse(listOf("净含量500g", "生产日期2026年")))
        assertNull(NutritionTableParser.parse(emptyList()))
    }

    @Test
    fun `无基准行 - 默认100g`() {
        val values = NutritionTableParser.parse(listOf("蛋白质1g"))!!
        assertEquals(100f, values.basisGrams, 0.001f)
    }

    // ---- 拆行配对（T8 真机联调：拍摄倾斜导致列被拆成独立行）----

    @Test
    fun `标签行与数值行拆开 - 相邻配对命中`() {
        val values = NutritionTableParser.parse(
            listOf("项目", "每100克g", "营养素参考值%", "能量", "1723千焦kJ", "21%", "蛋白质", "12.8克g", "21%", "脂肪", "19.4克g", "32%", "碳水化合物", "463克g", "15%", "钠", "46毫克mg", "2%")
        )!!
        assertEquals(100f, values.basisGrams, 0.001f)
        assertEquals(12.8f, values.proteinG!!, 0.001f)
        assertEquals(19.4f, values.fatG!!, 0.001f)
        assertEquals(463f, values.carbsG!!, 0.001f)
        assertEquals(46f, values.sodiumMg!!, 0.001f)
    }

    @Test
    fun `标签与数值之间隔了无数字行 - 严格相邻不跨行误配`() {
        val values = NutritionTableParser.parse(
            listOf("蛋白质", "用精更佳", "12.8克g")
        )
        // 「用精更佳」打断配对，蛋白质未命中；仅剩垃圾行 → 解析失败走降级
        assertNull(values)
    }

    @Test
    fun `标签行紧跟NRV百分比行 - 不把百分比当数值误配`() {
        val values = NutritionTableParser.parse(
            listOf("碳水化合物", "25克", "15%", "钠")
        )!!
        // 碳水与紧随的 25克 配对成功；末尾悬挂的「钠」没有数值行可配，不误配
        assertEquals(25f, values.carbsG!!, 0.001f)
        assertNull(values.sodiumMg)
    }

    @Test
    fun `直接命中优先 - 标签行自带数值时不用配对`() {
        val values = NutritionTableParser.parse(
            listOf("蛋白质12.5克", "脂肪", "3.0克")
        )!!
        assertEquals(12.5f, values.proteinG!!, 0.001f)
        assertEquals(3.0f, values.fatG!!, 0.001f)
    }
}
