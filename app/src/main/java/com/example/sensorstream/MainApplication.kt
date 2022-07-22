package com.example.sensorstream

import org.koin.dsl.module

val appModule = module {


    // single instance of HelloRepository
    single<SensorsDataSource> { SensorsDataSourceImpl() }

}