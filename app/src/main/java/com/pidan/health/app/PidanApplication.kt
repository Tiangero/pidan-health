package com.pidan.health.app

import android.app.Application
import com.pidan.health.core.data.di.dataModule
import com.pidan.health.core.domain.di.domainModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class PidanApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@PidanApplication)
            modules(dataModule, domainModule)
        }
    }
}
