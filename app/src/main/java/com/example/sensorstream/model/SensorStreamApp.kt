package com.example.sensorstream.model

import android.app.Application
import com.example.sensorstream.*
import com.example.sensorstream.viewmodel.SensorsReadoutsViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.GlobalContext.startKoin
import org.koin.dsl.module

val DEFAULT_STREAM_MODE = StreamMode.ON_TOUCH

val appModule = module {
    single<SensorsReadoutsViewModel> { params -> SensorsReadoutsViewModel(params.get()) }
    single<SensorStreamingManager> { params -> SensorStreamingManager(
        params.get(0), streamMode = DEFAULT_STREAM_MODE, params.get(1))
    }
    single<SensorsDataSource> { params -> SensorsDataSource(params.get()) }
    single<SensorDataSender> { params -> SocketDataSender(
        BuildConfig.WEBSOCKET_SERVER, BuildConfig.WEBSOCKET_SERVER_PORT,
        params.get(0)) }
    single<WebsocketConnection> { params -> WebsocketConnection(BuildConfig.WEBSOCKET_SERVER,
        BuildConfig.WEBSOCKET_SERVER_PORT, params.get(0), params.get(1))}
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