package com.pidan.health.feature.food.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pidan.health.core.data.entity.FoodTemplate
import com.pidan.health.core.data.repository.FoodRepository
import com.pidan.health.core.data.storage.FoodImageStorage
import com.pidan.health.core.domain.DietIntakeCalculator
import com.pidan.health.core.domain.FoodUnit
import com.pidan.health.core.domain.SaltConverter
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 食物详情页（只读，2026-10-10 与编辑页拆分）单页状态。
 * 全部数值直接取库内口径（每 100g / 盐 g / 总量 g），格式化在 VM 完成后供 UI 展示。
 */
data class FoodDetailUiState(
    val isLoading: Boolean = true,
    /** 模板不存在（被删除）→ UI 收到后立即退出 */
    val isMissing: Boolean = false,
    val name: String = "",
    val coverFile: File? = null,
    /** 0=按克 1=按份（FoodUnit） */
    val unitType: Int = FoodUnit.BY_GRAM,
    val weightPerServingText: String? = null,
    // ---- 营养成分（每 100g 口径）----
    val carbsText: String = "",
    val proteinText: String = "",
    val fatText: String = "",
    val fiberText: String = "",
    /** 库内存盐 g，详情页同时给盐 g 与钠 mg（换算唯一入口 SaltConverter） */
    val saltText: String = "",
    val sodiumMgText: String? = null,
    // ---- 价格信息 ----
    val totalPriceText: String? = null,
    val totalAmountText: String? = null,
    /** 单价（元/100g），金额/总量不成对时为 null 隐藏 */
    val unitPriceText: String? = null
)

/**
 * 食物详情页 ViewModel：只读展示模板，与 FoodEditViewModel（编辑）彻底分离。
 * 响应式观察单条模板：编辑页保存后返回详情页自动刷新；模板被删则通知 UI 退出。
 */
class FoodDetailViewModel(
    private val foodRepository: FoodRepository,
    private val foodImageStorage: FoodImageStorage,
    savedStateHandle: SavedStateHandle,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val foodId: Long =
        savedStateHandle.get<Long>(FoodEditViewModel.NAV_KEY_FOOD_ID) ?: FoodEditViewModel.NEW_FOOD_ID

    private val _uiState = MutableStateFlow(FoodDetailUiState())
    val uiState: StateFlow<FoodDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(ioDispatcher) {
            foodRepository.observeById(foodId).collect { template ->
                if (template == null) {
                    _uiState.update { it.copy(isLoading = false, isMissing = true) }
                } else {
                    _uiState.update { it.copy(isLoading = false, isMissing = false).fillFrom(template) }
                }
            }
        }
    }

    private fun FoodDetailUiState.fillFrom(template: FoodTemplate): FoodDetailUiState {
        val unitPrice = DietIntakeCalculator.unitPricePer100Grams(
            totalPrice = template.totalPrice,
            totalAmountGrams = template.totalAmount
        )
        return copy(
            name = template.name,
            coverFile = foodImageStorage.resolve(template.coverImagePath),
            unitType = template.unitType,
            weightPerServingText = template.weightPerServing?.let(FoodEditViewModel::formatNumber),
            carbsText = FoodEditViewModel.formatNumber(template.carbsPer100g),
            proteinText = FoodEditViewModel.formatNumber(template.proteinPer100g),
            fatText = FoodEditViewModel.formatNumber(template.fatPer100g),
            fiberText = FoodEditViewModel.formatNumber(template.fiberPer100g),
            saltText = FoodEditViewModel.formatNumber(template.saltPer100g),
            sodiumMgText = SaltConverter
                .saltGramsToSodiumMg(template.saltPer100g)
                .let(FoodEditViewModel::formatNumber),
            totalPriceText = template.totalPrice?.let { "¥" + FoodEditViewModel.formatNumber(it) },
            totalAmountText = template.totalAmount?.let { FoodEditViewModel.formatNumber(it) + "g" },
            unitPriceText = unitPrice?.let {
                "¥" + FoodEditViewModel.formatNumber(it) + " / 100g"
            }
        )
    }
}
