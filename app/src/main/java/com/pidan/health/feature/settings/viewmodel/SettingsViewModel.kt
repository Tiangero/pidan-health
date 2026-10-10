package com.pidan.health.feature.settings.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pidan.health.core.data.backup.BackupManager
import com.pidan.health.core.data.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val isLoading: Boolean = true,
    // 自定义营养系数表单（文本框绑定，保存时统一校验）
    val carbsFactorText: String = "",
    val proteinMinText: String = "",
    val proteinMaxText: String = "",
    val fatFactorText: String = "",
    val isFiberCustom: Boolean = false,
    val fiberMinText: String = "",
    val fiberMaxText: String = "",
    val isSaltCustom: Boolean = false,
    val saltMaxText: String = "",
    /** 导出/导入进行中 */
    val isBusy: Boolean = false,
    /** 导入确认弹窗（防呆：导入将覆盖当前数据） */
    val isImportConfirmVisible: Boolean = false,
    /** Snackbar 消息（消费即清空） */
    val message: String? = null
)

/**
 * 设置页 ViewModel：自定义营养系数配置 + 备份导出/导入。
 * 导入选中 zip 后先弹防呆确认，用户确认才执行覆盖导入（取消不做任何写操作）。
 */
class SettingsViewModel(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val backupManager: BackupManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    /** 待导入的 zip（用户在文件选择器选中后暂存，确认弹窗通过后使用） */
    private var pendingImportUri: Uri? = null

    init {
        viewModelScope.launch {
            val prefs = userPreferencesRepository.getOrDefault()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    carbsFactorText = prefs.carbsFactor.toString(),
                    proteinMinText = prefs.proteinMinFactor.toString(),
                    proteinMaxText = prefs.proteinMaxFactor.toString(),
                    fatFactorText = prefs.fatFactor.toString(),
                    isFiberCustom = prefs.isFiberCustom,
                    fiberMinText = prefs.fiberMin.toString(),
                    fiberMaxText = prefs.fiberMax.toString(),
                    isSaltCustom = prefs.isSaltCustom,
                    saltMaxText = prefs.saltMax.toString()
                )
            }
        }
    }

    // ---- 系数表单 ----

    fun onCarbsFactorChange(text: String) = _uiState.update { it.copy(carbsFactorText = text) }
    fun onProteinMinChange(text: String) = _uiState.update { it.copy(proteinMinText = text) }
    fun onProteinMaxChange(text: String) = _uiState.update { it.copy(proteinMaxText = text) }
    fun onFatFactorChange(text: String) = _uiState.update { it.copy(fatFactorText = text) }
    fun onFiberCustomChange(enabled: Boolean) =
        _uiState.update { it.copy(isFiberCustom = enabled) }
    fun onFiberMinChange(text: String) = _uiState.update { it.copy(fiberMinText = text) }
    fun onFiberMaxChange(text: String) = _uiState.update { it.copy(fiberMaxText = text) }
    fun onSaltCustomChange(enabled: Boolean) = _uiState.update { it.copy(isSaltCustom = enabled) }
    fun onSaltMaxChange(text: String) = _uiState.update { it.copy(saltMaxText = text) }

    fun saveFactors() {
        val state = _uiState.value
        val carbs = state.carbsFactorText.toFloatOrNull()
        val proteinMin = state.proteinMinText.toFloatOrNull()
        val proteinMax = state.proteinMaxText.toFloatOrNull()
        val fat = state.fatFactorText.toFloatOrNull()
        val fiberMin = state.fiberMinText.toFloatOrNull()
        val fiberMax = state.fiberMaxText.toFloatOrNull()
        val saltMax = state.saltMaxText.toFloatOrNull()

        val valid = carbs != null && carbs > 0f &&
            proteinMin != null && proteinMin > 0f &&
            proteinMax != null && proteinMax >= proteinMin &&
            fat != null && fat > 0f &&
            (!state.isFiberCustom || (
                fiberMin != null && fiberMin > 0f &&
                    fiberMax != null && fiberMax >= fiberMin
                )) &&
            (!state.isSaltCustom || (saltMax != null && saltMax > 0f))
        if (!valid) {
            showMessage("系数填写有误，请检查后保存")
            return
        }

        viewModelScope.launch {
            userPreferencesRepository.save(
                com.pidan.health.core.data.entity.UserPreferences(
                    carbsFactor = carbs,
                    proteinMinFactor = proteinMin,
                    proteinMaxFactor = proteinMax,
                    fatFactor = fat,
                    isFiberCustom = state.isFiberCustom,
                    fiberMin = fiberMin ?: 25f,
                    fiberMax = fiberMax ?: 30f,
                    isSaltCustom = state.isSaltCustom,
                    saltMax = saltMax ?: 5f
                )
            )
            showMessage("已保存，自今日起的营养目标按新系数计算")
        }
    }

    // ---- 备份导出/导入 ----

    fun export() {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            backupManager.export()
                .onSuccess { path -> showMessage("已导出：$path") }
                .onFailure { showMessage("导出失败：${it.message ?: "未知错误"}") }
            _uiState.update { it.copy(isBusy = false) }
        }
    }

    /** 文件选择器返回 zip：暂存 uri 并弹防呆确认弹窗 */
    fun onZipPicked(uri: Uri) {
        pendingImportUri = uri
        _uiState.update { it.copy(isImportConfirmVisible = true) }
    }

    fun dismissImportConfirm() {
        pendingImportUri = null
        _uiState.update { it.copy(isImportConfirmVisible = false) }
    }

    /** 确认导入：执行覆盖导入（事务保证失败不破坏现有数据） */
    fun confirmImport() {
        val uri = pendingImportUri
        if (uri == null || _uiState.value.isBusy) {
            dismissImportConfirm()
            return
        }
        _uiState.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            backupManager.import(uri)
                .onSuccess { summary ->
                    showMessage(
                        "导入完成：食物 ${summary.foodCount}、饮食 ${summary.dietCount}、" +
                            "快照 ${summary.snapshotCount}、运动 ${summary.exerciseCount} 条"
                    )
                }
                .onFailure { showMessage("导入失败，现有数据未受影响：${it.message ?: "文件无效"}") }
            _uiState.update { it.copy(isBusy = false, isImportConfirmVisible = false) }
            pendingImportUri = null
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    /** 旧版本（API < 29）存储权限被拒：给出提示，不执行导出 */
    fun onLegacyStorageDenied() {
        showMessage("未授予存储权限，无法保存到下载目录")
    }

    private fun showMessage(text: String) = _uiState.update { it.copy(message = text) }
}
