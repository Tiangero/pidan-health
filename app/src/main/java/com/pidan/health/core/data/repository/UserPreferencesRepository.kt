package com.pidan.health.core.data.repository

import com.pidan.health.core.data.dao.UserPreferencesDao
import com.pidan.health.core.data.entity.UserPreferences
import kotlinx.coroutines.flow.Flow

/**
 * 用户配置仓库（营养系数、纤维/盐自定义开关与值）。单例行，缺省时返回默认配置。
 */
interface UserPreferencesRepository {

    /** 读取配置，首次使用返回默认值（不落库，等用户真正修改时才 upsert） */
    suspend fun getOrDefault(): UserPreferences

    /** 配置变化响应式观察 */
    fun observe(): Flow<UserPreferences?>

    suspend fun save(prefs: UserPreferences)
}

class UserPreferencesRepositoryImpl(
    private val userPreferencesDao: UserPreferencesDao
) : UserPreferencesRepository {

    override suspend fun getOrDefault(): UserPreferences =
        userPreferencesDao.get() ?: UserPreferences()

    override fun observe(): Flow<UserPreferences?> = userPreferencesDao.observe()

    override suspend fun save(prefs: UserPreferences) = userPreferencesDao.upsert(prefs)
}
