package com.example.sensorstream.model

import android.app.Application
import com.example.sensorstream.*
import com.example.sensorstream.viewmodel.SensorsReadoutsViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.context.GlobalContext.startKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module

val DEFAULT_STREAM_MODE = StreamMode.ON_TOUCH
val initialState = MutableStateFlow( SensorsViewState(
        ConnectionStatus.NOT_ESTABLISHED, TransmissionState.OFF,
        SensorsData(), StartButtonState.INACTIVE, DEFAULT_STREAM_MODE))

val appModule = module {
    viewModel { params ->
            SensorsReadoutsViewModel(params.get(0)) }
    single<SensorStreamingManager> { params ->
        SensorStreamingManager(
            params.get(0), params.get(1), params.get(2), params.get(3), params.get(4))
    }
    single<SensorsDataSource> { params -> SensorsDataSource(
        params.get(0), params.get(1), params.get(2), params.get(3)) }
    single<SensorDataManipulator> { params -> SensorDataManipulator(params.get(0), params.get(1)) }
    single<SensorDataSender> { params -> SocketDataSender(
        params.get(0), params.get(1), params.get(2), params.get(3), params.get(4)) }
    single<WebsocketConnection> { params ->
        WebsocketConnection(
            BuildConfig.WEBSOCKET_SERVER,
            BuildConfig.WEBSOCKET_SERVER_PORT, BuildConfig.WEBSOCKET_PATH, params.get(0)
        )
    }
    single(named("initialState")){ initialState }
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