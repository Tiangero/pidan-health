package com.pidan.health.feature.food.viewmodel

import com.pidan.health.core.common.time.DateProvider
import com.pidan.health.core.data.entity.DietRecord
import com.pidan.health.core.data.entity.FoodTemplate
import com.pidan.health.core.data.model.DailySummary
import com.pidan.health.core.data.model.DatedDailySummary
import com.pidan.health.core.data.model.FoodRankItem
import com.pidan.health.core.data.repository.DietRepository
import com.pidan.health.core.data.repository.FoodRepository
import com.pidan.health.core.data.storage.FoodImageStorage
import com.pidan.health.core.domain.FoodUnit
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 食物库快捷录入链路验证（场景 B 落库 + 历史冻结红线）。
 * 用内存 Fake 仓库模拟 Room；核心断言：
 * 1. 录入数值与《数据库表结构》场景 B 完全一致；
 * 2. 删除食物模板后，已固化 diet_record 数据一字不变。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FoodLibraryViewModelTest {

    private val today = "2026-10-10"

    private lateinit var foodRepo: FakeFoodRepository
    private lateinit var dietRepo: FakeDietRepository
    private lateinit var imageStorage: FakeFoodImageStorage
    private lateinit var viewModel: FoodLibraryViewModel

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

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        foodRepo = FakeFoodRepository()
        dietRepo = FakeDietRepository()
        imageStorage = FakeFoodImageStorage()
        viewModel = FoodLibraryViewModel(
            foodRepository = foodRepo,
            dietRepository = dietRepo,
            foodImageStorage = imageStorage,
            dateProvider = FakeDateProvider(today)
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `快捷录入1_5份米饭 - 落库数值与场景B完全一致`() = runTest {
        foodRepo.seed(riceTemplate())

        viewModel.startQuickEntry(viewModel.uiState.value.items.first())
        viewModel.confirmQuickEntry("1.5")

        val record = dietRepo.records.single()
        assertEquals(1L, record.foodId)
        assertEquals(today, record.date)
        assertEquals(1.5f, record.amount, 0.001f)
        assertEquals(FoodUnit.BY_SERVING, record.unitType)
        assertEquals("米饭", record.foodName)
        assertEquals(56.25f, record.intakeCarbs, 0.001f)
        assertEquals(2.25f, record.cost, 0.001f)
        // 录入成功后弹窗关闭
        assertNull(viewModel.uiState.value.entryTarget)
    }

    @Test
    fun `删除食物模板后 - 已固化的diet_record一字不变`() = runTest {
        foodRepo.seed(riceTemplate())
        viewModel.startQuickEntry(viewModel.uiState.value.items.first())
        viewModel.confirmQuickEntry("1.5")
        val recordBefore = dietRepo.records.single()

        foodRepo.delete(riceTemplate())

        assertEquals(0, foodRepo.all().size)
        val recordAfter = dietRepo.records.single()
        assertEquals(recordBefore, recordAfter)
    }

    @Test
    fun `无效数量 - 不落库 - 弹窗保持`() = runTest {
        foodRepo.seed(riceTemplate())
        viewModel.startQuickEntry(viewModel.uiState.value.items.first())

        listOf("0", "-1", "abc", "").forEach { invalid ->
            viewModel.confirmQuickEntry(invalid)
            assertTrue(dietRepo.records.isEmpty())
        }
        // 弹窗仍开着，等待用户改输入
        assertTrue(viewModel.uiState.value.entryTarget != null)
    }

    @Test
    fun `列表观察 - 模板变化实时映射到UI条目`() = runTest {
        foodRepo.seed(riceTemplate())
        assertEquals(listOf("按份 · 150g/份"), viewModel.uiState.value.items.map { it.unitLabel })
        assertTrue(viewModel.uiState.value.items.first().coverFile == null)

        foodRepo.seed(riceTemplate().copy(id = 2L, name = "鸡胸肉", unitType = FoodUnit.BY_GRAM))
        assertEquals(2, viewModel.uiState.value.items.size)
        assertTrue(viewModel.uiState.value.items.any { it.unitLabel == "按克" })
    }

    // ---- 管理模式（T7）----

    @Test
    fun `进入管理模式 - 勾选 - 全选 - 取消全选 - 状态流转正确`() = runTest {
        foodRepo.seed(riceTemplate())
        foodRepo.seed(riceTemplate().copy(id = 2L, name = "鸡胸肉", unitType = FoodUnit.BY_GRAM))

        viewModel.enterManageMode()
        assertTrue(viewModel.uiState.value.isManageMode)
        assertEquals(emptySet<Long>(), viewModel.uiState.value.selectedIds)

        viewModel.toggleSelected(1L)
        assertEquals(setOf(1L), viewModel.uiState.value.selectedIds)

        // 重复勾选同一项 = 取消勾选
        viewModel.toggleSelected(1L)
        assertEquals(emptySet<Long>(), viewModel.uiState.value.selectedIds)

        viewModel.toggleSelected(1L)
        viewModel.toggleSelectAll()
        assertEquals(setOf(1L, 2L), viewModel.uiState.value.selectedIds)

        // 已全选时再点全选 = 取消全选
        viewModel.toggleSelectAll()
        assertEquals(emptySet<Long>(), viewModel.uiState.value.selectedIds)
    }

    @Test
    fun `批量删除确认 - 模板移除 - 选中清空并退出管理模式`() = runTest {
        foodRepo.seed(riceTemplate())
        foodRepo.seed(riceTemplate().copy(id = 2L, name = "鸡胸肉", unitType = FoodUnit.BY_GRAM))

        viewModel.enterManageMode()
        viewModel.toggleSelected(1L)
        viewModel.requestDeleteSelected()
        assertTrue(viewModel.uiState.value.isDeleteConfirmVisible)

        viewModel.confirmDeleteSelected()

        assertEquals(listOf(2L), foodRepo.all().map { it.id })
        val state = viewModel.uiState.value
        assertTrue(!state.isManageMode)
        assertEquals(emptySet<Long>(), state.selectedIds)
        assertTrue(!state.isDeleteConfirmVisible)
        assertEquals(1, viewModel.deleteSuccessCount.value)
    }

    @Test
    fun `取消删除确认弹窗 - 不执行删除`() = runTest {
        foodRepo.seed(riceTemplate())
        viewModel.enterManageMode()
        viewModel.toggleSelected(1L)
        viewModel.requestDeleteSelected()

        viewModel.dismissDeleteConfirm()

        assertEquals(1, foodRepo.all().size)
        assertTrue(!viewModel.uiState.value.isDeleteConfirmVisible)
        // 管理模式与勾选保持，用户可继续调整
        assertTrue(viewModel.uiState.value.isManageMode)
        assertEquals(setOf(1L), viewModel.uiState.value.selectedIds)
    }

    @Test
    fun `批量删除后 - 已固化的diet_record一字不变 - 历史冻结红线`() = runTest {
        foodRepo.seed(riceTemplate())
        viewModel.startQuickEntry(viewModel.uiState.value.items.first())
        viewModel.confirmQuickEntry("1.5")
        val recordBefore = dietRepo.records.single()

        foodRepo.seed(riceTemplate().copy(id = 2L, name = "鸡胸肉", unitType = FoodUnit.BY_GRAM))
        viewModel.enterManageMode()
        viewModel.toggleSelected(1L)
        viewModel.confirmDeleteSelected()

        // 模板已删，但固化记录一字不变
        assertEquals(listOf(2L), foodRepo.all().map { it.id })
        assertEquals(recordBefore, dietRepo.records.single())
    }

    @Test
    fun `批量删除 - 同步清理对应沙盒封面文件`() = runTest {
        val withCover = riceTemplate().copy(id = 1L, coverImagePath = "cover_1.webp")
        foodRepo.seed(withCover)
        foodRepo.seed(riceTemplate().copy(id = 2L, name = "鸡胸肉", unitType = FoodUnit.BY_GRAM))

        viewModel.enterManageMode()
        viewModel.toggleSelected(1L)
        viewModel.confirmDeleteSelected()

        assertEquals(listOf("cover_1.webp"), imageStorage.deletedPaths)
    }

    @Test
    fun `管理模式下 - 快捷录入被拦截`() = runTest {
        foodRepo.seed(riceTemplate())
        viewModel.enterManageMode()

        viewModel.startQuickEntry(viewModel.uiState.value.items.first())

        assertNull(viewModel.uiState.value.entryTarget)
    }

    @Test
    fun `模板被外部删除 - 自动移出勾选集`() = runTest {
        foodRepo.seed(riceTemplate())
        foodRepo.seed(riceTemplate().copy(id = 2L, name = "鸡胸肉", unitType = FoodUnit.BY_GRAM))
        viewModel.enterManageMode()
        viewModel.toggleSelected(1L)
        viewModel.toggleSelected(2L)

        // 模拟其他入口删掉了 id=1 的模板，列表流自动更新
        foodRepo.deleteByIds(listOf(1L))

        assertEquals(setOf(2L), viewModel.uiState.value.selectedIds)
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

        override suspend fun insert(food: FoodTemplate): Long {
            state.value = state.value + food
            return food.id
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

    private class FakeDietRepository : DietRepository {
        val records = mutableListOf<DietRecord>()

        override suspend fun getDailySummary(date: String): DailySummary? = null
        override fun observeDailySummary(date: String): Flow<DailySummary?> = flowOf(null)
        override fun observeRecordsByDate(date: String): Flow<List<DietRecord>> = flowOf(emptyList())
        override suspend fun getAvgDailyCostOfLast7Days(startDate: String): Float? = null
        override suspend fun getTopFoodsBetween(
            startDate: String,
            endDate: String
        ): List<FoodRankItem> = emptyList()
        override suspend fun getDailySummariesBetween(
            startDate: String,
            endDate: String
        ): List<DatedDailySummary> = emptyList()
        override suspend fun insert(record: DietRecord): Long {
            records += record
            return record.id
        }
        override suspend fun deleteById(id: Long) {
            records.removeAll { it.id == id }
        }
        override suspend fun getAll(): List<DietRecord> = records.toList()
    }

    private class FakeFoodImageStorage : FoodImageStorage {
        val deletedPaths = mutableListOf<String?>()
        override fun resolve(relativePath: String?): File? = null
        override fun coverDir(): File = File("unused")
        override fun adopt(tempFile: File): String? = null
        override fun delete(relativePath: String?) {
            deletedPaths += relativePath
        }
    }

    private class FakeDateProvider(private val today: String) : DateProvider {
        override fun today(): String = today
        override fun daysAgo(days: Int): String = today
    }
}
