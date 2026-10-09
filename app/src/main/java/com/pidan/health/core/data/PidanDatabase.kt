package com.pidan.health.core.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.pidan.health.core.data.dao.DietDao
import com.pidan.health.core.data.dao.ExerciseDao
import com.pidan.health.core.data.dao.FoodTemplateDao
import com.pidan.health.core.data.dao.SnapshotDao
import com.pidan.health.core.data.dao.UserPreferencesDao
import com.pidan.health.core.data.entity.DailySnapshot
import com.pidan.health.core.data.entity.DietRecord
import com.pidan.health.core.data.entity.ExerciseRecord
import com.pidan.health.core.data.entity.FoodTemplate
import com.pidan.health.core.data.entity.UserPreferences

/**
 * 皮蛋健康数据库。
 *
 * 红线：version 变更必须走 Migration，严禁 fallbackToDestructiveMigration()。
 */
@Database(
    entities = [
        FoodTemplate::class,
        DietRecord::class,
        DailySnapshot::class,
        ExerciseRecord::class,
        UserPreferences::class
    ],
    version = 1,
    exportSchema = false
)
abstract class PidanDatabase : RoomDatabase() {

    abstract fun foodTemplateDao(): FoodTemplateDao
    abstract fun dietDao(): DietDao
    abstract fun snapshotDao(): SnapshotDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun userPreferencesDao(): UserPreferencesDao

    companion object {
        const val DATABASE_NAME = "pidan_health.db"
    }
}
