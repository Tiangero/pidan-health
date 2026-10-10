package com.pidan.health.core.data.repository

import com.pidan.health.core.data.dao.ExerciseDao
import com.pidan.health.core.data.entity.ExerciseRecord
import kotlinx.coroutines.flow.Flow

/**
 * 运动打卡仓库。
 */
interface ExerciseRepository {

    suspend fun getByDate(date: String): ExerciseRecord?

    /** 月历区间状态（响应式） */
    fun observeRange(startDate: String, endDate: String): Flow<List<ExerciseRecord>>

    /** 打卡 / 补签 / 切换状态（按主键 date 覆盖） */
    suspend fun upsert(record: ExerciseRecord)

    /** 显式切回「休息」：删除该日记录（无记录 = 休息） */
    suspend fun deleteByDate(date: String)

    suspend fun getAll(): List<ExerciseRecord>
}

class ExerciseRepositoryImpl(
    private val exerciseDao: ExerciseDao
) : ExerciseRepository {

    override suspend fun getByDate(date: String): ExerciseRecord? = exerciseDao.getByDate(date)

    override fun observeRange(startDate: String, endDate: String): Flow<List<ExerciseRecord>> =
        exerciseDao.observeRange(startDate, endDate)

    override suspend fun upsert(record: ExerciseRecord) = exerciseDao.upsert(record)

    override suspend fun deleteByDate(date: String) = exerciseDao.deleteByDate(date)

    override suspend fun getAll(): List<ExerciseRecord> = exerciseDao.getAll()
}
