package com.pidan.health.feature.food.viewmodel

import com.pidan.health.core.common.time.DateProvider
import com.pidan.health.core.data.entity.DietRecord
import com.pidan.health.core.data.entity.FoodTemplate
import com.pidan.health.core.data.model.DailySummary
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
        viewModel = FoodLibraryViewModel(
            foodRepository = foodRepo,
            dietRepository = dietRepo,
            foodImageStorage = FakeFoodImageStorage(),
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

    // ---- 测试替身 ----

    private class FakeFoodRepository : FoodRepository {
        private val state = MutableStateFlow<List<FoodTemplate>>(emptyList())

        fun seed(template: FoodTemplate) {
            state.value = state.value + template
        }

        fun all(): List<FoodTemplate> = state.value

        override fun observeAll(): Flow<List<FoodTemplate>> = state

        override suspend fun getById(id: Long): FoodTemplate? = state.value.firstOrNull { it.id == id }

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
        override suspend fun getAvgDailyCostOfLast7Days(startDate: String): Float? = null
        override suspend fun getTopFoodsOfMonth(monthPrefix: String): List<FoodRankItem> = emptyList()
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
        override fun resolve(relativePath: String?): File? = null
        override fun coverDir(): File = File("unused")
        override fun adopt(tempFile: File): String? = null
    }

    private class FakeDateProvider(private val today: String) : DateProvider {
        override fun today(): String = today
        override fun daysAgo(days: Int): String = today
    }
}
