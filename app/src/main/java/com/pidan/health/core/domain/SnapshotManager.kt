package com.pidan.health.core.domain

import com.pidan.health.core.common.time.DateProvider
import com.pidan.health.core.data.entity.DailySnapshot
import com.pidan.health.core.data.repository.SnapshotRepository
import com.pidan.health.core.data.repository.UserPreferencesRepository
import com.pidan.health.core.domain.toNutritionFactors
import com.pidan.health.core.domain.toSnapshot

/**
 * 每日快照管理（UseCase 层）：决定快照"何时生成/更新"，并落实历史冻结原则。
 *
 * 快照生成时机（《需求梳理》1.1）：
 * 1. 当天首次打开 App → [ensureTodaySnapshot]
 * 2. 修改体重（T4 体重编辑弹窗确认）→ [refreshWithWeight]
 * 3. 修改自定义系数（T11 设置页保存）→ [refreshWithCurrentWeight]
 *
 * 历史冻结红线：所有写入只覆盖当天主键（INSERT OR REPLACE），
 * 昨天 = 不同主键 = 物理不可能被触碰；历史快照永不回溯重算。
 */
interface SnapshotManager {

    /**
     * 当天首次打开 App 时调用：
     * - 当天快照已存在 → 原样返回（幂等）
     * - 不存在但历史有体重记录 → 沿用最近一次体重生成当天快照
     * - 全新用户（无任何快照）→ 返回 null，由 UI 引导录入体重（T4）
     */
    suspend fun ensureTodaySnapshot(): DailySnapshot?

    /** 修改体重：以新体重 + 当前系数重算目标，覆盖当天快照（历史不动） */
    suspend fun refreshWithWeight(weightKg: Float): DailySnapshot

    /**
     * 修改自定义系数：保持当天体重不变，仅重算目标覆盖当天快照。
     * 无任何体重记录时返回 null（改系数不可能改变体重）。
     */
    suspend fun refreshWithCurrentWeight(): DailySnapshot?
}

class SnapshotManagerImpl(
    private val snapshotRepository: SnapshotRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val dateProvider: DateProvider
) : SnapshotManager {

    override suspend fun ensureTodaySnapshot(): DailySnapshot? {
        val today = dateProvider.today()
        snapshotRepository.getSnapshot(today)?.let { return it }
        val lastKnownWeight = latestWeight() ?: return null
        return refreshWithWeight(lastKnownWeight)
    }

    override suspend fun refreshWithWeight(weightKg: Float): DailySnapshot {
        val snapshot = buildSnapshot(dateProvider.today(), weightKg)
        snapshotRepository.upsertSnapshot(snapshot)
        return snapshot
    }

    override suspend fun refreshWithCurrentWeight(): DailySnapshot? {
        val today = dateProvider.today()
        val weight = snapshotRepository.getSnapshot(today)?.weight
            ?: latestWeight()
            ?: return null
        val snapshot = buildSnapshot(today, weight)
        snapshotRepository.upsertSnapshot(snapshot)
        return snapshot
    }

    /** 当天系数 + 指定体重 → 目标 → 快照实体 */
    private suspend fun buildSnapshot(date: String, weightKg: Float): DailySnapshot {
        val factors = userPreferencesRepository.getOrDefault().toNutritionFactors()
        val targets = NutritionCalculator.computeTargets(weightKg, factors)
        return targets.toSnapshot(date = date, weight = weightKg, updatedAt = System.currentTimeMillis())
    }

    /** 最近一次已知体重（快照按日期倒序，第一条即最近） */
    private suspend fun latestWeight(): Float? =
        snapshotRepository.getRecentWeights().firstOrNull()?.weight
}
