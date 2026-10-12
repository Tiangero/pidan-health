package com.pidan.health.core.data.backup

import com.pidan.health.core.common.time.DateProvider
import com.pidan.health.core.data.network.NetworkChecker
import com.pidan.health.core.data.storage.CloudSyncStore
import com.pidan.health.core.data.webdav.WebDavClient
import com.pidan.health.core.data.webdav.WebDavConfig
import com.pidan.health.core.data.webdav.WebDavFile
import com.pidan.health.core.data.webdav.WebDavLog

/** 自动备份结果（RecordViewModel 触发后仅记日志/状态，不打扰用户） */
sealed interface AutoBackupOutcome {
    data object Disabled : AutoBackupOutcome
    data object NotConfigured : AutoBackupOutcome
    data object AlreadyDoneToday : AutoBackupOutcome
    data object NetworkUnavailable : AutoBackupOutcome
    data class Success(val fileName: String) : AutoBackupOutcome
    data class Failure(val message: String) : AutoBackupOutcome
}

/**
 * 云同步管理（T13 方案 A：通用 WebDAV，坚果云默认预设）。
 *
 * 设计约定：
 * - 文件级同步（zip 整包），冲突策略 = 备份时间戳最新者为准，恢复前必须用户确认（覆盖导入）；
 * - 手动备份：带秒级时间戳文件名，永不覆盖历史备份；
 * - 自动备份：每日首次打开 App 触发，当日幂等，固定文件名覆盖（云端不堆积）；
 * - 数据链路 100% 复用 T11（BackupManager 字节级导出/导入）。
 */
interface CloudSyncManager {

    /** 验证连通性 + 目录写权限（先读后写各验一次） */
    suspend fun testConnection(config: WebDavConfig): Result<Unit>

    /** 立即备份到云端，返回远端文件名 */
    suspend fun backupNow(): Result<String>

    /** 云端备份列表（新→旧排序） */
    suspend fun listBackups(): Result<List<WebDavFile>>

    /** 恢复指定云端备份（覆盖导入，调用方须先经防呆确认） */
    suspend fun restore(fileName: String): Result<ImportSummary>

    /** 每日首次打开触发自动备份（当日幂等；未配置/关开关/无网直接跳过） */
    suspend fun maybeAutoBackup(): AutoBackupOutcome
}

class CloudSyncManagerImpl(
    private val webDavClient: WebDavClient,
    private val backupManager: BackupManager,
    private val store: CloudSyncStore,
    private val networkChecker: NetworkChecker,
    private val dateProvider: DateProvider
) : CloudSyncManager {

    override suspend fun testConnection(config: WebDavConfig): Result<Unit> {
        // 先验证读（PROPFIND），再验证写（MKCOL 建备份目录）：一次配置，上传/恢复都稳
        webDavClient.testConnection(config).onFailure { return Result.failure(it) }
        return webDavClient.ensureDirectory(config, CloudBackupNames.REMOTE_DIR)
    }

    override suspend fun backupNow(): Result<String> {
        WebDavLog.d("手动备份: 开始（读配置+打包）")
        val config = store.settings().config
            ?: return Result.failure(IllegalStateException("请先配置云端账号"))
        val zipBytes = backupManager.exportZipBytes().getOrElse {
            WebDavLog.d("手动备份打包失败: ${it.message}")
            return Result.failure(IllegalStateException("备份打包失败：${it.message ?: "未知错误"}"))
        }
        val fileName = CloudBackupNames.manualFileName(System.currentTimeMillis())
        WebDavLog.d("手动备份: 文件=$fileName, zip大小=${zipBytes.size}B, 目录=${CloudBackupNames.REMOTE_DIR}")
        return uploadZip(config, fileName, zipBytes).map { fileName }
    }

    override suspend fun listBackups(): Result<List<WebDavFile>> {
        val config = store.settings().config
            ?: return Result.failure(IllegalStateException("请先配置云端账号"))
        webDavClient.ensureDirectory(config, CloudBackupNames.REMOTE_DIR)
            .onFailure { return Result.failure(it) }
        return webDavClient.list(config, CloudBackupNames.REMOTE_DIR).map { files ->
            files.filter { it.name.endsWith(".zip") }.sortedWith(
                // 备份命名内的时间戳优先（多端时钟差异更小），无时间戳按服务器修改时间
                compareByDescending<WebDavFile> {
                    CloudBackupNames.extractTimestampMillis(it.name) ?: it.lastModifiedMillis
                }
            )
        }
    }

    override suspend fun restore(fileName: String): Result<ImportSummary> {
        // 文件名只允许来自云端列表的纯名字，拒绝路径穿越
        if (fileName.isBlank() || fileName.contains('/') || fileName.contains('\\')) {
            return Result.failure(IllegalArgumentException("非法的备份文件名"))
        }
        val config = store.settings().config
            ?: return Result.failure(IllegalStateException("请先配置云端账号"))
        return webDavClient.download(config, "${CloudBackupNames.REMOTE_DIR}/$fileName")
            .mapCatching { bytes ->
                backupManager.importBytes(bytes).getOrElse {
                    throw IllegalStateException("备份文件无效：${it.message ?: "解析失败"}")
                }
            }
            .onSuccess { recordSyncSuccess() }
            .onFailure { recordSyncFailure(it) }
    }

    override suspend fun maybeAutoBackup(): AutoBackupOutcome {
        val settings = store.settings()
        if (!settings.autoBackupEnabled) return AutoBackupOutcome.Disabled.also { WebDavLog.d("自动备份: 开关关闭，跳过") }
        val config = settings.config ?: return AutoBackupOutcome.NotConfigured.also { WebDavLog.d("自动备份: 未配置账号，跳过") }
        val today = dateProvider.today()
        if (settings.lastAutoBackupDate == today) {
            WebDavLog.d("自动备份: 今日($today)已备份，跳过")
            return AutoBackupOutcome.AlreadyDoneToday
        }
        if (!networkChecker.hasUsableNetwork(settings.wifiOnly)) {
            WebDavLog.d("自动备份: 网络不可用(wifiOnly=${settings.wifiOnly})，跳过")
            return AutoBackupOutcome.NetworkUnavailable
        }

        return backupManager.exportZipBytes().fold(
            onSuccess = { zipBytes ->
                WebDavLog.d("自动备份: 打包成功 ${zipBytes.size}B，开始上传 ${CloudBackupNames.AUTO_FILE_NAME}")
                uploadZip(config, CloudBackupNames.AUTO_FILE_NAME, zipBytes).fold(
                    onSuccess = {
                        store.recordAutoBackupDone(today)
                        recordSyncSuccess()
                        WebDavLog.d("自动备份: 上传成功 ${CloudBackupNames.AUTO_FILE_NAME}")
                        AutoBackupOutcome.Success(CloudBackupNames.AUTO_FILE_NAME)
                    },
                    onFailure = { error ->
                        WebDavLog.d("自动备份: 上传失败 ${error.message}")
                        recordSyncFailure(error)
                        AutoBackupOutcome.Failure(error.message ?: "自动备份失败")
                    }
                )
            },
            onFailure = { error ->
                WebDavLog.d("自动备份: 打包失败 ${error.message}")
                recordSyncFailure(error)
                AutoBackupOutcome.Failure("备份打包失败：${error.message ?: "未知错误"}")
            }
        )
    }

    // ---- 内部细节 ----

    /** 确保目录存在后上传（自动备份固定文件覆盖，手动时间戳名不冲突） */
    private suspend fun uploadZip(
        config: WebDavConfig,
        fileName: String,
        zipBytes: ByteArray
    ): Result<Unit> {
        webDavClient.ensureDirectory(config, CloudBackupNames.REMOTE_DIR)
            .onFailure { return Result.failure(it) }
        return webDavClient.upload(config, "${CloudBackupNames.REMOTE_DIR}/$fileName", zipBytes)
            .onSuccess { recordSyncSuccess() }
            .onFailure { recordSyncFailure(it) }
    }

    private suspend fun recordSyncSuccess() =
        store.recordLastSync(System.currentTimeMillis(), error = null)

    private suspend fun recordSyncFailure(error: Throwable) =
        store.recordLastSync(System.currentTimeMillis(), error = error.message ?: "未知错误")
}
