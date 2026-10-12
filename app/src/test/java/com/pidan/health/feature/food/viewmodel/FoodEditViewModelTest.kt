package com.pidan.health.feature.food.viewmodel

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.pidan.health.core.data.entity.FoodTemplate
import com.pidan.health.core.data.repository.FoodRepository
import com.pidan.health.core.data.storage.CoverImageCompressor
import com.pidan.health.core.data.storage.FoodImageStorage
import com.pidan.health.core.domain.FoodUnit
import com.pidan.health.core.ocr.OcrStrategy
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 新建/编辑食物页 ViewModel 验证（T6）：
 * 1. 选「按份」未填单份克重 → 无法保存（完成标准 1）；
 * 2. 钠 mg → 盐 g 换算经 SaltConverter 唯一入口落库（核心红线）；
 * 3. 购买总量按份输入 × 单份克重折算成克入库（收集箱按份计价方案）；
 * 4. 编辑态回填与保存复用同一页面。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FoodEditViewModelTest {

    private lateinit var foodRepo: FakeFoodRepository
    private lateinit var compressor: FakeCoverCompressor

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        foodRepo = FakeFoodRepository()
        compressor = FakeCoverCompressor()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel(foodId: Long? = null, ocrStrategy: OcrStrategy = FakeOcrStrategy()): FoodEditViewModel {
        val handle = SavedStateHandle().apply {
            foodId?.let { set(FoodEditViewModel.NAV_KEY_FOOD_ID, it) }
        }
        return FoodEditViewModel(
            foodRepository = foodRepo,
            foodImageStorage = FakeFoodImageStorage(),
            coverCompressor = compressor,
            ocrStrategy = ocrStrategy,
            savedStateHandle = handle,
            ioDispatcher = UnconfinedTestDispatcher()
        )
    }

    // ---- 完成标准 1：按份必须填单份克重 ----

    @Test
    fun `按份未填单份克重 - 无法保存且不落库`() = runTest {
        val vm = newViewModel()
        vm.onNameChange("鸡蛋")
        vm.onUnitTypeSelect(1) // 按份

        assertFalse(vm.canSave)
        assertFalse(vm.isServingValid)

        vm.save()
        assertTrue(foodRepo.all().isEmpty())

        // 补填后可保存
        vm.onWeightPerServingChange("50")
        assertTrue(vm.canSave)
        vm.save()
        assertEquals(1, foodRepo.all().size)
    }

    // ---- 盐换算：钠 mg → 盐 g（唯一入口 SaltConverter） ----

    @Test
    fun `钠600mg入库换算为盐1_5g`() = runTest {
        val vm = newViewModel()
        vm.onNameChange("挂面")
        vm.onNutritionChange(NutritionField.SODIUM, "600")

        // 预览：600mg → 1.5g
        assertEquals(1.5f, vm.saltPreviewGrams!!, 0.001f)

        vm.save()
        assertEquals(1.5f, foodRepo.all().single().saltPer100g, 0.001f)
    }

    @Test
    fun `钠未填时盐默认为0`() = runTest {
        val vm = newViewModel()
        vm.onNameChange("米饭")
        vm.save()
        assertEquals(0f, foodRepo.all().single().saltPer100g, 0.001f)
    }

    // ---- 收集箱按份计价：购买总量按份输入折算成克入库 ----

    @Test
    fun `购买总量按份输入 - 折算克入库且单价预览正确`() = runTest {
        val vm = newViewModel()
        vm.onNameChange("鸡蛋")
        vm.onWeightPerServingChange("50")
        vm.onPriceChange("8")
        vm.onPurchaseUnitSelect(1) // 按份
        vm.onPurchaseAmountChange("10")

        // 10 份 × 50g = 500g，8 元 / 500g = 1.6 元/100g
        assertEquals(1.6f, vm.unitPricePer100g!!, 0.001f)

        vm.save()
        val saved = foodRepo.all().single()
        assertEquals(500f, saved.totalAmount!!, 0.001f)
        assertEquals(8f, saved.totalPrice!!, 0.001f)
    }

    @Test
    fun `购买总量按份但缺单份克重 - 价格校验失败`() = runTest {
        val vm = newViewModel()
        vm.onPriceChange("8")
        vm.onPurchaseUnitSelect(1)
        vm.onPurchaseAmountChange("10")

        assertFalse(vm.isPriceValid)
        assertFalse(vm.canSave)
        assertNull(vm.unitPricePer100g)
    }

    @Test
    fun `价格区按份且计量单位按克 - 补填单份克重后可保存且折算入库`() = runTest {
        val vm = newViewModel()
        vm.onNameChange("坚果")
        vm.onPriceChange("30")
        vm.onPurchaseUnitSelect(1) // 价格区按份；计量单位保持按克（单份克重输入框由价格卡补显）
        vm.onPurchaseAmountChange("10")

        assertFalse(vm.isPriceValid)
        assertTrue(vm.isPriceServingWeightMissing)

        vm.onWeightPerServingChange("250")
        assertTrue(vm.isPriceValid)
        assertTrue(vm.canSave)

        vm.save()
        val saved = foodRepo.all().single()
        assertEquals(2500f, saved.totalAmount!!, 0.001f) // 10 份 × 250g
        assertEquals(30f, saved.totalPrice!!, 0.001f)
        assertEquals(250f, saved.weightPerServing!!, 0.001f) // 单份克重随模板保存，编辑回填不丢
    }

    @Test
    fun `金额与总量只填一个 - 价格校验失败`() = runTest {
        val vm = newViewModel()
        vm.onNameChange("米饭")
        vm.onPriceChange("10")
        assertFalse(vm.isPriceValid)

        vm.onPriceChange("")
        vm.onPurchaseAmountChange("1000")
        assertFalse(vm.isPriceValid)

        vm.onPriceChange("10")
        assertTrue(vm.isPriceValid)
    }

    // ---- 编辑复用 ----

    @Test
    fun `编辑态 - 模板回填且盐g反推为钠mg`() = runTest {
        foodRepo.seed(
            FoodTemplate(
                id = 7L,
                name = "鸡胸肉",
                unitType = FoodUnit.BY_GRAM,
                carbsPer100g = 1.1f,
                saltPer100g = 0.16f,
                totalPrice = 30f,
                totalAmount = 1500f
            )
        )
        val vm = newViewModel(foodId = 7L)

        val state = vm.uiState.value
        assertTrue(state.isEdit)
        assertEquals("鸡胸肉", state.name)
        // 0.16g 盐 → 64mg 钠
        assertEquals("64", state.sodiumMgText)
        assertEquals("30", state.priceText)

        // 改名保存 → update 而非 insert
        vm.onNameChange("去皮鸡胸肉")
        vm.save()
        val saved = foodRepo.all().single()
        assertEquals(7L, saved.id)
        assertEquals("去皮鸡胸肉", saved.name)
        assertEquals(1, foodRepo.all().size)
    }

    // ---- 封面压缩编排 ----

    @Test
    fun `图片压缩成功 - 临时文件挂到pending保存时收编`() = runTest {
        val vm = newViewModel()
        vm.onNameChange("米饭")
        vm.onImagePicked("content://test/img")
        assertEquals(compressor.lastInput, vm.uiState.value.pendingCoverFile)

        vm.save()
        // 保存时收编：compressor 产出的临时文件被 adopt 成相对路径
        assertEquals("fake_cover.webp", foodRepo.all().single().coverImagePath)
    }

    @Test
    fun `图片压缩失败 - coverFailed置位且不阻断保存`() = runTest {
        compressor.failNext = true
        val vm = newViewModel()
        vm.onNameChange("米饭")
        vm.onImagePicked("content://test/img")

        assertTrue(vm.uiState.value.coverFailed)
        assertNull(vm.uiState.value.pendingCoverFile)

        vm.consumeCoverError()
        assertFalse(vm.uiState.value.coverFailed)

        vm.save()
        assertNull(foodRepo.all().single().coverImagePath)
    }

    // ---- 营养基准量：包装按非 100g 标注，入库自动归一化为每 100g ----

    @Test
    fun `基准80g录入 - 碳水25钠600mg - 入库碳水31_25盐1_875g`() = runTest {
        val vm = newViewModel()
        vm.onNameChange("小包装零食")
        vm.onBasisGramsChange("80")
        vm.onNutritionChange(NutritionField.CARBS, "25")
        vm.onNutritionChange(NutritionField.SODIUM, "600")

        // 预览：600mg/80g → 750mg/100g → 1.875g 盐
        assertEquals(1.875f, vm.saltPreviewGrams!!, 0.001f)

        vm.save()
        val saved = foodRepo.all().single()
        assertEquals(31.25f, saved.carbsPer100g, 0.001f)
        assertEquals(1.875f, saved.saltPer100g, 0.001f)
    }

    @Test
    fun `基准默认100 - 入库数值与输入一致`() = runTest {
        val vm = newViewModel()
        vm.onNameChange("米饭")
        vm.onNutritionChange(NutritionField.CARBS, "25")
        vm.save()
        assertEquals(25f, foodRepo.all().single().carbsPer100g, 0.001f)
        assertEquals("100", vm.uiState.value.basisGramsText)
    }

    @Test
    fun `基准清空或非正数 - 无法保存`() = runTest {
        val vm = newViewModel()
        vm.onNameChange("米饭")
        vm.onBasisGramsChange("")
        assertFalse(vm.isBasisValid)
        assertFalse(vm.canSave)
        vm.save()
        assertTrue(foodRepo.all().isEmpty())

        vm.onBasisGramsChange("0")
        assertFalse(vm.canSave)
    }

    // ---- formatNumber ----

    @Test
    fun `formatNumber - 去尾零`() {
        assertEquals("25", FoodEditViewModel.formatNumber(25f))
        assertEquals("0.16", FoodEditViewModel.formatNumber(0.16f))
        assertEquals("1.5", FoodEditViewModel.formatNumber(1.5f))
        assertEquals("0", FoodEditViewModel.formatNumber(0f))
    }

    // ---- OCR 拍照识别（T8）----

    @Test
    fun `OCR识别成功 - 营养表数值与基准回填表单`() = runTest {
        val ocr = FakeOcrStrategy(
            lines = listOf(
                "营养成分表",
                "项目每80gNRV%",
                "蛋白质6.4克",
                "脂肪1.2克",
                "碳水化合物56.3克",
                "膳食纤维0克",
                "钠120毫克"
            )
        )
        val vm = newViewModel(ocrStrategy = ocr)
        vm.onNameChange("燕麦片")

        vm.onOcrImageCaptured("content://test/ocr")

        // 原图字节送达 OCR（未压缩保精度）
        assertTrue(ocr.lastImageBytes!!.isNotEmpty())
        val state = vm.uiState.value
        assertFalse(state.ocrFailed)
        assertFalse(state.isRecognizing)
        assertEquals("80", state.basisGramsText)
        assertEquals("56.3", state.carbsText)
        assertEquals("6.4", state.proteinText)
        assertEquals("1.2", state.fatText)
        assertEquals("0", state.fiberText)
        assertEquals("120", state.sodiumMgText)
        // 名称等其余字段不受影响
        assertEquals("燕麦片", state.name)

        // 识别结果落在表单中，可手动修正（保留手动修正入口）
        vm.onNutritionChange(NutritionField.SODIUM, "100")
        assertEquals("100", vm.uiState.value.sodiumMgText)
    }

    @Test
    fun `OCR识别成功 - 未命中的字段保留用户已填内容`() = runTest {
        val ocr = FakeOcrStrategy(lines = listOf("蛋白质3g"))
        val vm = newViewModel(ocrStrategy = ocr)
        vm.onNutritionChange(NutritionField.CARBS, "50")

        vm.onOcrImageCaptured("content://test/ocr")

        assertEquals("50", vm.uiState.value.carbsText)
        assertEquals("3", vm.uiState.value.proteinText)
    }

    @Test
    fun `OCR接口失败 - ocrFailed置位 - 表单原值不动`() = runTest {
        val ocr = FakeOcrStrategy(fail = true)
        val vm = newViewModel(ocrStrategy = ocr)
        vm.onNutritionChange(NutritionField.CARBS, "25")

        vm.onOcrImageCaptured("content://test/ocr")

        assertTrue(vm.uiState.value.ocrFailed)
        assertFalse(vm.uiState.value.isRecognizing)
        assertEquals("25", vm.uiState.value.carbsText)

        // 提示消费后复位
        vm.consumeOcrError()
        assertFalse(vm.uiState.value.ocrFailed)
    }

    @Test
    fun `OCR图片读取失败或解析不出营养成分 - 同样降级提示`() = runTest {
        compressor.failReadOcr = true
        val vm = newViewModel(ocrStrategy = FakeOcrStrategy(lines = listOf("蛋白质3g")))

        vm.onOcrImageCaptured("content://test/ocr")
        assertTrue(vm.uiState.value.ocrFailed)
        compressor.failReadOcr = false

        // 识别出文字但不是营养表 → 解析失败 → 降级
        val vm2 = newViewModel(ocrStrategy = FakeOcrStrategy(lines = listOf("净含量500g")))
        vm2.onOcrImageCaptured("content://test/ocr")
        assertTrue(vm2.uiState.value.ocrFailed)
    }

    // ---- 测试替身 ----

    private class FakeFoodRepository : FoodRepository {
        private val state = MutableStateFlow<List<FoodTemplate>>(emptyList())

        fun seed(template: FoodTemplate) {
            state.value = state.value + template
        }

        fun all(): List<FoodTemplate> = state.value

        override fun observeAll(): Flow<List<FoodTemplate>> = state

        override suspend fun getById(id: Long): FoodTemplate? = state.value.firstOrNull { it.id == id }

        override fun observeById(id: Long): Flow<FoodTemplate?> =
            state.map { list -> list.firstOrNull { it.id == id } }

        private var nextId = 100L

        override suspend fun insert(food: FoodTemplate): Long {
            val withId = food.copy(id = nextId++)
            state.value = state.value + withId
            return withId.id
        }

        override suspend fun update(food: FoodTemplate) {
            state.value = state.value.map { if (it.id == food.id) food else it }
        }

        override suspend fun delete(food: FoodTemplate) {
            state.value = state.value.filterNot { it.id == food.id }
        }

        override suspend fun deleteByIds(ids: List<Long>) {
            state.value = state.value.filterNot { it.id in ids }
        }
    }

    private class FakeCoverCompressor : CoverImageCompressor {
        var failNext = false
        var lastInput: File? = null
        var failReadOcr = false

        override fun compressToCache(sourceUri: String): File? {
            if (failNext) return null
            return File("build/tmp_test_cover.webp").also { lastInput = it }
        }

        override fun prepareCameraCapture(): Uri = Uri.parse("content://fake/camera")!!

        override fun prepareOcrBytes(sourceUri: String): ByteArray? =
            if (failReadOcr) null else byteArrayOf(1, 2, 3)
    }

    private class FakeOcrStrategy(
        private val lines: List<String>? = null,
        private val fail: Boolean = false
    ) : OcrStrategy {
        var lastImageBytes: ByteArray? = null

        override suspend fun recognize(imageBytes: ByteArray): Result<List<String>> {
            lastImageBytes = imageBytes
            if (fail) return Result.failure(IllegalStateException("网络错误"))
            return lines?.let { Result.success(it) }
                ?: Result.failure(IllegalStateException("未配置"))
        }
    }

    private class FakeFoodImageStorage : FoodImageStorage {
        override fun resolve(relativePath: String?): File? = null
        override fun coverDir(): File = File("unused")
        override fun adopt(tempFile: File): String? = "fake_cover.webp"
        override fun delete(relativePath: String?) = Unit
    }
}
