package com.pidan.health.core.domain

/**
 * 营养成分表 OCR 解析（T8，纯 JVM）：把通用 OCR 的按行文本解析成五大营养素 + 标注基准。
 *
 * 规则（对齐包装营养成分表排版）：
 * - 基准：从「每80g / 每 100 克 / 每100ml」等写法提取，缺省 100（与 T6 表单「营养基准量」同口径）；
 * - 碳水 = 碳水化合物；蛋白 = 蛋白质；脂肪行排除「反式/饱和」干扰项；纤维 = 膳食纤维；
 * - 钠按营养表惯例默认 mg，行内明确标「克/g」时 ×1000 换算成 mg（表单钠字段统一 mg 口径）；
 * - 每个营养素取第一处命中；能量（千焦/kJ）行忽略；NRV% 不干扰（取行内第一个数字）；
 * - **拆行配对**：拍摄倾斜时 OCR 常把「蛋白质」「12.8克g」拆成两行——标签行（无数字）与
 *   紧随其后的数值行配对（严格相邻：中间隔了其他无数字行即放弃，防跨行误配）；
 * - 一个营养素都没命中返回 null（视为解析失败，调用方降级手动录入）。
 */
object NutritionTableParser {

    /** 解析结果：值为「包装标注口径」的原始数值（归一化到每 100g 在入库时统一处理） */
    data class NutritionTableValues(
        val basisGrams: Float,
        val carbsG: Float?,
        val proteinG: Float?,
        val fatG: Float?,
        val fiberG: Float?,
        val sodiumMg: Float?
    ) {
        /** 命中的营养素项数（用于反馈与空结果判定） */
        val matchedCount: Int get() = listOfNotNull(carbsG, proteinG, fatG, fiberG, sodiumMg).size
    }

    /** 五大营养素关键词（关键词判定顺序即优先级，互斥取第一个命中） */
    private enum class Key { CARBS, PROTEIN, FAT, FIBER, SODIUM }

    fun parse(lines: List<String>): NutritionTableValues? {
        if (lines.isEmpty()) return null

        var basis: Float? = null
        var carbs: Float? = null
        var protein: Float? = null
        var fat: Float? = null
        var fiber: Float? = null
        var sodium: Float? = null

        /** 待配对的标签行（本行只有营养素关键词、没有数值） */
        var pendingKey: Key? = null

        fun assign(key: Key, line: String) {
            val num = firstNumber(line) ?: return
            when (key) {
                Key.CARBS -> if (carbs == null) carbs = num
                Key.PROTEIN -> if (protein == null) protein = num
                Key.FAT -> if (fat == null) fat = num
                Key.FIBER -> if (fiber == null) fiber = num
                Key.SODIUM -> if (sodium == null) sodium = sodiumToMg(line, num)
            }
        }

        for (raw in lines) {
            // 去空格统一比对：OCR 常在字与数字之间插空格
            val line = raw.trim().replace(" ", "").replace("\u00a0", "")
            if (line.isEmpty()) {
                pendingKey = null
                continue
            }
            if (basis == null) basis = parseBasisGrams(line)

            val key = matchKey(line)
            val num = firstNumber(line)
            when {
                // 标签行：同行有数值直接命中，否则挂起等下一行配对
                key != null -> {
                    if (num != null) {
                        assign(key, line)
                        pendingKey = null
                    } else {
                        pendingKey = key
                    }
                }
                // 紧随标签行的数值行（行内无其他关键词）→ 配对
                pendingKey != null && num != null -> {
                    assign(pendingKey, line)
                    pendingKey = null
                }
                // 其他无数字行打断配对，防跨行误配
                else -> pendingKey = null
            }
        }

        val values = NutritionTableValues(
            basisGrams = basis?.takeIf { it > 0f } ?: DEFAULT_BASIS_GRAMS,
            carbsG = carbs,
            proteinG = protein,
            fatG = fat,
            fiberG = fiber,
            sodiumMg = sodium
        )
        return values.takeIf { it.matchedCount > 0 }
    }

    /** 基准量写法：每80g / 每 100 克 / 每100ml / 每100毫升 */
    private val BASIS_REGEX = Regex("每(\\d+(?:\\.\\d+)?)(克|g|毫升|ml)", RegexOption.IGNORE_CASE)

    private val NUMBER_REGEX = Regex("\\d+(?:\\.\\d+)?")

    /** 基准量提取：正则第一个捕获组（数字部分） */
    private fun parseBasisGrams(line: String): Float? =
        BASIS_REGEX.find(line)?.groupValues?.get(1)?.toFloatOrNull()

    /** 行内第一个数字（NRV% 排在数值后，不会误取） */
    private fun firstNumber(line: String): Float? = NUMBER_REGEX.find(line)?.value?.toFloatOrNull()

    /** 关键词→营养素映射（脂肪排除反式/饱和干扰行） */
    private fun matchKey(line: String): Key? = when {
        line.contains("碳水化合物") -> Key.CARBS
        line.contains("蛋白质") -> Key.PROTEIN
        line.contains("脂肪") && !line.contains("反式") && !line.contains("饱和") -> Key.FAT
        line.contains("纤维") -> Key.FIBER
        line.contains("钠") -> Key.SODIUM
        else -> null
    }

    /** 钠单位判定：标「毫克/mg」→ 原值即 mg；标「克/g」→ ×1000；无单位按营养表惯例视为 mg */
    private fun sodiumToMg(line: String, value: Float): Float = when {
        line.contains("毫克") || line.contains("mg", ignoreCase = true) -> value
        line.contains("克") || line.contains("g", ignoreCase = true) -> value * 1000f
        else -> value
    }

    private const val DEFAULT_BASIS_GRAMS = 100f
}
