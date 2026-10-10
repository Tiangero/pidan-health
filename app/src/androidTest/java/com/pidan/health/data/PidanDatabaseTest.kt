package com.pidan.health.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pidan.health.core.data.PidanDatabase
import com.pidan.health.core.data.entity.DailySnapshot
import com.pidan.health.core.data.entity.DietRecord
import com.pidan.health.core.data.entity.ExerciseRecord
import com.pidan.health.core.data.entity.FoodTemplate
import com.pidan.health.core.data.entity.UserPreferences
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * T2 数据层验证：
 * 1. 五张表增删改查
 * 2. 日聚合 SUM 正确
 * 3. 快照 INSERT OR REPLACE 生效
 * 4. Top5 按次数分组排序正确
 * 5. 修改/删除食物模板后，已有 diet_record 数据不受影响（历史冻结）
 */
@RunWith(AndroidJUnit4::class)
class PidanDatabaseTest {

    private lateinit var db: PidanDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PidanDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ---------- 1. 五张表增删改查 ----------

    @Test
    fun foodTemplate_crud() = runTest {
        val dao = db.foodTemplateDao()
        val rice = FoodTemplate(
            name = "米饭", unitType = 1, weightPerServing = 150f,
            carbsPer100g = 25f, totalPrice = 10f, totalAmount = 1000f
        )
        val id = dao.insert(rice)
        assertTrue(id > 0)

        val loaded = dao.getById(id)
        assertNotNull(loaded)
        assertEquals("米饭", loaded!!.name)

        dao.update(loaded.copy(name = "东北大米"))
        assertEquals("东北大米", dao.getById(id)!!.name)

        dao.delete(dao.getById(id)!!)
        assertNull(dao.getById(id))
    }

    @Test
    fun dietRecord_crud() = runTest {
        val dao = db.dietDao()
        val id = dao.insert(dietRecord("2026-10-09", cost = 2.25f))
        assertTrue(id > 0)
        assertEquals(1, dao.getAll().size)
        dao.deleteById(id)
        assertTrue(dao.getAll().isEmpty())
    }

    @Test
    fun exerciseRecord_crud() = runTest {
        val dao = db.exerciseDao()
        dao.upsert(ExerciseRecord(date = "2026-10-09", type = 1))
        assertEquals(1, dao.getByDate("2026-10-09")!!.type)

        // 主键覆盖：补签/切换状态
        dao.upsert(ExerciseRecord(date = "2026-10-09", type = 2))
        assertEquals(2, dao.getByDate("2026-10-09")!!.type)
        assertEquals(1, dao.getAll().size)
    }

    @Test
    fun userPreferences_upsert() = runTest {
        val dao = db.userPreferencesDao()
        assertNull(dao.get())

        dao.upsert(UserPreferences(id = 1, carbsFactor = 3.0f))
        assertEquals(3.0f, dao.get()!!.carbsFactor)

        dao.upsert(UserPreferences(id = 1, carbsFactor = 2.5f, isSaltCustom = true, saltMax = 4f))
        assertEquals(2.5f, dao.get()!!.carbsFactor)
        assertTrue(dao.get()!!.isSaltCustom)
        assertEquals(4f, dao.get()!!.saltMax)
    }

    // ---------- 2. 日聚合 SUM 正确 ----------

    @Test
    fun dailySummary_sumCorrect() = runTest {
        val dao = db.dietDao()
        dao.insert(dietRecord("2026-10-09", carbs = 56.25f, protein = 10f, fat = 5f, fiber = 2f, salt = 1f, cost = 2.25f))
        dao.insert(dietRecord("2026-10-09", carbs = 43.75f, protein = 20f, fat = 5f, fiber = 3f, salt = 1f, cost = 7.75f))
        dao.insert(dietRecord("2026-10-08", carbs = 999f, cost = 100f)) // 非当天，不参与聚合

        val summary = dao.getDailyNutritionAndCost("2026-10-09")!!
        assertEquals(100f, summary.totalCarbs)
        assertEquals(30f, summary.totalProtein)
        assertEquals(10f, summary.totalFat)
        assertEquals(5f, summary.totalFiber)
        assertEquals(2f, summary.totalSalt)
        assertEquals(10f, summary.totalCost)
    }

    @Test
    fun dailySummary_emptyDay_returnsZeros() = runTest {
        val summary = db.dietDao().getDailyNutritionAndCost("2099-01-01")!!
        assertEquals(0f, summary.totalCarbs)
        assertEquals(0f, summary.totalCost)
    }

    @Test
    fun avgDailyCost_last7Days() = runTest {
        val dao = db.dietDao()
        // 窗口内三天：日花费 10、20、30 → 日均 20
        dao.insert(dietRecord("2026-10-03", cost = 10f))
        dao.insert(dietRecord("2026-10-05", cost = 12f))
        dao.insert(dietRecord("2026-10-05", cost = 8f))
        dao.insert(dietRecord("2026-10-07", cost = 30f))
        // 窗口外
        dao.insert(dietRecord("2026-10-01", cost = 999f))

        val avg = dao.getAvgDailyCostOfLast7Days("2026-10-03")!!
        assertEquals(20f, avg, 0.001f)
    }

    // ---------- 3. 快照 INSERT OR REPLACE 生效 ----------

    @Test
    fun snapshot_upsert_overwritesOnlySameDate() = runTest {
        val dao = db.snapshotDao()
        dao.upsertSnapshot(DailySnapshot(date = "2026-10-08", weight = 70f, targetCarbs = 210f, targetProteinMin = 91f, targetProteinMax = 119f, targetFat = 35f))
        dao.upsertSnapshot(DailySnapshot(date = "2026-10-09", weight = 70.5f, targetCarbs = 211.5f, targetProteinMin = 91.65f, targetProteinMax = 119.85f, targetFat = 35.25f))

        // 当天修改体重 → 同日期覆盖，不新增行
        dao.upsertSnapshot(DailySnapshot(date = "2026-10-09", weight = 69.5f, targetCarbs = 208.5f, targetProteinMin = 90.35f, targetProteinMax = 118.15f, targetFat = 34.75f))
        assertEquals(2, dao.getAll().size)

        // 今天的快照更新了
        val today = dao.getSnapshot("2026-10-09")!!
        assertEquals(69.5f, today.weight)
        assertEquals(208.5f, today.targetCarbs)

        // 昨天的快照一字不变（历史冻结）
        val yesterday = dao.getSnapshot("2026-10-08")!!
        assertEquals(70f, yesterday.weight)
        assertEquals(210f, yesterday.targetCarbs)

        // 近30天走势包含两天
        assertEquals(2, dao.getRecentWeights().size)
    }

    // ---------- 4. Top5 按次数分组排序正确 ----------

    @Test
    fun topFoods_groupByCountAndOrder() = runTest {
        val dao = db.dietDao()
        repeat(3) { dao.insert(dietRecord("2026-10-0${it + 1}", foodName = "鸡胸肉", cost = 3f)) }
        repeat(2) { dao.insert(dietRecord("2026-10-1${it + 1}", foodName = "米饭", cost = 2f)) }
        dao.insert(dietRecord("2026-10-15", foodName = "牛肉", cost = 15f))
        // 上月记录，不参与本月统计
        dao.insert(dietRecord("2026-09-20", foodName = "鸡胸肉", cost = 3f))

        val top = dao.getTopFoodsBetween("2026-10-01", "2026-10-31")
        assertEquals(3, top.size)
        assertEquals("鸡胸肉", top[0].foodName)
        assertEquals(3, top[0].eatCount)
        assertEquals(9f, top[0].totalCost)
        assertEquals("米饭", top[1].foodName)
        assertEquals(2, top[1].eatCount)
        assertEquals(4f, top[1].totalCost)
        assertEquals("牛肉", top[2].foodName)
    }

    // ---------- 5. 修改/删除模板不影响 diet_record（历史冻结红线） ----------

    @Test
    fun modifyAndDeleteTemplate_dietRecordUnaffected() = runTest {
        val foodDao = db.foodTemplateDao()
        val dietDao = db.dietDao()

        val riceId = foodDao.insert(
            FoodTemplate(
                name = "米饭", unitType = 1, weightPerServing = 150f,
                carbsPer100g = 25f, totalPrice = 10f, totalAmount = 1000f
            )
        )
        // 模拟场景 B 固化后的记录
        val recordId = dietDao.insert(
            dietRecord("2026-10-09", foodId = riceId, foodName = "米饭", carbs = 56.25f, cost = 2.25f)
        )

        // 改模板：改名、改营养、改单价
        val rice = foodDao.getById(riceId)!!
        foodDao.update(rice.copy(name = "糙米饭", carbsPer100g = 99f, totalPrice = 999f))

        val afterModify = dietDao.getAll().first { it.id == recordId }
        assertEquals("米饭", afterModify.foodName)
        assertEquals(56.25f, afterModify.intakeCarbs)
        assertEquals(2.25f, afterModify.cost)
        assertEquals(riceId, afterModify.foodId)

        // 删模板：记录仍在、数据不变
        foodDao.deleteByIds(listOf(riceId))
        assertNull(foodDao.getById(riceId))
        val afterDelete = dietDao.getAll().first { it.id == recordId }
        assertEquals("米饭", afterDelete.foodName)
        assertEquals(56.25f, afterDelete.intakeCarbs)
        assertEquals(2.25f, afterDelete.cost)
        assertFalse(dietDao.getAll().isEmpty())
    }

    // ---------- 工具 ----------

    private fun dietRecord(
        date: String,
        foodId: Long = 1L,
        foodName: String = "米饭",
        carbs: Float = 0f,
        protein: Float = 0f,
        fat: Float = 0f,
        fiber: Float = 0f,
        salt: Float = 0f,
        cost: Float = 0f
    ) = DietRecord(
        foodId = foodId,
        date = date,
        amount = 1.5f,
        unitType = 1,
        foodName = foodName,
        intakeCarbs = carbs,
        intakeProtein = protein,
        intakeFat = fat,
        intakeFiber = fiber,
        intakeSalt = salt,
        cost = cost
    )
}
