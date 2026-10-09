package com.pidan.health.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pidan.health.core.data.entity.ExerciseRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {

    @Query("SELECT * FROM exercise_record WHERE date = :date LIMIT 1")
    suspend fun getByDate(date: String): ExerciseRecord?

    /** 某日期区间的运动状态（月历视图按月查询），响应式 */
    @Query("SELECT * FROM exercise_record WHERE date BETWEEN :startDate AND :endDate")
    fun observeRange(startDate: String, endDate: String): Flow<List<ExerciseRecord>>

    /** 月历点击打卡 / 补签：按主键 date 覆盖 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: ExerciseRecord)

    /** 全量读取（备份导出 T11 用） */
    @Query("SELECT * FROM exercise_record ORDER BY date DESC")
    suspend fun getAll(): List<ExerciseRecord>
}
