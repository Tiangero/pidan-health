package com.pidan.health.core.data.repository

import com.pidan.health.core.data.entity.FoodTemplate
import kotlinx.coroutines.flow.Flow

/**
 * 食物模板仓库。ViewModel 只依赖此接口，实现细节（Room）不外泄。
 */
interface FoodRepository {

    /** 食物库列表（响应式，按更新时间倒序） */
    fun observeAll(): Flow<List<FoodTemplate>>

    suspend fun getById(id: Long): FoodTemplate?

    /** 单条响应式观察（详情页用，模板被删/编辑后自动响应） */
    fun observeById(id: Long): Flow<FoodTemplate?>

    /** 新建模板，返回自增 id */
    suspend fun insert(food: FoodTemplate): Long

    suspend fun update(food: FoodTemplate)

    suspend fun delete(food: FoodTemplate)

    suspend fun deleteByIds(ids: List<Long>)
}

class FoodRepositoryImpl(
    private val foodTemplateDao: com.pidan.health.core.data.dao.FoodTemplateDao
) : FoodRepository {

    override fun observeAll(): Flow<List<FoodTemplate>> = foodTemplateDao.observeAll()

    override suspend fun getById(id: Long): FoodTemplate? = foodTemplateDao.getById(id)

    override fun observeById(id: Long): Flow<FoodTemplate?> = foodTemplateDao.observeById(id)

    override suspend fun insert(food: FoodTemplate): Long = foodTemplateDao.insert(food)

    override suspend fun update(food: FoodTemplate) = foodTemplateDao.update(food)

    override suspend fun delete(food: FoodTemplate) = foodTemplateDao.delete(food)

    override suspend fun deleteByIds(ids: List<Long>) = foodTemplateDao.deleteByIds(ids)
}
