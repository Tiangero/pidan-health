package com.pidan.health.core.data.backup

import android.net.Uri
import com.pidan.health.core.common.time.DateProvider
import com.pidan.health.core.data.network.NetworkChecker
import com.pidan.health.core.data.storage.CloudSyncStore
import com.pidan.health.core.data.storage.CloudSyncStoreImpl
import com.pidan.health.core.data.webdav.WebDavClient
import com.pidan.health.core.data.webdav.WebDavConfig
import com.pidan.health.core.data.webdav.WebDavFile
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/** T13：CloudSyncManager 行为单测（fake 全套依赖 + 内存 DataStore） */
class CloudSyncManagerTest {

    private val config = WebDavConfig("https://dav.example.com/dav/", "user@example.com", "app-pass")

    private lateinit var webDavClient: FakeWebDavClient
    private lateinit var backupManager: FakeBackupManager
    private lateinit var networkChecker: FakeNetworkChecker
    private lateinit var store: CloudSyncStore
    private lateinit var manager: CloudSyncManager

    @Before
    fun setUp() = runTest {
        webDavClient = FakeWebDavClient()
        backupManager = FakeBackupManager()
        networkChecker = FakeNetworkChecker(available = true)
        store = CloudSyncStoreImpl(InMemoryDataStore())
        manager = CloudSyncManagerImpl(
            webDavClient = webDavClient,
            backupManager = backupManager,
            store = store,
            networkChecker = networkChecker,
            dateProvider = FixedDateProvider("2026-10-12")
        )
        store.saveConfig(config)
    }

    @Test
    fun `auto backup skipped when disabled`() = runTest {
        store.setAutoBackupEnabled(false)
        assertEquals(AutoBackupOutcome.Disabled, manager.maybeAutoBackup())
        assertTrue(webDavClient.uploaded.isEmpty())
    }

    @Test
    fun `auto backup skipped when not configured`() = runTest {
        // 未保存任何配置：换一个全新 store
        val freshStore = CloudSyncStoreImpl(InMemoryDataStore())
        val freshManager = CloudSyncManagerImpl(
            webDavClient, backupManager, freshStore, networkChecker, FixedDateProvider("2026-10-12")
        )
        assertEquals(AutoBackupOutcome.NotConfigured, freshManager.maybeAutoBackup())
    }

    @Test
    fun `auto backup is idempotent within the same day`() = runTest {
        assertEquals(
            AutoBackupOutcome.Success(CloudBackupNames.AUTO_FILE_NAME),
            manager.maybeAutoBackup()
        )
        assertEquals(AutoBackupOutcome.AlreadyDoneToday, manager.maybeAutoBackup())
        assertEquals(1, webDavClient.uploaded.size)
    }

    @Test
    fun `auto backup skipped when network unavailable`() = runTest {
        networkChecker.available = false
        assertEquals(AutoBackupOutcome.NetworkUnavailable, manager.maybeAutoBackup())
        assertTrue(webDavClient.uploaded.isEmpty())
    }

    @Test
    fun `auto backup uploads fixed file name and records state`() = runTest {
        val outcome = manager.maybeAutoBackup()
        assertTrue(outcome is AutoBackupOutcome.Success)
        val upload = webDavClient.uploaded.single()
        assertEquals("${CloudBackupNames.REMOTE_DIR}/${CloudBackupNames.AUTO_FILE_NAME}", upload.first)
        assertEquals(FakeBackupManager.ZIP_BYTES, upload.second)
        assertEquals("2026-10-12", store.settings().lastAutoBackupDate)
        assertEquals(null, store.settings().lastSyncError)
    }

    @Test
    fun `wifi only blocks auto backup on non wifi network`() = runTest {
        store.setWifiOnly(true)
        networkChecker.available = false
        assertEquals(AutoBackupOutcome.NetworkUnavailable, manager.maybeAutoBackup())
    }

    @Test
    fun `backup now uploads manual named file`() = runTest {
        val fileName = manager.backupNow().getOrThrow()
        assertTrue(fileName.startsWith("皮蛋健康_backup_"))
        val upload = webDavClient.uploaded.single()
        assertEquals("${CloudBackupNames.REMOTE_DIR}/$fileName", upload.first)
        assertEquals(FakeBackupManager.ZIP_BYTES, upload.second)
    }

    @Test
    fun `backup now fails when export fails`() = runTest {
        backupManager.exportShouldFail = true
        val result = manager.backupNow()
        assertTrue(result.isFailure)
        assertTrue(webDavClient.uploaded.isEmpty())
    }

    @Test
    fun `list backups sorted newest first and only zip`() = runTest {
        webDavClient.filesToReturn = listOf(
            WebDavFile("皮蛋健康_backup_20261010_080000.zip", 1, 100),
            WebDavFile("皮蛋健康_backup_20261012_090000.zip", 1, 100),
            WebDavFile("皮蛋健康_backup_20261011_070000.zip", 1, 100),
            WebDavFile("notes.txt", 1, 999)
        )
        val names = manager.listBackups().getOrThrow().map { it.name }
        assertEquals(
            listOf(
                "皮蛋健康_backup_20261012_090000.zip",
                "皮蛋健康_backup_20261011_070000.zip",
                "皮蛋健康_backup_20261010_080000.zip"
            ),
            names
        )
    }

    @Test
    fun `restore downloads and imports`() = runTest {
        val summary = manager.restore("皮蛋健康_backup_20261012_090000.zip").getOrThrow()
        assertEquals(
            "${CloudBackupNames.REMOTE_DIR}/皮蛋健康_backup_20261012_090000.zip",
            webDavClient.downloaded.single()
        )
        assertEquals(3, summary.foodCount)
    }

    @Test
    fun `restore rejects path traversal`() = runTest {
        val result = manager.restore("../evil.zip")
        assertTrue(result.isFailure)
        assertTrue(webDavClient.downloaded.isEmpty())
    }

    // ---- fakes ----

    private class FakeWebDavClient : WebDavClient {
        val uploaded = mutableListOf<Pair<String, ByteArray>>()
        val downloaded = mutableListOf<String>()
        var filesToReturn: List<WebDavFile> = emptyList()

        override suspend fun testConnection(config: WebDavConfig): Result<Unit> = Result.success(Unit)
        override suspend fun list(config: WebDavConfig, remoteDir: String): Result<List<WebDavFile>> =
            Result.success(filesToReturn)
        override suspend fun ensureDirectory(config: WebDavConfig, remoteDir: String): Result<Unit> =
            Result.success(Unit)
        override suspend fun upload(config: WebDavConfig, remotePath: String, bytes: ByteArray): Result<Unit> {
            uploaded.add(remotePath to bytes)
            return Result.success(Unit)
        }
        override suspend fun download(config: WebDavConfig, remotePath: String): Result<ByteArray> {
            downloaded.add(remotePath)
            return Result.success(FakeBackupManager.ZIP_BYTES)
        }
        override suspend fun delete(config: WebDavConfig, remotePath: String): Result<Unit> =
            Result.success(Unit)
    }

    private class FakeBackupManager : BackupManager {
        companion object { val ZIP_BYTES = "fake-zip".toByteArray() }
        var exportShouldFail = false
        override suspend fun export(): Result<String> = Result.success("ignored")
        override suspend fun exportZipBytes(): Result<ByteArray> =
            if (exportShouldFail) Result.failure(IllegalStateException("打包失败"))
            else Result.success(ZIP_BYTES)
        override suspend fun import(uri: Uri): Result<ImportSummary> = Result.success(summary())
        override suspend fun importBytes(bytes: ByteArray): Result<ImportSummary> = Result.success(summary())
        private fun summary() = ImportSummary(foodCount = 3, dietCount = 0, snapshotCount = 0, exerciseCount = 0)
    }

    private class FakeNetworkChecker(var available: Boolean) : NetworkChecker {
        override fun hasUsableNetwork(requireWifi: Boolean): Boolean = available
    }

    private class FixedDateProvider(private val todayIso: String) : DateProvider {
        override fun today(): String = todayIso
        override fun daysAgo(days: Int): String =
            LocalDate.parse(todayIso).minusDays(days.toLong()).toString()
    }

    /** 内存版 DataStore（Preferences 语义：merge 写入） */
    private class InMemoryDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow<Preferences>(emptyPreferences())
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            state.value = transform(state.value)
            return state.value
        }
    }
}
