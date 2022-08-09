package com.example.sensorstream.model

import android.app.Application
import com.example.sensorstream.*
import com.example.sensorstream.viewmodel.SensorsReadoutsViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.GlobalContext.startKoin
import org.koin.dsl.module

val appModule = module {
    single<SensorsReadoutsViewModel> { params -> SensorsReadoutsViewModel(params.get()) }
    single<SensorStreamingManager> { params -> SensorStreamingManager(
        params.get(0), params.get(1), params.get(2))
    }
    single<SensorsDataSource> { params -> SensorsDataSource(params.get()) }
    single<SensorDataSender> { params -> SocketDataSender(params.get(0), params.get(1),
        params.get(2), params.get(3)) }
    single<WebsocketConnection> { params -> WebsocketConnection(params.get(0), params.get(1),
        params.get(2), params[3]
    )}
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