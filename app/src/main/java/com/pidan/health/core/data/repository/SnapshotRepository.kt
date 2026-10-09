package com.pidan.health.core.data.repository

import com.pidan.health.core.data.dao.SnapshotDao
import com.pidan.health.core.data.entity.DailySnapshot
import com.pidan.health.core.data.model.WeightTrendItem

/**
 * 每日快照仓库。历史冻结原则：upsert 只按当天主键覆盖，历史快照永不回溯。
 */
interface SnapshotRepository {

    suspend fun getSnapshot(date: String): DailySnapshot?

    /** 生成/更新当天快照（INSERT OR REPLACE） */
    suspend fun upsertSnapshot(snapshot: DailySnapshot)

    /** 近30天体重走势（按日期倒序） */
    suspend fun getRecentWeights(): List<WeightTrendItem>

    suspend fun getAll(): List<DailySnapshot>
}

class SnapshotRepositoryImpl(
    private val snapshotDao: SnapshotDao
) : SnapshotRepository {

    override suspend fun getSnapshot(date: String): DailySnapshot? = snapshotDao.getSnapshot(date)

    override suspend fun upsertSnapshot(snapshot: DailySnapshot) = snapshotDao.upsertSnapshot(snapshot)

    override suspend fun getRecentWeights(): List<WeightTrendItem> = snapshotDao.getRecentWeights()

    override suspend fun getAll(): List<DailySnapshot> = snapshotDao.getAll()
}
