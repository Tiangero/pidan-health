package com.pidan.health.core.data.di

import androidx.room.Room
import com.pidan.health.core.data.PidanDatabase
import com.pidan.health.core.data.repository.DietRepository
import com.pidan.health.core.data.repository.DietRepositoryImpl
import com.pidan.health.core.data.repository.ExerciseRepository
import com.pidan.health.core.data.repository.ExerciseRepositoryImpl
import com.pidan.health.core.data.repository.FoodRepository
import com.pidan.health.core.data.repository.FoodRepositoryImpl
import com.pidan.health.core.data.repository.SnapshotRepository
import com.pidan.health.core.data.repository.SnapshotRepositoryImpl
import com.pidan.health.core.data.repository.UserPreferencesRepository
import com.pidan.health.core.data.repository.UserPreferencesRepositoryImpl
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * 数据层 Koin 模块：数据库单例、DAO、Repository（接口绑定实现）。
 */
val dataModule: Module = module {

    single {
        Room.databaseBuilder(
            androidContext(),
            PidanDatabase::class.java,
            PidanDatabase.DATABASE_NAME
        ).build()
    }

    // DAO
    single { get<PidanDatabase>().foodTemplateDao() }
    single { get<PidanDatabase>().dietDao() }
    single { get<PidanDatabase>().snapshotDao() }
    single { get<PidanDatabase>().exerciseDao() }
    single { get<PidanDatabase>().userPreferencesDao() }

    // Repository（绑定接口而非实现，符合依赖倒置）
    single<FoodRepository> { FoodRepositoryImpl(get()) }
    single<DietRepository> { DietRepositoryImpl(get()) }
    single<SnapshotRepository> { SnapshotRepositoryImpl(get()) }
    single<ExerciseRepository> { ExerciseRepositoryImpl(get()) }
    single<UserPreferencesRepository> { UserPreferencesRepositoryImpl(get()) }
}
