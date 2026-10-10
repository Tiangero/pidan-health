package com.pidan.health.feature.record.viewmodel

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pidan.health.R
import com.pidan.health.core.common.time.DateProvider
import com.pidan.health.core.data.entity.DailySnapshot
import com.pidan.health.core.data.entity.UserPreferences
import com.pidan.health.core.data.model.DailySummary
import com.pidan.health.core.data.model.WeightTrendItem
import com.pidan.health.core.data.repository.DietRepository
import com.pidan.health.core.data.repository.SnapshotRepository
import com.pidan.health.core.data.repository.UserPreferencesRepository
import com.pidan.health.core.domain.NutritionCalculator
import com.pidan.health.core.domain.NutritionJudge
import com.pidan.health.core.domain.NutritionStatus
import com.pidan.health.core.domain.NutritionTargets
import com.pidan.health.core.domain.SnapshotManager
import com.pidan.health.core.domain.toNutritionFactors
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

/** 体重折线图显示范围 */
enum class WeightChartRange(val days: Int) {
    D7(7),
    D30(30)
}

/** 体重增减展示（区间首尾对比，着色由 UI 按 direction 处理） */
data class WeightDeltaUi(
    val text: String,
    /** -1 下降（绿） / 0 持平（灰） / 1 上升（红） */
    val direction: Int
)

/** 营养卡片单行展示数据（判定着色与进度全部在此算好，UI 零计算） */
data class NutrientRowUi(
    @StringRes val labelRes: Int,
    val intakeText: String,
    val targetText: String,
    val status: NutritionStatus,
    val progress: Float
)

data class RecordUiState(
    val isInitialLoading: Boolean = true,
    /** 顶栏日期展示，如「10月9日 周五」 */
    val todayLabel: String = "",
    /** 全新用户无任何快照 → 弹出体重引导 */
    val needsWeightSetup: Boolean = false,
    val todayWeight: Float? = null,
    val weights7: List<WeightTrendItem> = emptyList(),
    val weights30: List<WeightTrendItem> = emptyList(),
    val chartRange: WeightChartRange = WeightChartRange.D7,
    /** 当前区间的体重增减（区间不足 2 个点时为 null，UI 隐藏） */
    val weightDelta: WeightDeltaUi? = null,
    val nutrientRows: List<NutrientRowUi> = emptyList(),
    val todayCostText: String = "",
    val weekAvgCostText: String? = null
)

/**
 * 记录页（首页）ViewModel。
 * 数据源约束：只读 diet_record 聚合 + daily_snapshot，无模板 JOIN（红线）。
 * 所有营养计算/判定在 VM 层完成，Composable 只做展示（红线 2）。
 */
class RecordViewModel(
    private val snapshotRepository: SnapshotRepository,
    private val dietRepository: DietRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val snapshotManager: SnapshotManager,
    private val dateProvider: DateProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecordUiState())
    val uiState: StateFlow<RecordUiState> = _uiState.asStateFlow()

    /** 体重编辑弹窗的实时目标预览（已格式化的展示文本，与保存后首页同一计算链路，保证一致） */
    private val _weightPreview = MutableStateFlow<List<String>?>(null)
    val weightPreview: StateFlow<List<String>?> = _weightPreview.asStateFlow()

    private var dataJob: Job? = null
    private var cachedFactors = UserPreferences().toNutritionFactors()

    /** 每次回到前台调用：跨天后重取"今天"，重新订阅数据流 */
    fun onScreenResumed() {
        dataJob?.cancel()
        dataJob = viewModelScope.launch {
            // 快照生成时机 1：当天首次打开（历史冻结由 SnapshotManager 保证）
            val ensured = snapshotManager.ensureTodaySnapshot()
            _uiState.update { it.copy(needsWeightSetup = ensured == null && todayHasNoSnapshot()) }

            cachedFactors = userPreferencesRepository.getOrDefault().toNutritionFactors()
            val today = dateProvider.today()
            _uiState.update { it.copy(todayLabel = formatTodayLabel(today)) }
            val weekStart = dateProvider.daysAgo(6)

            combine(
                snapshotRepository.observeSnapshot(today),
                dietRepository.observeDailySummary(today)
            ) { snapshot, summary ->
                Triple(snapshot, summary, dietRepository.getAvgDailyCostOfLast7Days(weekStart))
            }.collect { (snapshot, summary, weekAvgCost) ->
                _uiState.update { current ->
                    val w7 = recentWeightsAsc(7)
                    val w30 = recentWeightsAsc(30)
                    current.copy(
                        isInitialLoading = false,
                        todayWeight = snapshot?.weight,
                        weights7 = w7,
                        weights30 = w30,
                        weightDelta = computeWeightDelta(
                            if (current.chartRange == WeightChartRange.D7) w7 else w30
                        ),
                        nutrientRows = buildNutrientRows(summary, snapshot),
                        todayCostText = formatCost(summary?.totalCost ?: 0f),
                        weekAvgCostText = weekAvgCost?.let { formatCost(it) }
                    )
                }
            }
        }
    }

    fun setChartRange(range: WeightChartRange) {
        _uiState.update {
            val weights = if (range == WeightChartRange.D7) it.weights7 else it.weights30
            it.copy(chartRange = range, weightDelta = computeWeightDelta(weights))
        }
    }

    /** 弹窗输入变化 → 实时预览新目标（无效输入返回 null 隐藏预览区） */
    fun onWeightDraftChanged(weightText: String) {
        val weight = weightText.toFloatOrNull()
        _weightPreview.value = weight?.takeIf { it.isValidWeight() }
            ?.let { formatPreviewTargets(NutritionCalculator.computeTargets(it, cachedFactors)) }
    }

    /**
     * 弹窗确认：写入体重并更新快照（快照生成时机 2），历史快照不动。
     * date 为 null 时写今天；补录历史日期时以该日期为主键写快照
     * （refreshWithWeightOn，用户显式补录动作）。未来日期非法，直接忽略。
     */
    fun confirmWeightInput(weightText: String, date: String? = null) {
        val weight = weightText.toFloatOrNull()?.takeIf { it.isValidWeight() } ?: return
        val targetDate = date ?: dateProvider.today()
        if (targetDate > dateProvider.today()) return
        viewModelScope.launch {
            if (targetDate == dateProvider.today()) {
                snapshotManager.refreshWithWeight(weight)
            } else {
                snapshotManager.refreshWithWeightOn(targetDate, weight)
            }
            _uiState.update { it.copy(needsWeightSetup = false) }
            _weightPreview.value = null
        }
    }

    fun dismissWeightDialog() {
        _uiState.update { it.copy(needsWeightSetup = false) }
        _weightPreview.value = null
    }

    // ---- 内部组装 ----

    private suspend fun todayHasNoSnapshot(): Boolean =
        snapshotRepository.getSnapshot(dateProvider.today()) == null

    /** 近 N 天体重（升序，折线图 x 轴为序号；缺失日期直接跳过连线） */
    private suspend fun recentWeightsAsc(days: Int): List<WeightTrendItem> =
        snapshotRepository.getRecentWeights().take(days).sortedBy { it.date }

    /** 区间体重增减：首尾对比，±0.05kg 视为持平；不足 2 点无增减 */
    private fun computeWeightDelta(weights: List<WeightTrendItem>): WeightDeltaUi? {
        if (weights.size < 2) return null
        val delta = weights.last().weight - weights.first().weight
        val abs = kotlin.math.abs(delta)
        val text = String.format(Locale.US, "%.1f kg", abs)
        return when {
            delta < -0.05f -> WeightDeltaUi(text = "↓ $text", direction = -1)
            delta > 0.05f -> WeightDeltaUi(text = "↑ $text", direction = 1)
            else -> WeightDeltaUi(text = "→ $text", direction = 0)
        }
    }

    private fun buildNutrientRows(summary: DailySummary?, snapshot: DailySnapshot?): List<NutrientRowUi> {
        if (snapshot == null) return emptyList()
        val intake = summary ?: DailySummary()
        return listOf(
            fixedValueRow(
                R.string.nutrient_carbs,
                intake.totalCarbs,
                snapshot.targetCarbs
            ),
            rangeRow(
                R.string.nutrient_protein,
                intake.totalProtein,
                snapshot.targetProteinMin,
                snapshot.targetProteinMax
            ),
            fixedValueRow(
                R.string.nutrient_fat,
                intake.totalFat,
                snapshot.targetFat
            ),
            rangeRow(
                R.string.nutrient_fiber,
                intake.totalFiber,
                snapshot.targetFiberMin,
                snapshot.targetFiberMax
            ),
            upperLimitRow(
                R.string.nutrient_salt,
                intake.totalSalt,
                snapshot.targetSaltMax
            )
        )
    }

    /** 碳水/脂肪：固定值目标 ±5g 绝对容差 */
    private fun fixedValueRow(@StringRes labelRes: Int, intake: Float, target: Float): NutrientRowUi {
        val status = NutritionJudge.judgeFixedValue(intake, target)
        return NutrientRowUi(
            labelRes = labelRes,
            intakeText = formatGrams(intake),
            targetText = formatGrams(target),
            status = status,
            progress = safeProgress(intake, target)
        )
    }

    /** 蛋白/纤维：区间目标 */
    private fun rangeRow(@StringRes labelRes: Int, intake: Float, min: Float, max: Float): NutrientRowUi {
        val status = NutritionJudge.judgeRange(intake, min, max)
        return NutrientRowUi(
            labelRes = labelRes,
            intakeText = formatGrams(intake),
            targetText = "${formatGrams(min).removeSuffix("g")}-${formatGrams(max)}",
            status = status,
            progress = safeProgress(intake, max)
        )
    }

    /** 盐：只判上限（摄入为已换算的盐 g） */
    private fun upperLimitRow(@StringRes labelRes: Int, intake: Float, max: Float): NutrientRowUi {
        val status = NutritionJudge.judgeUpperLimit(intake, max)
        return NutrientRowUi(
            labelRes = labelRes,
            intakeText = formatGrams(intake),
            targetText = "≤${formatGrams(max)}",
            status = status,
            progress = safeProgress(intake, max)
        )
    }

    private fun safeProgress(intake: Float, target: Float): Float =
        if (target <= 0f) 0f else (intake / target).coerceIn(0f, 1f)

    /** 弹窗预览：新体重对应的五大营养目标（单行文本） */
    private fun formatPreviewTargets(t: NutritionTargets): List<String> = listOf(
        "碳水 ${formatGrams(t.carbsGrams)}",
        "蛋白 ${formatGrams(t.proteinMinGrams).removeSuffix("g")}-${formatGrams(t.proteinMaxGrams)}",
        "脂肪 ${formatGrams(t.fatGrams)}",
        "纤维 ${formatGrams(t.fiberMinGrams).removeSuffix("g")}-${formatGrams(t.fiberMaxGrams)}",
        "盐 ≤${formatGrams(t.saltMaxGrams)}"
    )

    private fun formatGrams(value: Float): String =
        if (value == value.toInt().toFloat()) "${value.toInt()}g" else String.format(Locale.US, "%.1fg", value)

    /** 「2026-10-09」→「10月9日 周五」（顶栏日期展示） */
    private fun formatTodayLabel(isoDate: String): String {
        val date = java.time.LocalDate.parse(isoDate)
        val week = when (date.dayOfWeek) {
            java.time.DayOfWeek.MONDAY -> "周一"
            java.time.DayOfWeek.TUESDAY -> "周二"
            java.time.DayOfWeek.WEDNESDAY -> "周三"
            java.time.DayOfWeek.THURSDAY -> "周四"
            java.time.DayOfWeek.FRIDAY -> "周五"
            java.time.DayOfWeek.SATURDAY -> "周六"
            else -> "周日"
        }
        return "${date.monthValue}月${date.dayOfMonth}日 $week"
    }

    private fun formatCost(value: Float): String = String.format(Locale.US, "¥%.2f", value)

    private fun Float.isValidWeight(): Boolean = this > 0f && this < 500f
}
