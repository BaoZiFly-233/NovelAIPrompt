package com.novelstudio.app.android

import android.app.Application
import com.novelstudio.di.appModule
import org.koin.core.context.startKoin

class StudioApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            modules(appModule(applicationContext))
        }
    }
}
