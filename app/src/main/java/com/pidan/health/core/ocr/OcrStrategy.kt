package com.pidan.health.core.ocr

/**
 * OCR 策略接口（T8，既定抽象范例）：换供应商只需新增实现类，不改任何业务代码。
 * 策略只负责「图片 → 按行文字」，营养表解析归 domain 层 [com.pidan.health.core.domain.NutritionTableParser]。
 */
interface OcrStrategy {

    /**
     * 识别图片中的文字，按行（自上而下）返回。
     * 任何失败（未配置密钥 / 网络超时 / 鉴权失败 / 接口错误 / 空结果）
     * 都以 Result.failure 返回，不向调用方抛异常（红线：错误处理统一 Result）。
     *
     * @param imageBytes 原始图片字节（jpg/png/bmp）
     */
    suspend fun recognize(imageBytes: ByteArray): Result<List<String>>
}
