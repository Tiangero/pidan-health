package com.pidan.health.app.di

import com.pidan.health.feature.record.viewmodel.RecordViewModel
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
}
