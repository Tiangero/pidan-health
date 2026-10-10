package com.pidan.health.core.domain.di

import com.pidan.health.core.common.time.DateProvider
import com.pidan.health.core.common.time.SystemDateProvider
import com.pidan.health.core.domain.SnapshotManager
import com.pidan.health.core.domain.SnapshotManagerImpl
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * domain 层 Koin 模块：快照管理（UseCase）、日期供给。
 * 计算器/判定器为无状态 object，直接引用，不经容器。
 */
val domainModule: Module = module {

    single<SnapshotManager> { SnapshotManagerImpl(get(), get(), get()) }

    single<DateProvider> { SystemDateProvider }
}
