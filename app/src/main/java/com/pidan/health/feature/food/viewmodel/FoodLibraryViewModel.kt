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
    val entryTarget: FoodItemUi? = null,
    /** 管理模式：多选 / 批量删除，隐藏快捷录入「+」与「新建食物」 */
    val isManageMode: Boolean = false,
    /** 管理模式下勾选的模板 id 集合 */
    val selectedIds: Set<Long> = emptySet(),
    /** 批量删除防呆确认弹窗可见 */
    val isDeleteConfirmVisible: Boolean = false
)

/**
 * 食物库 ViewModel：列表观察 + 快捷录入（场景 B 固化计算落库）+ 管理模式（多选 / 批量删除，T7）。
 * 录入只写 diet_record；首页卡片因 observeDailySummary 响应式订阅而即时刷新。
 * 删除只动 food_template 与封面文件，历史 diet_record 依赖固化快照不受影响。
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

    /** 批量删除成功提示（删除数量），消费后置空 */
    private val _deleteSuccessCount = MutableStateFlow<Int?>(null)
    val deleteSuccessCount: StateFlow<Int?> = _deleteSuccessCount.asStateFlow()

    /** 模板缓存：批量删除时用于清理沙盒封面文件（FoodItemUi 不携带相对路径） */
    private var templatesById: Map<Long, FoodTemplate> = emptyMap()

    init {
        viewModelScope.launch {
            foodRepository.observeAll().collect { templates ->
                templatesById = templates.associateBy { it.id }
                _uiState.update {
                    it.copy(
                        isInitialLoading = false,
                        items = templates.map(::toItemUi),
                        // 已被删除的模板自动移出勾选集，避免幽灵选中
                        selectedIds = it.selectedIds intersect templates.mapTo(mutableSetOf()) { t -> t.id }
                    )
                }
            }
        }
    }

    fun startQuickEntry(item: FoodItemUi) {
        // 管理模式禁止快捷录入（UI 已隐藏「+」，此处兜底）
        if (_uiState.value.isManageMode) return
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

    fun consumeDeleteSuccessCount() {
        _deleteSuccessCount.value = null
    }

    // ---- 管理模式（T7：多选 / 批量删除）----

    fun enterManageMode() {
        _uiState.update { it.copy(isManageMode = true, selectedIds = emptySet()) }
    }

    fun exitManageMode() {
        _uiState.update {
            it.copy(
                isManageMode = false,
                selectedIds = emptySet(),
                isDeleteConfirmVisible = false
            )
        }
    }

    /** 勾选 / 取消勾选单个模板 */
    fun toggleSelected(id: Long) {
        _uiState.update {
            if (!it.isManageMode) return@update it
            it.copy(
                selectedIds = if (id in it.selectedIds) it.selectedIds - id else it.selectedIds + id
            )
        }
    }

    /** 全选 / 取消全选（当前列表已全选时切为取消） */
    fun toggleSelectAll() {
        _uiState.update {
            if (!it.isManageMode) return@update it
            val allIds = it.items.map { item -> item.id }.toSet()
            if (allIds.isNotEmpty() && it.selectedIds.containsAll(allIds)) {
                it.copy(selectedIds = emptySet())
            } else {
                it.copy(selectedIds = allIds)
            }
        }
    }

    /** 点「删除」：先弹防呆确认，不直接删 */
    fun requestDeleteSelected() {
        _uiState.update {
            if (it.selectedIds.isEmpty()) it else it.copy(isDeleteConfirmVisible = true)
        }
    }

    fun dismissDeleteConfirm() {
        _uiState.update { it.copy(isDeleteConfirmVisible = false) }
    }

    /**
     * 确认批量删除：删模板 + 尽力清理沙盒封面文件。
     * 历史冻结红线：diet_record 无外键且录入时已固化快照，删模板不影响任何历史数据（T2 单测兜底）。
     */
    fun confirmDeleteSelected() {
        val ids = _uiState.value.selectedIds.toList()
        if (ids.isEmpty()) {
            dismissDeleteConfirm()
            return
        }
        viewModelScope.launch {
            ids.forEach { id ->
                foodImageStorage.delete(templatesById[id]?.coverImagePath)
            }
            foodRepository.deleteByIds(ids)
            dismissDeleteConfirm()
            exitManageMode()
            _deleteSuccessCount.value = ids.size
        }
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
