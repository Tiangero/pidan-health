package com.pidan.health.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pidan.health.core.data.entity.UserPreferences
import kotlinx.coroutines.flow.Flow

@Dao
interface UserPreferencesDao {

    /** 单例配置（id 固定 1），首次使用前可能不存在，默认值兜底在 Repository 层处理 */
    @Query("SELECT * FROM user_preferences WHERE id = 1 LIMIT 1")
    suspend fun get(): UserPreferences?

    /** 配置响应式观察（T3/T4 计算引擎用） */
    @Query("SELECT * FROM user_preferences WHERE id = 1 LIMIT 1")
    fun observe(): Flow<UserPreferences?>

    /** 生成/更新配置：按主键 id 覆盖 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(prefs: UserPreferences)
}
