package com.pidan.health.feature.settings.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pidan.health.core.data.backup.CloudSyncManager
import com.pidan.health.core.data.storage.CloudSyncStore
import com.pidan.health.core.data.storage.WebDavDefaults
import com.pidan.health.core.data.webdav.WebDavConfig
import com.pidan.health.core.data.webdav.WebDavFile
import com.pidan.health.core.data.webdav.WebDavLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 云端备份列表行（格式化好的展示文本，UI 零计算） */
data class CloudBackupItemUi(
    val fileName: String,
    val sizeText: String,
    val timeText: String
)

data class CloudSyncUiState(
    val isLoaded: Boolean = false,
    /** 表单字段仅在首次加载时回填，之后由用户编辑，避免流刷新冲掉输入 */
    val serverUrlText: String = WebDavDefaults.DEFAULT_SERVER_URL,
    val usernameText: String = "",
    val passwordText: String = "",
    val isConfigured: Boolean = false,
    val autoBackupEnabled: Boolean = true,
    val wifiOnly: Boolean = false,
    val isBusy: Boolean = false,
    val lastSyncText: String? = null,
    /** null=列表未展开；空列表=云端无备份 */
    val backups: List<CloudBackupItemUi>? = null,
    /** 待恢复的云端文件名（防呆确认弹窗） */
    val pendingRestoreFile: String? = null,
    /** 卡内状态/错误提示（内联展示，随操作刷新） */
    val message: String? = null
)

/**
 * 云端同步 ViewModel（T13）：配置管理 + 立即备份 + 云端列表恢复 + 自动备份开关。
 * 与 SettingsViewModel 分离（同页不同职责，遵守单 VM 单一职责红线）。
 */
class CloudSyncViewModel(
    private val cloudSyncManager: CloudSyncManager,
    private val cloudSyncStore: CloudSyncStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(CloudSyncUiState())
    val uiState: StateFlow<CloudSyncUiState> = _uiState.asStateFlow()

    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)

    init {
        viewModelScope.launch {
            cloudSyncStore.settingsFlow.collect { settings ->
                _uiState.update { current ->
                    current.copy(
                        isLoaded = true,
                        // 表单只回填一次（首次）；之后保留用户正在编辑的内容
                        serverUrlText = if (current.isLoaded) current.serverUrlText
                        else settings.config?.baseUrl ?: settings.let { WebDavDefaults.DEFAULT_SERVER_URL },
                        usernameText = if (current.isLoaded) current.usernameText
                        else settings.config?.username ?: "",
                        passwordText = if (current.isLoaded) current.passwordText
                        else settings.config?.password ?: "",
                        isConfigured = settings.config != null,
                        autoBackupEnabled = settings.autoBackupEnabled,
                        wifiOnly = settings.wifiOnly,
                        lastSyncText = settings.lastSyncAtMillis?.let { millis ->
                            val result = settings.lastSyncError?.let { "失败：$it" } ?: "成功"
                            "${timeFormat.format(Date(millis))} · $result"
                        }
                    )
                }
            }
        }
    }

    // ---- 表单 ----

    fun onServerUrlChange(text: String) = _uiState.update { it.copy(serverUrlText = text) }
    fun onUsernameChange(text: String) = _uiState.update { it.copy(usernameText = text) }
    fun onPasswordChange(text: String) = _uiState.update { it.copy(passwordText = text) }

    /** 测试连接（读+写双重验证），通过后保存配置 */
    fun testAndSaveConfig() {
        val state = _uiState.value
        WebDavLog.d("测试连接: 点击 (isBusy=${state.isBusy})")
        if (state.isBusy) return
        val username = state.usernameText.trim()
        val password = state.passwordText.trim()
        val serverUrl = state.serverUrlText.trim()
        if (username.isEmpty() || password.isEmpty()) {
            showMessage("请填写账号与应用密码")
            return
        }
        setBusy(true)
        viewModelScope.launch {
            try {
                val config = WebDavConfig(baseUrl = serverUrl, username = username, password = password)
                cloudSyncManager.testConnection(config)
                    .onSuccess {
                        cloudSyncStore.saveConfig(config)
                        WebDavLog.d("测试连接: 成功，配置已保存")
                        showMessage("连接成功，配置已保存")
                    }
                    .onFailure { showMessage(it.message ?: "连接失败，请检查配置") }
            } catch (e: Throwable) {
                WebDavLog.d("测试连接: 未捕获异常 ${e.javaClass.simpleName}: ${e.message}")
                showMessage("连接失败：${e.message ?: "未知错误"}")
            } finally {
                setBusy(false)
            }
        }
    }

    // ---- 备份与恢复 ----

    /** 立即备份到云端（需已保存配置） */
    fun backupNow() {
        val state = _uiState.value
        WebDavLog.d("立即备份: 点击 (isBusy=${state.isBusy}, isConfigured=${state.isConfigured})")
        if (state.isBusy || !state.isConfigured) {
            if (!state.isConfigured) showMessage("请先填写账号与应用密码并测试连接")
            return
        }
        setBusy(true)
        viewModelScope.launch {
            try {
                cloudSyncManager.backupNow()
                    .onSuccess {
                        WebDavLog.d("立即备份: 完成 $it")
                        showMessage("已上传：$it")
                    }
                    .onFailure { showMessage(it.message ?: "备份失败，请稍后重试") }
            } catch (e: Throwable) {
                WebDavLog.d("立即备份: 未捕获异常 ${e.javaClass.simpleName}: ${e.message}")
                showMessage("备份失败：${e.message ?: "未知错误"}")
            } finally {
                setBusy(false)
            }
        }
    }

    /** 展开云端备份列表（新→旧） */
    fun openBackups() {
        val state = _uiState.value
        if (state.isBusy || !state.isConfigured) {
            if (!state.isConfigured) showMessage("请先填写账号与应用密码并测试连接")
            return
        }
        setBusy(true)
        viewModelScope.launch {
            cloudSyncManager.listBackups()
                .onSuccess { files ->
                    _uiState.update { it.copy(backups = files.map(::toItemUi)) }
                    if (files.isEmpty()) showMessage("云端暂无备份文件")
                }
                .onFailure { showMessage(it.message ?: "获取云端列表失败") }
            setBusy(false)
        }
    }

    fun dismissBackups() = _uiState.update { it.copy(backups = null) }

    fun onRestoreClick(fileName: String) =
        _uiState.update { it.copy(pendingRestoreFile = fileName) }

    fun dismissRestoreConfirm() =
        _uiState.update { it.copy(pendingRestoreFile = null) }

    /** 确认恢复：下载云端 zip → 覆盖导入（失败回滚，现有数据不受影响） */
    fun confirmRestore() {
        val file = _uiState.value.pendingRestoreFile
        if (file == null || _uiState.value.isBusy) {
            dismissRestoreConfirm()
            return
        }
        setBusy(true)
        viewModelScope.launch {
            cloudSyncManager.restore(file)
                .onSuccess { summary ->
                    showMessage("恢复完成：食物 ${summary.foodCount}、饮食 ${summary.dietCount}、" +
                        "快照 ${summary.snapshotCount}、运动 ${summary.exerciseCount} 条")
                }
                .onFailure { showMessage("恢复失败，现有数据未受影响：${it.message ?: "未知错误"}") }
            _uiState.update { it.copy(pendingRestoreFile = null) }
            setBusy(false)
        }
    }

    // ---- 开关 ----

    fun setAutoBackupEnabled(enabled: Boolean) {
        _uiState.update { it.copy(autoBackupEnabled = enabled) }
        viewModelScope.launch { cloudSyncStore.setAutoBackupEnabled(enabled) }
    }

    fun setWifiOnly(enabled: Boolean) {
        _uiState.update { it.copy(wifiOnly = enabled) }
        viewModelScope.launch { cloudSyncStore.setWifiOnly(enabled) }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    // ---- 内部细节 ----

    private fun toItemUi(file: WebDavFile): CloudBackupItemUi {
        val sizeText = when {
            file.sizeBytes >= 1024 * 1024 ->
                String.format(Locale.US, "%.1f MB", file.sizeBytes / 1024f / 1024f)
            else -> String.format(Locale.US, "%.1f KB", file.sizeBytes / 1024f)
        }
        val timeText = if (file.lastModifiedMillis > 0) {
            timeFormat.format(Date(file.lastModifiedMillis))
        } else {
            "-"
        }
        return CloudBackupItemUi(fileName = file.name, sizeText = sizeText, timeText = timeText)
    }

    private fun setBusy(busy: Boolean) = _uiState.update { it.copy(isBusy = busy) }

    private fun showMessage(text: String) = _uiState.update { it.copy(message = text) }
}
