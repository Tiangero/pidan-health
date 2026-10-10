package com.pidan.health.feature.exercise.viewmodel

import com.pidan.health.core.common.time.DateProvider
import com.pidan.health.core.data.entity.ExerciseRecord
import com.pidan.health.core.data.repository.ExerciseRepository
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
 * 运动月历打卡逻辑验证：
 * 1. 打卡/补签写入与显式休息删除（无记录 = 休息）；
 * 2. 未来日期拦截，不产生脏数据；
 * 3. 月度概览统计与月份切换。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExerciseViewModelTest {

    private val today = LocalDate.parse("2026-10-10")

    private lateinit var repo: FakeExerciseRepository
    private lateinit var viewModel: ExerciseViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repo = FakeExerciseRepository()
        viewModel = ExerciseViewModel(repo, FixedDateProvider(today))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `初始状态 - 默认展示当月且概览休息日为已过天数`() {
        assertEquals(YearMonth.of(2026, 10), viewModel.uiState.value.displayedMonth)
        assertEquals("2026 年 10 月", viewModel.uiState.value.monthLabel)
        // 10 月已过 10 天，全部休息
        assertEquals(0, viewModel.uiState.value.overview.aerobicDays)
        assertEquals(0, viewModel.uiState.value.overview.anaerobicDays)
        assertEquals(10, viewModel.uiState.value.overview.restDays)
    }

    @Test
    fun `打卡有氧 - 写入记录且概览更新`() = runTest {
        viewModel.onStatusPicked(today, ExerciseType.AEROBIC)

        val record = repo.records.values.single()
        assertEquals("2026-10-10", record.date)
        assertEquals(1, record.type)
        assertEquals(ExerciseType.AEROBIC, viewModel.uiState.value.statusByDate["2026-10-10"])
        assertEquals(1, viewModel.uiState.value.overview.aerobicDays)
        assertEquals(9, viewModel.uiState.value.overview.restDays)
    }

    @Test
    fun `补签历史日期 - 写入对应日期并正确显示`() = runTest {
        viewModel.onStatusPicked(LocalDate.parse("2026-10-03"), ExerciseType.ANAEROBIC)

        assertEquals("2026-10-03", repo.records.values.single().date)
        assertEquals(2, repo.records.values.single().type)
        assertEquals(
            ExerciseType.ANAEROBIC,
            viewModel.uiState.value.statusByDate["2026-10-03"]
        )
    }

    @Test
    fun `切换到休息 - 删除记录而非写入0`() = runTest {
        viewModel.onStatusPicked(today, ExerciseType.AEROBIC)
        viewModel.onStatusPicked(today, ExerciseType.REST)

        assertTrue(repo.records.isEmpty())
        assertTrue(repo.deletedDates.contains("2026-10-10"))
        assertEquals(10, viewModel.uiState.value.overview.restDays)
    }

    @Test
    fun `点击未来日期 - 不弹层不落库`() {
        viewModel.onDayClick(LocalDate.parse("2026-10-11"))

        assertNull(viewModel.uiState.value.pickerDate)

        // 即使绕过 UI 直接落未来状态也不被点击链路触发（双保险验证弹层拦截）
        viewModel.onStatusPicked(LocalDate.parse("2026-10-11"), ExerciseType.AEROBIC)
        assertTrue(repo.records.isEmpty())
    }

    @Test
    fun `切换月份 - 重新订阅区间且未来月份休息日为0`() = runTest {
        viewModel.selectMonth(YearMonth.of(2026, 9))

        assertEquals("2026 年 9 月", viewModel.uiState.value.monthLabel)
        assertEquals("2026-09-01", repo.lastObservedStart)
        assertEquals("2026-09-30", repo.lastObservedEnd)

        viewModel.selectMonth(YearMonth.of(2026, 11))
        assertEquals(0, viewModel.uiState.value.overview.restDays)
    }

    @Test
    fun `同月再次打卡 - 按主键覆盖不重复`() = runTest {
        viewModel.onStatusPicked(today, ExerciseType.AEROBIC)
        viewModel.onStatusPicked(today, ExerciseType.ANAEROBIC)

        assertEquals(1, repo.records.size)
        assertEquals(2, repo.records.values.single().type)
        assertFalse(
            viewModel.uiState.value.statusByDate.containsValue(ExerciseType.AEROBIC)
        )
    }
}

private class FixedDateProvider(private val date: LocalDate) : DateProvider {
    override fun today(): String = date.toString()
    override fun daysAgo(days: Int): String = date.minusDays(days.toLong()).toString()
}

/** 内存 Fake：模拟 exercise_record 表（主键 date 覆盖 + 区间订阅） */
private class FakeExerciseRepository : ExerciseRepository {

    val records = mutableMapOf<String, ExerciseRecord>()
    val deletedDates = mutableListOf<String>()
    var lastObservedStart: String? = null
        private set
    var lastObservedEnd: String? = null
        private set

    private val flow = MutableStateFlow<List<ExerciseRecord>>(emptyList())

    private fun notifyChanged() {
        flow.value = records.values.toList()
    }

    override suspend fun getByDate(date: String): ExerciseRecord? = records[date]

    override fun observeRange(startDate: String, endDate: String): Flow<List<ExerciseRecord>> {
        lastObservedStart = startDate
        lastObservedEnd = endDate
        return flow
    }

    override suspend fun upsert(record: ExerciseRecord) {
        records[record.date] = record
        notifyChanged()
    }

    override suspend fun deleteByDate(date: String) {
        records.remove(date)
        deletedDates.add(date)
        notifyChanged()
    }

    override suspend fun getAll(): List<ExerciseRecord> = records.values.toList()
}
