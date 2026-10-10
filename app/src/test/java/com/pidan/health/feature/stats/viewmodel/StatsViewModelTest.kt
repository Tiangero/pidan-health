package com.pidan.health.feature.stats.viewmodel

import com.pidan.health.core.common.time.DateProvider
import com.pidan.health.core.data.entity.DailySnapshot
import com.pidan.health.core.data.entity.DietRecord
import com.pidan.health.core.data.model.DailySummary
import com.pidan.health.core.data.model.DatedDailySummary
import com.pidan.health.core.data.model.FoodRankItem
import com.pidan.health.core.data.model.WeightTrendItem
import com.pidan.health.core.data.repository.DietRepository
import com.pidan.health.core.data.repository.SnapshotRepository
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * 统计页 ViewModel 验证：周/月区间切换、达标偏差聚合、开销极值/累计、Top5 与性价比、空态。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModelTest {

    private val today = LocalDate.parse("2026-10-10") // 周六；本周一 = 10-05

    private lateinit var dietRepo: FakeDietRepo
    private lateinit var snapshotRepo: FakeSnapshotRepo

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        dietRepo = FakeDietRepo()
        snapshotRepo = FakeSnapshotRepo()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun seedWeekData() {
        snapshotRepo.snapshots += listOf(
            DailySnapshot(
                date = "2026-10-05", weight = 62.5f,
                targetCarbs = 200f, targetProteinMin = 80f, targetProteinMax = 105f, targetFat = 60f
            ),
            DailySnapshot(
                date = "2026-10-06", weight = 62.5f,
                targetCarbs = 100f, targetProteinMin = 80f, targetProteinMax = 105f, targetFat = 60f
            )
        )
        dietRepo.summaries += listOf(
            DatedDailySummary(
                date = "2026-10-05", totalCarbs = 150f, totalProtein = 88f,
                totalFat = 72f, totalFiber = 20f, totalCost = 22f
            ),
            DatedDailySummary(
                date = "2026-10-06", totalCarbs = 150f, totalProtein = 92f,
                totalFat = 68f, totalFiber = 25f, totalCost = 10f
            )
        )
        dietRepo.topFoods += listOf(
            FoodRankItem(foodName = "鸡胸肉", eatCount = 7, totalCost = 53.2f, totalProtein = 106.4f),
            FoodRankItem(foodName = "米饭", eatCount = 4, totalCost = 13.5f, totalProtein = 20f)
        )
    }

    @Test
    fun `默认本周 - 偏差与开销极值累计正确`() {
        seedWeekData()
        val viewModel = StatsViewModel(dietRepo, snapshotRepo, FixedDateProvider(today))

        val state = viewModel.uiState.value
        assertEquals(StatsRange.WEEK, state.range)
        assertEquals("本周", state.rangeLabel)

        val deviation = state.deviation!!
        assertEquals(150f, deviation.carbs.avgIntakeGrams, 0.001f)
        assertEquals(90f, deviation.protein.avgIntakeGrams, 0.001f)
        assertEquals(70f, deviation.fat.avgIntakeGrams, 0.001f)

        val trend = state.costTrend!!
        assertEquals(32f, trend.totalCost, 0.001f)
        assertEquals(22f, trend.maxPoint?.cost ?: -1f, 0.001f)
        assertEquals(10f, trend.minPoint?.cost ?: -1f, 0.001f)
        assertEquals(2, trend.points.size)

        assertEquals(2, state.topFoods.size)
        assertEquals("鸡胸肉", state.bestProteinSource?.foodName)
        assertEquals(2f, state.bestProteinSource?.proteinPerYuan ?: -1f, 0.001f)
    }

    @Test
    fun `切本月 - 只统计月区间数据且标签联动`() = runTest {
        seedWeekData()
        // 本月独有（本周区间外）：10-01 的记录
        snapshotRepo.snapshots += DailySnapshot(
            date = "2026-10-01", weight = 63f,
            targetCarbs = 150f, targetProteinMin = 80f, targetProteinMax = 105f, targetFat = 60f
        )
        dietRepo.summaries += DatedDailySummary(
            date = "2026-10-01", totalCarbs = 180f, totalProtein = 100f,
            totalFat = 60f, totalFiber = 28f, totalCost = 5f
        )
        val viewModel = StatsViewModel(dietRepo, snapshotRepo, FixedDateProvider(today))
        viewModel.setRange(StatsRange.MONTH)

        val state = viewModel.uiState.value
        assertEquals(StatsRange.MONTH, state.range)
        assertEquals("本月", state.rangeLabel)
        assertEquals(3, state.costTrend?.points?.size) // 10-01 + 周内两天
        assertEquals(37f, state.costTrend?.totalCost ?: -1f, 0.001f)
        // 月维度多了一天：碳水摄入均值 = (180 + 150 + 150) / 3 = 160
        assertEquals(160f, state.deviation?.carbs?.avgIntakeGrams ?: -1f, 0.001f)
    }

    @Test
    fun `空数据周期 - 三卡空态不崩溃`() = runTest {
        val viewModel = StatsViewModel(dietRepo, snapshotRepo, FixedDateProvider(today))
        viewModel.setRange(StatsRange.MONTH)

        val state = viewModel.uiState.value
        assertNull(state.deviation)
        assertNull(state.costTrend)
        assertEquals(0, state.topFoods.size)
        assertNull(state.bestProteinSource)
    }

    @Test
    fun `周区间边界 - 本周一至本周日`() {
        val viewModel = StatsViewModel(dietRepo, snapshotRepo, FixedDateProvider(today))
        // 初始化即请求本周区间（周一 10-05 ~ 周日 10-11）
        assertEquals("2026-10-05" to "2026-10-11", dietRepo.lastRange)
        assertEquals("2026-10-05" to "2026-10-11", snapshotRepo.lastRange)
    }
}

class FixedDateProvider(private val today: LocalDate) : DateProvider {
    override fun today(): String = today.toString()
    override fun daysAgo(days: Int): String = today.minusDays(days.toLong()).toString()
}

class FakeDietRepo : DietRepository {
    val summaries = mutableListOf<DatedDailySummary>()
    val topFoods = mutableListOf<FoodRankItem>()
    var lastRange: Pair<String, String>? = null
        private set

    override suspend fun getDailySummary(date: String): DailySummary? = null
    override fun observeDailySummary(date: String): Flow<DailySummary?> = flowOf(null)
    override suspend fun getAvgDailyCostOfLast7Days(startDate: String): Float? = null
    override suspend fun getDailySummariesBetween(
        startDate: String,
        endDate: String
    ): List<DatedDailySummary> {
        lastRange = startDate to endDate
        return summaries.filter { it.date >= startDate && it.date <= endDate }
    }

    override suspend fun getTopFoodsBetween(
        startDate: String,
        endDate: String
    ): List<FoodRankItem> = topFoods

    override suspend fun insert(record: DietRecord): Long = record.id
    override suspend fun deleteById(id: Long) = Unit
    override suspend fun getAll(): List<DietRecord> = emptyList()
}

class FakeSnapshotRepo : SnapshotRepository {
    val snapshots = mutableListOf<DailySnapshot>()
    var lastRange: Pair<String, String>? = null
        private set

    override suspend fun getSnapshot(date: String): DailySnapshot? =
        snapshots.firstOrNull { it.date == date }

    override fun observeSnapshot(date: String): Flow<DailySnapshot?> = flowOf(null)
    override suspend fun upsertSnapshot(snapshot: DailySnapshot) {
        snapshots.removeAll { it.date == snapshot.date }
        snapshots += snapshot
    }

    override suspend fun getRecentWeights(): List<WeightTrendItem> = emptyList()
    override suspend fun getSnapshotsBetween(
        startDate: String,
        endDate: String
    ): List<DailySnapshot> {
        lastRange = startDate to endDate
        return snapshots.filter { it.date >= startDate && it.date <= endDate }
    }

    override suspend fun getAll(): List<DailySnapshot> = snapshots.toList()
}
