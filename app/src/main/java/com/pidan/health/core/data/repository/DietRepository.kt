package com.pidan.health.core.data.repository

import com.pidan.health.core.data.dao.DietDao
import com.pidan.health.core.data.entity.DietRecord
import com.pidan.health.core.data.model.DailySummary
import com.pidan.health.core.data.model.DatedDailySummary
import com.pidan.health.core.data.model.FoodRankItem
import kotlinx.coroutines.flow.Flow

/**
 * 饮食记录仓库。历史冻结原则：写入即固化，查询只对 diet_record 自身聚合。
 */
interface DietRepository {

    /** 某天营养与花费聚合（空数据日返回全 0 的 summary 或 null） */
    suspend fun getDailySummary(date: String): DailySummary?

    /** 某天营养与花费聚合（响应式：录入/删除后首页自动刷新） */
    fun observeDailySummary(date: String): Flow<DailySummary?>

    /** 某天饮食记录明细（响应式，按录入时间升序；克数与营养为录入时固化值） */
    fun observeRecordsByDate(date: String): Flow<List<DietRecord>>

    /** 近7天日均花费（startDate 之后的按天花费取平均），无数据返回 null */
    suspend fun getAvgDailyCostOfLast7Days(startDate: String): Float?

    /** 区间内逐日聚合（统计页开销曲线 / 达标率均值计算） */
    suspend fun getDailySummariesBetween(startDate: String, endDate: String): List<DatedDailySummary>

    /** 区间内高频食物 Top5（周/月切换共用，已删除模板的历史记录同样参与统计） */
    suspend fun getTopFoodsBetween(startDate: String, endDate: String): List<FoodRankItem>

    /** 快捷录入写入（数值已在固化进 record） */
    suspend fun insert(record: DietRecord): Long

    suspend fun deleteById(id: Long)

    suspend fun getAll(): List<DietRecord>
}

class DietRepositoryImpl(
    private val dietDao: DietDao
) : DietRepository {

    override suspend fun getDailySummary(date: String): DailySummary? =
        dietDao.getDailyNutritionAndCost(date)

    override fun observeDailySummary(date: String): Flow<DailySummary?> =
        dietDao.observeDailySummary(date)

    override fun observeRecordsByDate(date: String): Flow<List<DietRecord>> =
        dietDao.observeRecordsByDate(date)

    override suspend fun getAvgDailyCostOfLast7Days(startDate: String): Float? =
        dietDao.getAvgDailyCostOfLast7Days(startDate)

    override suspend fun getDailySummariesBetween(
        startDate: String,
        endDate: String
    ): List<DatedDailySummary> = dietDao.getDailySummariesBetween(startDate, endDate)

    override suspend fun getTopFoodsBetween(
        startDate: String,
        endDate: String
    ): List<FoodRankItem> = dietDao.getTopFoodsBetween(startDate, endDate)

    override suspend fun insert(record: DietRecord): Long = dietDao.insert(record)

    override suspend fun deleteById(id: Long) = dietDao.deleteById(id)

    override suspend fun getAll(): List<DietRecord> = dietDao.getAll()
}
