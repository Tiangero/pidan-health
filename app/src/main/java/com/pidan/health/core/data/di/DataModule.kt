package com.pidan.health.core.data.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.pidan.health.core.data.PidanDatabase
import com.pidan.health.core.data.backup.BackupManager
import com.pidan.health.core.data.backup.BackupManagerImpl
import com.pidan.health.core.data.backup.CloudSyncManager
import com.pidan.health.core.data.backup.CloudSyncManagerImpl
import com.pidan.health.core.data.network.AndroidNetworkChecker
import com.pidan.health.core.data.network.NetworkChecker
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
import com.pidan.health.core.data.storage.CloudSyncStore
import com.pidan.health.core.data.storage.CloudSyncStoreImpl
import com.pidan.health.core.data.storage.CoverImageCompressor
import com.pidan.health.core.data.storage.CoverImageCompressorImpl
import com.pidan.health.core.data.storage.FoodImageStorage
import com.pidan.health.core.data.storage.FoodImageStorageImpl
import com.pidan.health.core.data.webdav.OkHttpWebDavClient
import com.pidan.health.core.data.webdav.WebDavClient
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

    // 封面图片存取（T5 解析展示，T6 写入压缩图）
    single<FoodImageStorage> { FoodImageStorageImpl(androidContext()) }

    // 封面强制压缩（T6：长边≤500px、WebP 优先、质量 80、≤50KB）
    single<CoverImageCompressor> { CoverImageCompressorImpl(androidContext()) }

    // 本地备份（T11：zip = 五表 JSON + 封面，导出至 Downloads，覆盖导入）
    single<BackupManager> { BackupManagerImpl(get(), get(), androidContext()) }

    // 云同步（T13：通用 WebDAV + 坚果云默认预设；DataStore 首次投入使用，存轻量配置）
    single<DataStore<Preferences>> {
        PreferenceDataStoreFactory.create {
            androidContext().preferencesDataStoreFile("cloud_sync")
        }
    }
    single<WebDavClient> { OkHttpWebDavClient() }
    single<NetworkChecker> { AndroidNetworkChecker(androidContext()) }
    single<CloudSyncStore> { CloudSyncStoreImpl(get()) }
    single<CloudSyncManager> {
        CloudSyncManagerImpl(
            webDavClient = get(),
            backupManager = get(),
            store = get(),
            networkChecker = get(),
            dateProvider = get()
        )
    }
}
