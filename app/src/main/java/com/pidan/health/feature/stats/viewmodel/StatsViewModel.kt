package com.pidan.health.feature.stats.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pidan.health.core.common.time.DateProvider
import com.pidan.health.core.data.model.DatedDailySummary
import com.pidan.health.core.data.model.FoodRankItem
import com.pidan.health.core.data.repository.DietRepository
import com.pidan.health.core.data.repository.SnapshotRepository
import com.pidan.health.core.domain.NutritionDeviationSummary
import com.pidan.health.core.domain.StatsAggregator
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** 统计维度：本周（周一至周日）/ 本月（1 号至月末） */
enum class StatsRange {
    WEEK,
    MONTH
}

/** 开销曲线的单日点（x 轴为日期，只含有记录的日子） */
data class CostPoint(
    val date: String,
    val dayLabel: String,
    val cost: Float
)

/** 开销趋势汇总：极值与区间累计（无数据日不计入极值） */
data class CostTrendStats(
    val points: List<CostPoint>,
    val totalCost: Float,
    val maxPoint: CostPoint?,
    val minPoint: CostPoint?
)

/** 性价比最优蛋白来源（每元蛋白质 g/元 最高的 Top5 项） */
data class BestProteinSource(
    val foodName: String,
    val proteinPerYuan: Float
)

data class StatsUiState(
    val isLoading: Boolean = true,
    val range: StatsRange = StatsRange.WEEK,
    val rangeLabel: String = "本周",
    val deviation: NutritionDeviationSummary? = null,
    val costTrend: CostTrendStats? = null,
    val topFoods: List<FoodRankItem> = emptyList(),
    val bestProteinSource: BestProteinSource? = null
)

/**
 * 统计分析页 ViewModel。
 * 周/月切换后拉取区间数据：达标偏差（均值摄入 vs 各日冻结目标均值，StatsAggregator）、
 * 开销逐日曲线（极值/累计）、高频食物 Top5（diet_record 快照字段聚合，历史冻结）。
 */
class StatsViewModel(
    private val dietRepository: DietRepository,
    private val snapshotRepository: SnapshotRepository,
    private val dateProvider: DateProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    private val today: LocalDate = LocalDate.parse(dateProvider.today())

    init {
        refresh(StatsRange.WEEK)
    }

    /** 切换统计维度（本周/本月） */
    fun setRange(range: StatsRange) {
        if (range == _uiState.value.range && !_uiState.value.isLoading) return
        refresh(range)
    }

    /** 回到前台重算（其他页录入后统计自动跟进） */
    fun refresh() {
        refresh(_uiState.value.range)
    }

    private fun refresh(range: StatsRange) {
        _uiState.update {
            it.copy(isLoading = true, range = range, rangeLabel = rangeLabelOf(range))
        }
        viewModelScope.launch {
            val start = startDateOf(range)
            val end = endDateOf(range)
            val summaries: List<DatedDailySummary>
            val snapshots: List<com.pidan.health.core.data.entity.DailySnapshot>
            val topFoods: List<FoodRankItem>
            coroutineScope {
                val summariesDeferred =
                    async { dietRepository.getDailySummariesBetween(start, end) }
                val snapshotsDeferred =
                    async { snapshotRepository.getSnapshotsBetween(start, end) }
                val topDeferred = async { dietRepository.getTopFoodsBetween(start, end) }
                summaries = summariesDeferred.await()
                snapshots = snapshotsDeferred.await()
                topFoods = topDeferred.await()
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    deviation = StatsAggregator.computeDeviation(summaries, snapshots),
                    costTrend = buildCostTrend(summaries, range),
                    topFoods = topFoods,
                    bestProteinSource = bestProteinSourceOf(topFoods)
                )
            }
        }
    }

    // ---- 区间计算（周 = 本周一至周日，月 = 本月 1 号至月末） ----

    private fun startDateOf(range: StatsRange): String = when (range) {
        StatsRange.WEEK -> today.with(DayOfWeek.MONDAY)
        StatsRange.MONTH -> today.withDayOfMonth(1)
    }.format(DateTimeFormatter.ISO_LOCAL_DATE)

    private fun endDateOf(range: StatsRange): String = when (range) {
        StatsRange.WEEK -> today.with(DayOfWeek.SUNDAY)
        StatsRange.MONTH -> today.withDayOfMonth(today.lengthOfMonth())
    }.format(DateTimeFormatter.ISO_LOCAL_DATE)

    private fun rangeLabelOf(range: StatsRange): String = when (range) {
        StatsRange.WEEK -> "本周"
        StatsRange.MONTH -> "本月"
    }

    // ---- 纯函数组装（单测直接覆盖） ----

    fun buildCostTrend(
        summaries: List<DatedDailySummary>,
        range: StatsRange
    ): CostTrendStats? {
        if (summaries.isEmpty()) return null
        val points = summaries.map { summary ->
            CostPoint(
                date = summary.date,
                dayLabel = dayLabelOf(summary.date, range),
                cost = summary.totalCost
            )
        }
        val maxPoint = points.maxBy { it.cost }
        val minPoint = points.minBy { it.cost }
        return CostTrendStats(
            points = points,
            totalCost = points.sumOf { it.cost.toDouble() }.toFloat(),
            maxPoint = maxPoint,
            minPoint = minPoint
        )
    }

    fun bestProteinSourceOf(topFoods: List<FoodRankItem>): BestProteinSource? =
        topFoods.mapNotNull { item ->
            StatsAggregator.proteinPerYuan(item.totalProtein, item.totalCost)
                ?.let { item.foodName to it }
        }.maxByOrNull { it.second }
            ?.let { (name, perYuan) -> BestProteinSource(name, perYuan) }

    /** x 轴标签：月维度用「d 日」，周维度用「M/d」 */
    private fun dayLabelOf(date: String, range: StatsRange): String {
        val day = LocalDate.parse(date)
        return when (range) {
            StatsRange.MONTH -> "${day.dayOfMonth}日"
            StatsRange.WEEK -> "${day.monthValue}/${day.dayOfMonth}"
        }
    }
}
