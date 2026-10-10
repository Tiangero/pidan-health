package com.pidan.health.core.domain

/**
 * 营养算法相关常量（产品规则硬编码，严禁散落到 UI / ViewModel）。
 *
 * 注意：碳/蛋/脂系数与纤维/盐默认目标的"用户可配置值"存于 UserPreferences 表；
 * 此处常量是判定容差与自定义开关关闭时的兜底值，两者需保持语义一致。
 */
object NutritionConstants {

    /** 碳水/脂肪固定值目标的绝对容差（±5g，非百分比），见《需求梳理》1.2 */
    const val CARBS_FAT_TOLERANCE_GRAMS = 5f

    /** 膳食纤维默认下限（自定义开关关闭时生效） */
    const val DEFAULT_FIBER_MIN_GRAMS = 25f

    /** 膳食纤维默认上限（自定义开关关闭时生效） */
    const val DEFAULT_FIBER_MAX_GRAMS = 30f

    /** 盐分默认上限（自定义开关关闭时生效） */
    const val DEFAULT_SALT_MAX_GRAMS = 5f

    /**
     * 钠与盐的换算基准：1g 盐 ≈ 400mg 钠。
     *
     * 重要：食品包装营养成分表标注的是【钠 (mg)】，本 App 全链路（模板、记录、快照、目标）
     * 统一存储/统计【盐 (g)】。T6 手动表单、T8 OCR 识别结果一律先经
     * [SaltConverter.sodiumMgToSaltGrams] 换算后再入库，严禁把钠当盐直接存储。
     */
    const val SODIUM_MG_PER_GRAM_SALT = 400f
}

/**
 * 钠 (mg) ⇄ 盐 (g) 换算。全 App 盐相关数值的唯一换算入口。
 */
object SaltConverter {

    /** 钠含量 (mg) → 盐当量 (g)。营养成分表/OCR 结果入库前必须调用 */
    fun sodiumMgToSaltGrams(sodiumMg: Float): Float = sodiumMg / NutritionConstants.SODIUM_MG_PER_GRAM_SALT

    /** 盐 (g) → 钠含量 (mg)（展示对照、反向校验用） */
    fun saltGramsToSodiumMg(saltGrams: Float): Float = saltGrams * NutritionConstants.SODIUM_MG_PER_GRAM_SALT
}
