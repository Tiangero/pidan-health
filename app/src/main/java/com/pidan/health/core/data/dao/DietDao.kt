package com.pidan.health.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.pidan.health.core.data.entity.DietRecord
import com.pidan.health.core.data.model.DailySummary
import com.pidan.health.core.data.model.DatedDailySummary
import com.pidan.health.core.data.model.FoodRankItem
import kotlinx.coroutines.flow.Flow

@Dao
interface DietDao {

    /**
     * 首页 - 营养卡片 & 开销卡片：聚合某天的总摄入和总花费。
     * 营养值已在录入时固化到 diet_record，直接对自身聚合，历史数据不受模板修改/删除影响。
     * COALESCE 兜底：当天无记录时返回全 0（SUM 空行为 NULL，直接映射非空字段会抛异常）。
     */
    @Query(
        """
        SELECT
            COALESCE(SUM(intakeCarbs), 0)   as totalCarbs,
            COALESCE(SUM(intakeProtein), 0) as totalProtein,
            COALESCE(SUM(intakeFat), 0)     as totalFat,
            COALESCE(SUM(intakeFiber), 0)   as totalFiber,
            COALESCE(SUM(intakeSalt), 0)    as totalSalt,
            COALESCE(SUM(cost), 0)          as totalCost
        FROM diet_record
        WHERE date = :date
        """
    )
    suspend fun getDailyNutritionAndCost(date: String): DailySummary?

    /** 同上，响应式版本：录入/删除记录后首页自动刷新 */
    @Query(
        """
        SELECT
            COALESCE(SUM(intakeCarbs), 0)   as totalCarbs,
            COALESCE(SUM(intakeProtein), 0) as totalProtein,
            COALESCE(SUM(intakeFat), 0)     as totalFat,
            COALESCE(SUM(intakeFiber), 0)   as totalFiber,
            COALESCE(SUM(intakeSalt), 0)    as totalSalt,
            COALESCE(SUM(cost), 0)          as totalCost
        FROM diet_record
        WHERE date = :date
        """
    )
    fun observeDailySummary(date: String): Flow<DailySummary?>

    /**
     * 首页 - 开销卡片：近7天日均花费（用于"对比本周日均值"）。
     */
    @Query(
        """
        SELECT AVG(dailyCost) FROM (
            SELECT SUM(cost) as dailyCost FROM diet_record
            WHERE date >= :startDate GROUP BY date
        )
        """
    )
    suspend fun getAvgDailyCostOfLast7Days(startDate: String): Float?

    /**
     * 统计页 - 日期区间内高频食物 Top 5（周/月切换共用）。
     * 使用记录内快照字段聚合，已删除模板的历史记录同样参与统计。
     */
    @Query(
        """
        SELECT foodName, COUNT(id) as eatCount, SUM(cost) as totalCost,
               SUM(intakeProtein) as totalProtein
        FROM diet_record
        WHERE date BETWEEN :startDate AND :endDate
        GROUP BY foodName
        ORDER BY eatCount DESC
        LIMIT 5
        """
    )
    suspend fun getTopFoodsBetween(startDate: String, endDate: String): List<FoodRankItem>

    /**
     * 统计页 - 日期区间内逐日聚合（开销趋势曲线 / 营养达标均值计算）。
     */
    @Query(
        """
        SELECT
            date,
            COALESCE(SUM(intakeCarbs), 0)   as totalCarbs,
            COALESCE(SUM(intakeProtein), 0) as totalProtein,
            COALESCE(SUM(intakeFat), 0)     as totalFat,
            COALESCE(SUM(intakeFiber), 0)   as totalFiber,
            COALESCE(SUM(intakeSalt), 0)    as totalSalt,
            COALESCE(SUM(cost), 0)          as totalCost
        FROM diet_record
        WHERE date BETWEEN :startDate AND :endDate
        GROUP BY date
        ORDER BY date ASC
        """
    )
    suspend fun getDailySummariesBetween(startDate: String, endDate: String): List<DatedDailySummary>

    @Insert
    suspend fun insert(record: DietRecord): Long

    @Query("DELETE FROM diet_record WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 全量读取（备份导出 T11 / 测试用） */
    @Query("SELECT * FROM diet_record ORDER BY createdAt DESC")
    suspend fun getAll(): List<DietRecord>
}
