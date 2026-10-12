package com.pidan.health.feature.food.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pidan.health.core.data.entity.FoodTemplate
import com.pidan.health.core.data.repository.FoodRepository
import com.pidan.health.core.data.storage.CoverImageCompressor
import com.pidan.health.core.data.storage.FoodImageStorage
import com.pidan.health.core.domain.DietIntakeCalculator
import com.pidan.health.core.domain.FoodUnit
import com.pidan.health.core.domain.NutritionTableParser
import com.pidan.health.core.domain.SaltConverter
import com.pidan.health.core.ocr.OcrStrategy
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 新建/编辑食物页单条字段状态：全部以「输入框文本」为唯一真源，
 * 数值解析与校验收敛在 VM；派生展示（单价 / 盐换算预览）由 VM 计算后供 UI 直接展示。
 */
data class FoodEditUiState(
    val isLoading: Boolean = true,
    val isEdit: Boolean = false,
    // ---- 基本信息 ----
    val name: String = "",
    /** 已保存模板的封面（编辑态展示） */
    val existingCoverFile: File? = null,
    /** 本次新选、压缩完成但尚未随保存落盘的临时封面（优先生于已保存封面展示） */
    val pendingCoverFile: File? = null,
    val isCompressing: Boolean = false,
    /** 封面压缩失败标记（消费一次后由 VM 复位） */
    val coverFailed: Boolean = false,
    // ---- 计量单位 ----
    val unitType: Int = FoodUnit.BY_GRAM,
    val weightPerServingText: String = "",
    // ---- 营养成分（按包装基准输入，入库归一化为每 100g；盐以包装钠 mg 输入） ----
    /** 营养基准量 (g)：包装营养表的标注基准，默认 100；非 100 时入库自动换算为每 100g */
    val basisGramsText: String = FoodEditViewModel.DEFAULT_BASIS_GRAMS.toString(),
    val carbsText: String = "",
    val proteinText: String = "",
    val fatText: String = "",
    val fiberText: String = "",
    val sodiumMgText: String = "",
    // ---- 价格区 ----
    val priceText: String = "",
    val purchaseAmountText: String = "",
    /** 价格区「购买总量」输入单位：表单级概念，入库前一律折算成克 */
    val purchaseUnit: Int = FoodUnit.BY_GRAM,
    // ---- 提交 ----
    val isSaving: Boolean = false,
    /** 保存成功（一次性，UI 收到后返回列表页） */
    val isSaved: Boolean = false,
    // ---- OCR 拍照识别（T8）----
    /** 正在拍照识别（网络中，UI 按钮 spinner） */
    val isRecognizing: Boolean = false,
    /** 识别/解析失败（一次性，UI 提示「识别暂不可用」后由 VM 复位） */
    val ocrFailed: Boolean = false
)

/**
 * 新建/编辑食物 ViewModel：新建与编辑复用（路由 foodId 决定模式）。
 *
 * 职责：
 * - 编辑态加载模板回填（盐字段按 换算关系 反推为钠 mg 展示）；
 * - 表单校验（按份必填单份克重、价格成对填写）；
 * - 封面压缩编排（压缩结果先落 cache，保存成功才收编进沙盒）；
 * - OCR 拍照识别编排（T8）：原图字节 → OcrStrategy → 营养表解析 → 回填表单（结果落在表单可手动修正）；
 * - 盐换算唯一入口 [SaltConverter]：入库存盐 g，UI 输入钠 mg。
 */
class FoodEditViewModel(
    private val foodRepository: FoodRepository,
    private val foodImageStorage: FoodImageStorage,
    private val coverCompressor: CoverImageCompressor,
    private val ocrStrategy: OcrStrategy,
    savedStateHandle: SavedStateHandle,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val foodId: Long = savedStateHandle.get<Long>(NAV_KEY_FOOD_ID) ?: NEW_FOOD_ID

    private val _uiState = MutableStateFlow(FoodEditUiState(isEdit = foodId != NEW_FOOD_ID))
    val uiState: StateFlow<FoodEditUiState> = _uiState.asStateFlow()

    /** 编辑态加载出的原模板；保存时保留 createdAt 等不可变字段 */
    private var loadedTemplate: FoodTemplate? = null

    /** 压缩临时文件是否已随保存收编（未收编则在 onCleared 清理，避免孤儿文件） */
    private var pendingCoverConsumed = false

    init {
        if (foodId != NEW_FOOD_ID) {
            viewModelScope.launch {
                val template = foodRepository.getById(foodId)
                if (template == null) {
                    // 模板已不存在（如被批量删除）：直接退出，避免在脏数据上编辑
                    _uiState.update { it.copy(isSaved = true) }
                } else {
                    loadedTemplate = template
                    _uiState.update { current ->
                        current.fillFrom(template).copy(isLoading = false)
                    }
                }
            }
        } else {
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    // ---- 字段输入 ----

    fun onNameChange(value: String) = _uiState.update { it.copy(name = value) }

    fun onWeightPerServingChange(value: String) =
        _uiState.update { it.copy(weightPerServingText = value) }

    fun onUnitTypeSelect(index: Int) {
        val unitType = if (index == 1) FoodUnit.BY_SERVING else FoodUnit.BY_GRAM
        _uiState.update { it.copy(unitType = unitType) }
    }

    fun onBasisGramsChange(value: String) =
        _uiState.update { it.copy(basisGramsText = value) }

    fun onNutritionChange(field: NutritionField, value: String) = _uiState.update {
        when (field) {
            NutritionField.CARBS -> it.copy(carbsText = value)
            NutritionField.PROTEIN -> it.copy(proteinText = value)
            NutritionField.FAT -> it.copy(fatText = value)
            NutritionField.FIBER -> it.copy(fiberText = value)
            NutritionField.SODIUM -> it.copy(sodiumMgText = value)
        }
    }

    fun onPriceChange(value: String) = _uiState.update { it.copy(priceText = value) }

    fun onPurchaseAmountChange(value: String) =
        _uiState.update { it.copy(purchaseAmountText = value) }

    /** 价格区购买总量单位切换（0=按 g，1=按份），仅影响输入口径，入库一律折算为克 */
    fun onPurchaseUnitSelect(index: Int) {
        val unit = if (index == 1) FoodUnit.BY_SERVING else FoodUnit.BY_GRAM
        _uiState.update { it.copy(purchaseUnit = unit) }
    }

    // ---- 封面 ----

    /** 相机拍摄输出 Uri（TakePicture 契约入参，由压缩器统一管理临时文件） */
    fun cameraCaptureUri(): Uri = coverCompressor.prepareCameraCapture()

    /**
     * 相机/相册返回后压缩；失败置 coverFailed（UI 提示「压缩失败，请换一张试试」）。
     * 入参为 URI 字符串（UI 侧 uri.toString()），VM 不依赖 android.net.Uri。
     */
    fun onImagePicked(sourceUri: String) {
        viewModelScope.launch(ioDispatcher) {
            _uiState.update { it.copy(isCompressing = true, coverFailed = false) }
            val compressed = coverCompressor.compressToCache(sourceUri)
            _uiState.update {
                if (compressed != null) {
                    it.copy(isCompressing = false, pendingCoverFile = compressed)
                } else {
                    it.copy(isCompressing = false, coverFailed = true)
                }
            }
        }
    }

    fun consumeCoverError() = _uiState.update { it.copy(coverFailed = false) }

    // ---- OCR 拍照识别（T8）----

    /** OCR 拍摄输出 Uri（复用压缩器的相机临时文件管理，TakePicture 契约用） */
    fun prepareOcrCapture(): Uri = coverCompressor.prepareCameraCapture()

    /**
     * 拍照识别完成：读原图字节（不压缩保精度）→ OCR → 营养表解析 → 回填表单字段。
     * 任何一步失败都置 ocrFailed（UI 提示「识别暂不可用」），表单保持原值无缝切回手动录入。
     * 回填按包装基准口径填「基准量 + 各营养素 + 钠 mg」，入库归一化仍由保存链路统一处理。
     */
    fun onOcrImageCaptured(sourceUri: String) {
        if (_uiState.value.isRecognizing) return
        viewModelScope.launch(ioDispatcher) {
            _uiState.update { it.copy(isRecognizing = true, ocrFailed = false) }
            val values = try {
                val bytes = requireNotNull(coverCompressor.prepareOcrBytes(sourceUri)) { "读取图片失败" }
                Log.d(TAG, "OCR step1 读图成功: ${bytes.size} 字节")
                val lines = ocrStrategy.recognize(bytes).getOrThrow()
                Log.d(TAG, "OCR step2 识别返回 ${lines.size} 行: $lines")
                val parsed = requireNotNull(NutritionTableParser.parse(lines)) { "未解析出营养成分" }
                Log.d(TAG, "OCR step3 解析结果: $parsed")
                parsed
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "OCR 链路失败", e)
                null
            }

            _uiState.update { current ->
                if (values == null) {
                    current.copy(isRecognizing = false, ocrFailed = true)
                } else {
                    // 只回填识别到的项，未命中的字段保留用户已填内容
                    current.copy(
                        isRecognizing = false,
                        basisGramsText = formatNumber(values.basisGrams),
                        carbsText = values.carbsG?.let(::formatNumber) ?: current.carbsText,
                        proteinText = values.proteinG?.let(::formatNumber) ?: current.proteinText,
                        fatText = values.fatG?.let(::formatNumber) ?: current.fatText,
                        fiberText = values.fiberG?.let(::formatNumber) ?: current.fiberText,
                        sodiumMgText = values.sodiumMg?.let(::formatNumber) ?: current.sodiumMgText
                    )
                }
            }
        }
    }

    fun consumeOcrError() = _uiState.update { it.copy(ocrFailed = false) }

    // ---- 派生校验与预览（UI 零计算） ----

    private fun parsePositive(text: String): Float? =
        text.toFloatOrNull()?.takeIf { it > 0f }

    private fun parseNonNegative(text: String): Float? =
        text.toFloatOrNull()?.takeIf { it >= 0f }

    /** 单份克重（按份时必填，返回 null 即校验失败） */
    private fun weightPerServingOrNull(): Float? =
        if (_uiState.value.unitType == FoodUnit.BY_SERVING) {
            parsePositive(_uiState.value.weightPerServingText)
        } else {
            null
        }

    /** 营养基准量（默认 100，须为正数；非法时不可保存） */
    private fun basisGramsOrNull(): Float? = parsePositive(_uiState.value.basisGramsText)

    val isBasisValid: Boolean get() = basisGramsOrNull() != null

    val isNameValid: Boolean get() = _uiState.value.name.isNotBlank()

    /** 选「按份」必须填单份克重（核心校验） */
    val isServingValid: Boolean
        get() = _uiState.value.unitType != FoodUnit.BY_SERVING || weightPerServingOrNull() != null

    /**
     * 价格校验：金额与总量要么都空（不计价），要么都有效；
     * 总量按份输入时依赖单份克重折算。
     */
    val isPriceValid: Boolean
        get() {
            val s = _uiState.value
            val priceBlank = s.priceText.isBlank()
            val amountBlank = s.purchaseAmountText.isBlank()
            if (priceBlank && amountBlank) return true
            if (priceBlank != amountBlank) return false
            val price = parseNonNegative(s.priceText) ?: return false
            if (price < 0f) return false
            return purchaseAmountToGrams() != null
        }

    /**
     * 价格区按份输入时单份克重是否缺失（金额与总量已成对填写，仅缺折算依据）。
     * 用于价格卡内单份克重字段的独立错误提示，与「成对填写」提示区分。
     */
    val isPriceServingWeightMissing: Boolean
        get() {
            val s = _uiState.value
            if (s.purchaseUnit != FoodUnit.BY_SERVING) return false
            if (s.priceText.isBlank() || s.purchaseAmountText.isBlank()) return false
            return parsePositive(s.weightPerServingText) == null
        }

    val canSave: Boolean
        get() {
            val s = _uiState.value
            return isNameValid && isBasisValid && isServingValid && isPriceValid &&
                !s.isSaving && !s.isCompressing
        }

    /** 购买总量（按表单输入单位）折算为入库克数；未填或无效返回 null */
    private fun purchaseAmountToGrams(): Float? {
        val s = _uiState.value
        val amount = parsePositive(s.purchaseAmountText) ?: return null
        val serving = if (s.purchaseUnit == FoodUnit.BY_SERVING) parsePositive(s.weightPerServingText) else null
        return DietIntakeCalculator.purchaseAmountToGrams(amount, s.purchaseUnit, serving)
    }

    /** 单价预览（元/100g），无有效价格时返回 null 由 UI 展示占位 */
    val unitPricePer100g: Float?
        get() = DietIntakeCalculator.unitPricePer100Grams(
            totalPrice = parseNonNegative(_uiState.value.priceText),
            totalAmountGrams = purchaseAmountToGrams()
        )

    /**
     * 钠 → 盐换算预览（入库口径的每 100g 盐当量）。
     * 钠按包装基准输入，先归一化到每 100g 再换算；未填钠或基准非法返回 null。
     */
    val saltPreviewGrams: Float?
        get() {
            val basis = basisGramsOrNull() ?: return null
            return parseNonNegative(_uiState.value.sodiumMgText)
                ?.let { DietIntakeCalculator.normalizeToPer100g(it, basis) }
                ?.let(SaltConverter::sodiumMgToSaltGrams)
        }

    // ---- 保存 ----

    fun save() {
        if (!canSave) return
        val snapshot = _uiState.value
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch(ioDispatcher) {
            // 压缩临时封面随保存收编进沙盒；收编失败降级为无封面（或编辑态保留旧封面），不阻断保存
            val pendingPath = snapshot.pendingCoverFile?.let { foodImageStorage.adopt(it) }
            pendingCoverConsumed = pendingPath != null
            val coverPath = pendingPath
                ?: loadedTemplate?.coverImagePath?.takeIf { snapshot.existingCoverFile != null }

            // 营养素按包装基准输入，入库统一归一化为每 100g（canSave 已保证基准有效）
            val basis = basisGramsOrNull() ?: return@launch
            val per100g: (Float) -> Float = { value ->
                DietIntakeCalculator.normalizeToPer100g(value, basis) ?: 0f
            }

            val saltPer100g = per100g(parseNonNegative(snapshot.sodiumMgText) ?: 0f)
                .let(SaltConverter::sodiumMgToSaltGrams)

            val base = loadedTemplate ?: FoodTemplate(name = snapshot.name.trim(), unitType = snapshot.unitType)
            val template = base.copy(
                name = snapshot.name.trim(),
                coverImagePath = coverPath,
                unitType = snapshot.unitType,
                // 单份克重非空即存：计量单位按份时必填；计量单位按克但价格区按份折算时同样需要
                weightPerServing = snapshot.weightPerServingText.toFloatOrNull()?.takeIf { it > 0f },
                carbsPer100g = per100g(parseNonNegative(snapshot.carbsText) ?: 0f),
                proteinPer100g = per100g(parseNonNegative(snapshot.proteinText) ?: 0f),
                fatPer100g = per100g(parseNonNegative(snapshot.fatText) ?: 0f),
                fiberPer100g = per100g(parseNonNegative(snapshot.fiberText) ?: 0f),
                saltPer100g = saltPer100g,
                totalPrice = parseNonNegative(snapshot.priceText)?.takeIf { purchaseAmountToGrams() != null },
                totalAmount = purchaseAmountToGrams(),
                updatedAt = System.currentTimeMillis()
            )

            if (snapshot.isEdit) {
                foodRepository.update(template)
            } else {
                foodRepository.insert(template)
            }
            _uiState.update { it.copy(isSaving = false, isSaved = true) }
        }
    }

    /** 未保存就退出时清理压缩临时文件（已收编的不动） */
    override fun onCleared() {
        if (!pendingCoverConsumed) {
            _uiState.value.pendingCoverFile?.delete()
        }
        super.onCleared()
    }

    // ---- 编辑态回填 ----

    private fun FoodEditUiState.fillFrom(template: FoodTemplate): FoodEditUiState = copy(
        name = template.name,
        existingCoverFile = foodImageStorage.resolve(template.coverImagePath),
        unitType = template.unitType,
        weightPerServingText = template.weightPerServing?.let(::formatNumber) ?: "",
        // 库内一律是每 100g，回填基准默认 100（历史包装基准无从得知，直接按 100 展示即可）
        basisGramsText = DEFAULT_BASIS_GRAMS.toString(),
        carbsText = formatNumber(template.carbsPer100g),
        proteinText = formatNumber(template.proteinPer100g),
        fatText = formatNumber(template.fatPer100g),
        fiberText = formatNumber(template.fiberPer100g),
        // 库内存盐 g，表单展示钠 mg（换算唯一入口 SaltConverter）
        sodiumMgText = SaltConverter.saltGramsToSodiumMg(template.saltPer100g).let(::formatNumber),
        priceText = template.totalPrice?.let(::formatNumber) ?: "",
        purchaseAmountText = template.totalAmount?.let(::formatNumber) ?: "",
        purchaseUnit = FoodUnit.BY_GRAM
    )

    companion object {
        private const val TAG = "BaiduOcr"
        const val NAV_KEY_FOOD_ID = "foodId"
        const val NEW_FOOD_ID = -1L

        /** 营养基准量默认值（每 100g） */
        const val DEFAULT_BASIS_GRAMS = 100

        /** 数字展示去尾零：25.0 → "25"，0.16 → "0.16" */
        fun formatNumber(value: Float): String {
            val rounded = (value * 100).toInt() / 100f
            return if (rounded == rounded.toInt().toFloat()) {
                rounded.toInt().toString()
            } else {
                rounded.toString()
            }
        }
    }
}

/** 营养成分输入框标识（表单五字段统一走一个 onChange） */
enum class NutritionField { CARBS, PROTEIN, FAT, FIBER, SODIUM }
