package com.pidan.health.app.di

import com.pidan.health.feature.food.viewmodel.FoodEditViewModel
import com.pidan.health.feature.food.viewmodel.FoodLibraryViewModel
import com.pidan.health.feature.exercise.viewmodel.ExerciseViewModel
import com.pidan.health.feature.record.viewmodel.RecordViewModel
import com.pidan.health.feature.settings.viewmodel.SettingsViewModel
import com.pidan.health.feature.stats.viewmodel.StatsViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * UI 层 Koin 模块：各页面 ViewModel（一个 VM 只服务一个页面）。
 */
val uiModule: Module = module {

    viewModel {
        RecordViewModel(
            snapshotRepository = get(),
            dietRepository = get(),
            userPreferencesRepository = get(),
            snapshotManager = get(),
            dateProvider = get()
        )
    }

    viewModel {
        FoodLibraryViewModel(
            foodRepository = get(),
            dietRepository = get(),
            foodImageStorage = get(),
            dateProvider = get()
        )
    }

    viewModel {
        ExerciseViewModel(
            exerciseRepository = get(),
            dateProvider = get()
        )
    }

    // 新建/编辑食物页（新建与编辑复用，foodId 经 SavedStateHandle 从路由取）
    viewModel {
        FoodEditViewModel(
            foodRepository = get(),
            foodImageStorage = get(),
            coverCompressor = get(),
            ocrStrategy = get(),
            savedStateHandle = get()
        )
    }

    // 统计分析页（T10）
    viewModel {
        StatsViewModel(
            dietRepository = get(),
            snapshotRepository = get(),
            dateProvider = get()
        )
    }

    // 设置页（T11：备份导出/导入 + 自定义营养系数）
    viewModel {
        SettingsViewModel(
            userPreferencesRepository = get(),
            backupManager = get()
        )
    }
}
