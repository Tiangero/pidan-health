package com.pidan.health.core.data.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.pidan.health.core.data.webdav.WebDavConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * 云同步轻量配置（T13）：唯一使用 DataStore 的场景——
 * 服务器地址/账号/应用密码、自动备份开关、Wi-Fi 开关、最近同步状态。
 * 均为设备本机的轻量键值，不涉及业务数据（业务数据仍归 Room 五表）。
 */
data class CloudSyncSettings(
    /** username/password 均非空视为已配置 */
    val config: WebDavConfig?,
    /** 每日首次打开自动备份（用户拍板：默认开启） */
    val autoBackupEnabled: Boolean,
    /** 仅 Wi-Fi 网络同步（默认关闭，任意网络均可） */
    val wifiOnly: Boolean,
    /** 最近一次自动备份完成的日期（ISO yyyy-MM-dd），当日幂等判据 */
    val lastAutoBackupDate: String?,
    /** 最近一次同步动作时间戳（手动/自动均记录） */
    val lastSyncAtMillis: Long?,
    /** 最近一次同步结果：null=成功，否则为失败原因 */
    val lastSyncError: String?
)

interface CloudSyncStore {

    val settingsFlow: Flow<CloudSyncSettings>

    suspend fun settings(): CloudSyncSettings

    suspend fun saveConfig(config: WebDavConfig)

    suspend fun setAutoBackupEnabled(enabled: Boolean)

    suspend fun setWifiOnly(enabled: Boolean)

    /** 自动备份成功后写入当天日期（当日不再重复备份） */
    suspend fun recordAutoBackupDone(dateIso: String)

    /** 任何同步动作结束后记录时间与结果（error=null 表示成功） */
    suspend fun recordLastSync(atMillis: Long, error: String?)
}

class CloudSyncStoreImpl(
    private val dataStore: DataStore<Preferences>
) : CloudSyncStore {

    override val settingsFlow: Flow<CloudSyncSettings> = dataStore.data.map { prefs -> prefs.toSettings() }

    override suspend fun settings(): CloudSyncSettings = dataStore.data.first().toSettings()

    override suspend fun saveConfig(config: WebDavConfig) {
        dataStore.edit { prefs ->
            prefs[KEY_SERVER_URL] = config.baseUrl.trim()
            prefs[KEY_USERNAME] = config.username.trim()
            prefs[KEY_APP_PASSWORD] = config.password
        }
    }

    override suspend fun setAutoBackupEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_AUTO_BACKUP_ENABLED] = enabled }
    }

    override suspend fun setWifiOnly(enabled: Boolean) {
        dataStore.edit { it[KEY_WIFI_ONLY] = enabled }
    }

    override suspend fun recordAutoBackupDone(dateIso: String) {
        dataStore.edit { it[KEY_LAST_AUTO_BACKUP_DATE] = dateIso }
    }

    override suspend fun recordLastSync(atMillis: Long, error: String?) {
        dataStore.edit { prefs ->
            prefs[KEY_LAST_SYNC_AT] = atMillis
            if (error == null) prefs.remove(KEY_LAST_SYNC_ERROR) else prefs[KEY_LAST_SYNC_ERROR] = error.take(200)
        }
    }

    private fun Preferences.toSettings(): CloudSyncSettings {
        val serverUrl = this[KEY_SERVER_URL] ?: WebDavDefaults.DEFAULT_SERVER_URL
        val username = this[KEY_USERNAME] ?: ""
        val password = this[KEY_APP_PASSWORD] ?: ""
        return CloudSyncSettings(
            config = WebDavConfig(
                baseUrl = serverUrl.ifBlank { WebDavDefaults.DEFAULT_SERVER_URL },
                username = username,
                password = password
            ).takeIf { username.isNotBlank() && password.isNotBlank() },
            autoBackupEnabled = this[KEY_AUTO_BACKUP_ENABLED] ?: true,
            wifiOnly = this[KEY_WIFI_ONLY] ?: false,
            lastAutoBackupDate = this[KEY_LAST_AUTO_BACKUP_DATE],
            lastSyncAtMillis = this[KEY_LAST_SYNC_AT],
            lastSyncError = this[KEY_LAST_SYNC_ERROR]
        )
    }

    private companion object {
        val KEY_SERVER_URL = stringPreferencesKey("cloud_server_url")
        val KEY_USERNAME = stringPreferencesKey("cloud_username")
        val KEY_APP_PASSWORD = stringPreferencesKey("cloud_app_password")
        val KEY_AUTO_BACKUP_ENABLED = booleanPreferencesKey("cloud_auto_backup_enabled")
        val KEY_WIFI_ONLY = booleanPreferencesKey("cloud_wifi_only")
        val KEY_LAST_AUTO_BACKUP_DATE = stringPreferencesKey("cloud_last_auto_backup_date")
        val KEY_LAST_SYNC_AT = longPreferencesKey("cloud_last_sync_at")
        val KEY_LAST_SYNC_ERROR = stringPreferencesKey("cloud_last_sync_error")
    }
}

/** 云同步默认值（坚果云为默认预设，地址可编辑兼容任意 WebDAV 服务） */
object WebDavDefaults {
    const val DEFAULT_SERVER_URL = "https://dav.jianguoyun.com/dav/"
}
