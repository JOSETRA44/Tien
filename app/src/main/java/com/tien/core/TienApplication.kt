package com.tien.core

import android.app.Application
import com.tien.core.di.AppContainer
import com.tien.core.di.DefaultAppContainer

/**
 * Application entry point. Owns the object graph so repositories — and the
 * single native database connection behind them — live for the whole process
 * rather than being rebuilt per screen.
 */
class TienApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(applicationContext)
    }

    override fun onTerminate() {
        // Only ever called on emulators, so it is a courtesy rather than the
        // real cleanup path: the native connection is reclaimed with the
        // process either way.
        container.shutdown()
        super.onTerminate()
    }
}
