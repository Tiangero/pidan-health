package com.pidan.health.core.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.pidan.health.core.data.entity.FoodTemplate
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodTemplateDao {

    /** 食物库列表（按更新时间倒序），UI 响应式观察 */
    @Query("SELECT * FROM food_template ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<FoodTemplate>>

    @Query("SELECT * FROM food_template WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): FoodTemplate?

    /** 单条响应式观察（详情页：编辑保存返回后自动刷新） */
    @Query("SELECT * FROM food_template WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<FoodTemplate?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(food: FoodTemplate): Long

    @Update
    suspend fun update(food: FoodTemplate)

    @Delete
    suspend fun delete(food: FoodTemplate)

    /** 批量删除（T7 管理模式） */
    @Query("DELETE FROM food_template WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    /** 备份导入时清空重建（T11） */
    @Query("DELETE FROM food_template")
    suspend fun deleteAll()

    /** 全量读取（备份导出 T11 用），按 id 升序保证导入重插顺序稳定 */
    @Query("SELECT * FROM food_template ORDER BY id ASC")
    suspend fun getAll(): List<FoodTemplate>

    /** 备份导入批量重插（T11，主键 id 随备份保留） */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(foods: List<FoodTemplate>)
}
