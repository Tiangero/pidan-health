package com.pidan.health.feature.exercise.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pidan.health.core.common.time.DateProvider
import com.pidan.health.core.data.entity.ExerciseRecord
import com.pidan.health.core.data.repository.ExerciseRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * 运动状态（与 ExerciseRecord.type 对应）：休息为默认态，库中只存真实运动，
 * 显式切回「休息」即删除该日记录（无记录 = 休息）。
 */
enum class ExerciseType(val value: Int) {
    REST(0),
    AEROBIC(1),
    ANAEROBIC(2);

    companion object {
        fun fromValue(value: Int): ExerciseType = entries.firstOrNull { it.value == value } ?: REST
    }
}

/** 当月概览：三类状态的天数统计 */
data class ExerciseMonthOverview(
    val aerobicDays: Int,
    val anaerobicDays: Int,
    val restDays: Int
)

data class ExerciseUiState(
    val isLoading: Boolean = true,
    /** 当前展示的月份 */
    val displayedMonth: YearMonth = YearMonth.now(),
    /** 顶栏月份文案，如「2026 年 10 月」 */
    val monthLabel: String = "",
    /** 月内有运动记录的日期 → 状态（无记录日期 = 休息） */
    val statusByDate: Map<String, ExerciseType> = emptyMap(),
    val overview: ExerciseMonthOverview = ExerciseMonthOverview(0, 0, 0),
    /** 状态选择弹层当前操作的日期（null = 弹层关闭） */
    val pickerDate: LocalDate? = null
)

/**
 * 运动页（月历打卡）ViewModel。
 * 数据：按展示月份区间订阅 exercise_record 响应式流；打卡/补签按主键 date 覆盖。
 * 未来日期不可打卡（UI 禁用 + VM 双重拦截），补签历史日期即时生效。
 */
class ExerciseViewModel(
    private val exerciseRepository: ExerciseRepository,
    private val dateProvider: DateProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExerciseUiState())
    val uiState: StateFlow<ExerciseUiState> = _uiState.asStateFlow()

    /** 「今天」（月历高亮 + 未来日期拦截），由 DateProvider 提供 */
    val today: LocalDate = LocalDate.parse(dateProvider.today())

    private var observeJob: Job? = null

    init {
        selectMonth(YearMonth.from(today))
    }

    /** 切换月份（左右按钮），重订该月区间的数据流 */
    fun selectMonth(month: YearMonth) {
        _uiState.update {
            it.copy(
                isLoading = true,
                displayedMonth = month,
                monthLabel = formatMonthLabel(month)
            )
        }
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            val first = month.atDay(1).format(DateTimeFormatter.ISO_LOCAL_DATE)
            val last = month.atEndOfMonth().format(DateTimeFormatter.ISO_LOCAL_DATE)
            exerciseRepository.observeRange(first, last).collect { records ->
                _uiState.update { it.copy(isLoading = false, statusByDate = toStatusMap(records)) }
                _uiState.update { current -> current.copy(overview = buildOverview(current)) }
            }
        }
    }

    /** 点击日期：打开状态选择弹层（未来日期拦截，不产生脏数据） */
    fun onDayClick(date: LocalDate) {
        if (date.isAfter(today)) return
        _uiState.update { it.copy(pickerDate = date) }
    }

    fun dismissPicker() {
        _uiState.update { it.copy(pickerDate = null) }
    }

    /** 弹层选定状态：有氧/无氧写入，休息删除记录（无记录 = 休息），弹层关闭。未来日期双重拦截 */
    fun onStatusPicked(date: LocalDate, type: ExerciseType) {
        if (date.isAfter(today)) return
        viewModelScope.launch {
            when (type) {
                ExerciseType.REST -> exerciseRepository.deleteByDate(date.toString())
                else -> exerciseRepository.upsert(
                    ExerciseRecord(date = date.toString(), type = type.value)
                )
            }
        }
        dismissPicker()
    }

    // ---- 内部组装 ----

    private fun toStatusMap(records: List<ExerciseRecord>): Map<String, ExerciseType> =
        records.associate { it.date to ExerciseType.fromValue(it.type) }

    /** 概览统计：休息 = 已过天数 − 有氧 − 无氧（未来月份已过天数为 0） */
    private fun buildOverview(state: ExerciseUiState): ExerciseMonthOverview {
        val aerobic = state.statusByDate.values.count { it == ExerciseType.AEROBIC }
        val anaerobic = state.statusByDate.values.count { it == ExerciseType.ANAEROBIC }
        val elapsed = elapsedDaysOf(state.displayedMonth)
        return ExerciseMonthOverview(
            aerobicDays = aerobic,
            anaerobicDays = anaerobic,
            restDays = (elapsed - aerobic - anaerobic).coerceAtLeast(0)
        )
    }

    private fun elapsedDaysOf(month: YearMonth): Int = when {
        month.isBefore(YearMonth.from(today)) -> month.lengthOfMonth()
        month == YearMonth.from(today) -> today.dayOfMonth
        else -> 0
    }

    /** 「2026-10」→「2026 年 10 月」（顶栏月份展示） */
    private fun formatMonthLabel(month: YearMonth): String = "${month.year} 年 ${month.monthValue} 月"
}
