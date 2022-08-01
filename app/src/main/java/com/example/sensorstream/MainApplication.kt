package com.example.sensorstream

import android.app.Application
import android.hardware.SensorManager
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.GlobalContext.startKoin
import org.koin.dsl.module

val appModule = module {
    single<SensorsReadoutsVM> { params -> SensorsReadoutsVM (sensorManager = params.get(), streamMode = params.get()) }
    single<SensorsDataSource> { params -> SensorsDataSourceImpl(sensorManager = params.get()) }
    single<SensorDataSender> { params -> SocketDataSender(host = params.get(), port = params.get(), delay = params.get(), dataFlow = params.get())}
}

class SensorStreamApp : Application(){
    override fun onCreate() {
        super.onCreate()
        // Start Koin
        startKoin{
            androidLogger()
            androidContext(this@SensorStreamApp)
            modules(appModule)
        }
    }
}