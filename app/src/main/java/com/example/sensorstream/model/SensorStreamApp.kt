package com.example.sensorstream.model

import android.app.Application
import com.example.sensorstream.*
import com.example.sensorstream.view.SensorsReadoutsActivity
import com.example.sensorstream.viewmodel.SensorsReadoutsViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.context.GlobalContext.startKoin
import org.koin.dsl.module

val DEFAULT_STREAM_MODE = StreamMode.ON_TOUCH

val appModule = module {
    viewModel { params ->
            SensorsReadoutsViewModel(params.get(0)) }
    single<SensorStreamingManager> { params ->
        SensorStreamingManager(params.get(0), params.get(1))
    }
    single<SensorsDataSource> { params -> SensorsDataSource(params.get()) }
    single<SensorDataSender> { params -> SocketDataSender(params.get(0)) }
    single<WebsocketConnection> { params ->
        WebsocketConnection(
            BuildConfig.WEBSOCKET_SERVER,
            BuildConfig.WEBSOCKET_SERVER_PORT, params.get(0)
        )
    }
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