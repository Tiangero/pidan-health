package com.pidan.health.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pidan.health.core.data.entity.DailySnapshot
import com.pidan.health.core.data.model.WeightTrendItem
import kotlinx.coroutines.flow.Flow

@Dao
interface SnapshotDao {

    /** 获取某天的快照，如果没有则返回 null（UI 层负责触发初始化） */
    @Query("SELECT * FROM daily_snapshot WHERE date = :date LIMIT 1")
    suspend fun getSnapshot(date: String): DailySnapshot?

    /** 某天快照响应式观察（首页体重/目标卡片自动刷新） */
    @Query("SELECT * FROM daily_snapshot WHERE date = :date LIMIT 1")
    fun observeSnapshot(date: String): Flow<DailySnapshot?>

    /** 生成/更新当天快照：INSERT OR REPLACE，按主键 date 覆盖，历史日期不受影响 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSnapshot(snapshot: DailySnapshot)

    /** 首页 - 体重卡片：近30天体重走势（按日期倒序） */
    @Query("SELECT date, weight FROM daily_snapshot ORDER BY date DESC LIMIT 30")
    suspend fun getRecentWeights(): List<WeightTrendItem>

    /** 统计页 - 区间内逐日快照（各日冻结目标，达标率均值计算用） */
    @Query("SELECT * FROM daily_snapshot WHERE date BETWEEN :startDate AND :endDate ORDER BY date ASC")
    suspend fun getSnapshotsBetween(startDate: String, endDate: String): List<DailySnapshot>

    /** 全量读取（备份导出 T11 用） */
    @Query("SELECT * FROM daily_snapshot ORDER BY date DESC")
    suspend fun getAll(): List<DailySnapshot>

    /** 备份导入批量重插（T11，按主键 date 覆盖） */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(snapshots: List<DailySnapshot>)

    /** 备份导入时清空重建（T11） */
    @Query("DELETE FROM daily_snapshot")
    suspend fun deleteAll()
}
