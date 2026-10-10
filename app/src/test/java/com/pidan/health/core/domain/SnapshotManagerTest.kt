package com.pidan.health.core.domain

import com.pidan.health.core.common.time.DateProvider
import com.pidan.health.core.data.entity.DailySnapshot
import com.pidan.health.core.data.entity.UserPreferences
import com.pidan.health.core.data.model.WeightTrendItem
import com.pidan.health.core.data.repository.SnapshotRepository
import com.pidan.health.core.data.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * 快照生成时机与历史冻结原则验证。
 * 用内存 Fake 仓库模拟 Room（主键 date 覆盖语义与 INSERT OR REPLACE 一致），
 * 验证：改体重/改系数只写当天快照，历史快照字段一字不变。
 */
class SnapshotManagerTest {

    private lateinit var snapshotRepo: FakeSnapshotRepository
    private lateinit var prefsRepo: FakeUserPreferencesRepository
    private lateinit var dateProvider: FakeDateProvider
    private lateinit var manager: SnapshotManager

    private val yesterday = "2026-10-08"
    private val today = "2026-10-09"

    @Before
    fun setup() {
        snapshotRepo = FakeSnapshotRepository()
        prefsRepo = FakeUserPreferencesRepository()
        dateProvider = FakeDateProvider(today)
        manager = SnapshotManagerImpl(snapshotRepo, prefsRepo, dateProvider)
    }

    // ---- 时机 1：当天首次打开 App ----

    @Test
    fun `全新用户无任何快照 - 返回null待UI引导录体重`() = runTest {
        assertNull(manager.ensureTodaySnapshot())
    }

    @Test
    fun `昨天有快照今天首次打开 - 沿用最近体重生成今天快照`() = runTest {
        seedSnapshot(date = yesterday, weight = 70f)

        val todaySnapshot = manager.ensureTodaySnapshot()

        assertNotNull(todaySnapshot)
        assertEquals(today, todaySnapshot!!.date)
        assertEquals(70f, todaySnapshot.weight, 0.001f)
        assertEquals(210f, todaySnapshot.targetCarbs, 0.001f)
    }

    @Test
    fun `当天快照已存在 - 幂等原样返回不重算`() = runTest {
        seedSnapshot(date = yesterday, weight = 70f)
        manager.ensureTodaySnapshot()
        // 手动污染今天快照的目标值，验证不会被 ensure 覆盖
        snapshotRepo.snapshots[today] = snapshotRepo.snapshots[today]!!.copy(targetCarbs = 999f)

        val result = manager.ensureTodaySnapshot()

        assertEquals(999f, result!!.targetCarbs, 0.001f)
    }

    // ---- 时机 2：修改体重 ----

    @Test
    fun `今天改体重 - 今天快照更新 - 昨天快照一字不变`() = runTest {
        seedSnapshot(date = yesterday, weight = 70f)
        manager.ensureTodaySnapshot()
        val yesterdayBefore = snapshotRepo.snapshots[yesterday]!!

        manager.refreshWithWeight(72f)

        // 今天：体重与目标全部按 72kg 重算
        val todayAfter = snapshotRepo.snapshots[today]!!
        assertEquals(72f, todayAfter.weight, 0.001f)
        assertEquals(216f, todayAfter.targetCarbs, 0.001f)      // 72 × 3
        assertEquals(93.6f, todayAfter.targetProteinMin, 0.001f) // 72 × 1.3
        assertEquals(122.4f, todayAfter.targetProteinMax, 0.001f) // 72 × 1.7
        assertEquals(36f, todayAfter.targetFat, 0.001f)          // 72 × 0.5

        // 昨天：逐字段冻结校验
        val yesterdayAfter = snapshotRepo.snapshots[yesterday]!!
        assertEquals(yesterdayBefore, yesterdayAfter)
    }

    // ---- 时机 3：修改自定义系数 ----

    @Test
    fun `改碳水系数 - 仅当天及未来目标变化 - 体重与历史不变`() = runTest {
        seedSnapshot(date = yesterday, weight = 70f)
        manager.ensureTodaySnapshot() // 今天快照沿用昨天体重 70
        manager.refreshWithWeight(72f) // 今天体重改为 72
        val yesterdayBefore = snapshotRepo.snapshots[yesterday]!!

        // 用户在设置页把碳水系数 3.0 改为 4.0
        prefsRepo.prefs = UserPreferences(carbsFactor = 4.0f)
        val refreshed = manager.refreshWithCurrentWeight()

        // 今天：体重保持 72 不变，碳水目标按新系数重算
        assertNotNull(refreshed)
        assertEquals(72f, refreshed!!.weight, 0.001f)
        assertEquals(288f, refreshed.targetCarbs, 0.001f)       // 72 × 4

        // 昨天：碳水目标仍是旧系数结果 210，逐字段冻结
        val yesterdayAfter = snapshotRepo.snapshots[yesterday]!!
        assertEquals(210f, yesterdayAfter.targetCarbs, 0.001f)
        assertEquals(yesterdayBefore, yesterdayAfter)
    }

    @Test
    fun `改纤维盐自定义 - 仅当天目标变化 - 历史仍用旧默认值`() = runTest {
        seedSnapshot(date = yesterday, weight = 70f)
        manager.ensureTodaySnapshot()
        val yesterdayBefore = snapshotRepo.snapshots[yesterday]!!

        prefsRepo.prefs = UserPreferences(isFiberCustom = true, fiberMin = 20f, fiberMax = 35f, isSaltCustom = true, saltMax = 6f)
        val refreshed = manager.refreshWithCurrentWeight()

        assertEquals(20f, refreshed!!.targetFiberMin, 0.001f)
        assertEquals(35f, refreshed.targetFiberMax, 0.001f)
        assertEquals(6f, refreshed.targetSaltMax, 0.001f)

        val yesterdayAfter = snapshotRepo.snapshots[yesterday]!!
        assertEquals(25f, yesterdayAfter.targetFiberMin, 0.001f)
        assertEquals(30f, yesterdayAfter.targetFiberMax, 0.001f)
        assertEquals(5f, yesterdayAfter.targetSaltMax, 0.001f)
        assertEquals(yesterdayBefore, yesterdayAfter)
    }

    @Test
    fun `无任何体重时改系数 - 返回null不产生快照`() = runTest {
        assertNull(manager.refreshWithCurrentWeight())
    }

    // ---- 跨天场景：日期推进后"昨天"的快照彻底不可变 ----

    @Test
    fun `跨天后首次打开 - 新日期生成新快照 - 旧日期不受任何影响`() = runTest {
        seedSnapshot(date = yesterday, weight = 70f)
        manager.ensureTodaySnapshot()
        val oldDaySnapshot = snapshotRepo.snapshots[today]!!

        // 日期推进到明天
        val tomorrow = "2026-10-10"
        dateProvider.today = tomorrow

        val newDay = manager.ensureTodaySnapshot()

        assertEquals(tomorrow, newDay!!.date)
        assertEquals(oldDaySnapshot, snapshotRepo.snapshots[today])
    }

    // ---- 测试替身 ----

    private suspend fun seedSnapshot(date: String, weight: Float) {
        val snapshot = NutritionCalculator
            .computeTargets(weight, UserPreferences().toNutritionFactors())
            .toSnapshot(date, weight, updatedAt = 0L)
        snapshotRepo.snapshots[date] = snapshot
    }

    private class FakeSnapshotRepository : SnapshotRepository {
        val snapshots = LinkedHashMap<String, DailySnapshot>()

        override suspend fun getSnapshot(date: String): DailySnapshot? = snapshots[date]

        override suspend fun upsertSnapshot(snapshot: DailySnapshot) {
            snapshots[snapshot.date] = snapshot
        }

        override suspend fun getRecentWeights(): List<WeightTrendItem> =
            snapshots.values.sortedByDescending { it.date }.map { WeightTrendItem(it.date, it.weight) }

        override suspend fun getAll(): List<DailySnapshot> =
            snapshots.values.sortedByDescending { it.date }
    }

    private class FakeUserPreferencesRepository(
        var prefs: UserPreferences = UserPreferences()
    ) : UserPreferencesRepository {
        override suspend fun getOrDefault(): UserPreferences = prefs
        override fun observe(): Flow<UserPreferences?> = flowOf(prefs)
        override suspend fun save(prefs: UserPreferences) {
            this.prefs = prefs
        }
    }

    private class FakeDateProvider(var today: String) : DateProvider {
        override fun today(): String = today
    }
}
