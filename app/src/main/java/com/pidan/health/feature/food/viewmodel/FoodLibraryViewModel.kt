package com.pidan.health.feature.food.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pidan.health.core.common.time.DateProvider
import com.pidan.health.core.data.entity.FoodTemplate
import com.pidan.health.core.data.repository.DietRepository
import com.pidan.health.core.data.repository.FoodRepository
import com.pidan.health.core.data.storage.FoodImageStorage
import com.pidan.health.core.domain.DietIntakeCalculator
import com.pidan.health.core.domain.FoodUnit
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 食物库列表单行展示数据（封面文件已解析、单位标识文案已拼好，UI 零逻辑） */
data class FoodItemUi(
    val id: Long,
    val name: String,
    /** 沙盒内封面文件；null 显示首字占位色块 */
    val coverFile: File?,
    /** 单位标识，如「按份 · 150g/份」「按克」 */
    val unitLabel: String,
    val unitType: Int,
    val weightPerServing: Float?
)

data class FoodLibraryUiState(
    val isInitialLoading: Boolean = true,
    val items: List<FoodItemUi> = emptyList(),
    /** 快捷录入目标，非空时显示数量输入弹窗 */
    val entryTarget: FoodItemUi? = null
)

/**
 * 食物库 ViewModel：列表观察 + 快捷录入（场景 B 固化计算落库）。
 * 录入只写 diet_record；首页卡片因 observeDailySummary 响应式订阅而即时刷新。
 */
class FoodLibraryViewModel(
    private val foodRepository: FoodRepository,
    private val dietRepository: DietRepository,
    private val foodImageStorage: FoodImageStorage,
    private val dateProvider: DateProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(FoodLibraryUiState())
    val uiState: StateFlow<FoodLibraryUiState> = _uiState.asStateFlow()

    /** 录入成功提示（食物名），消费后置空 */
    private val _entrySuccessMessage = MutableStateFlow<String?>(null)
    val entrySuccessMessage: StateFlow<String?> = _entrySuccessMessage.asStateFlow()

    init {
        viewModelScope.launch {
            foodRepository.observeAll().collect { templates ->
                _uiState.update {
                    it.copy(
                        isInitialLoading = false,
                        items = templates.map(::toItemUi)
                    )
                }
            }
        }
    }

    fun startQuickEntry(item: FoodItemUi) {
        _uiState.update { it.copy(entryTarget = item) }
    }

    fun dismissQuickEntry() {
        _uiState.update { it.copy(entryTarget = null) }
    }

    /** 弹窗确认：固化计算 → 写入当天 diet_record → 关弹窗 + 提示 */
    fun confirmQuickEntry(amountText: String) {
        val target = _uiState.value.entryTarget ?: return
        val amount = amountText.toFloatOrNull()?.takeIf { it > 0f } ?: return
        viewModelScope.launch {
            val template = foodRepository.getById(target.id) ?: run {
                dismissQuickEntry()
                return@launch
            }
            val record = DietIntakeCalculator.buildDietRecord(
                template,
                amount,
                date = dateProvider.today()
            )
            if (record == null) {
                // 数量非法或模板缺单份克重：不落库，关弹窗（表单侧已有校验兜底）
                dismissQuickEntry()
                return@launch
            }
            dietRepository.insert(record)
            _entrySuccessMessage.value = template.name
            dismissQuickEntry()
        }
    }

    fun consumeEntrySuccessMessage() {
        _entrySuccessMessage.value = null
    }

    // ---- 内部组装 ----

    private fun toItemUi(template: FoodTemplate): FoodItemUi = FoodItemUi(
        id = template.id,
        name = template.name,
        coverFile = foodImageStorage.resolve(template.coverImagePath),
        unitLabel = when (template.unitType) {
            FoodUnit.BY_SERVING -> {
                val perServing = template.weightPerServing?.takeIf { it > 0f }
                if (perServing != null) "按份 · ${formatGrams(perServing)}/份" else "按份"
            }
            else -> "按克"
        },
        unitType = template.unitType,
        weightPerServing = template.weightPerServing
    )

    private fun formatGrams(value: Float): String =
        if (value == value.toInt().toFloat()) "${value.toInt()}g" else "${value}g"
}
