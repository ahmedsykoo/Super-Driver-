package com.superdriver.app.config

import kotlinx.coroutines.flow.Flow

interface DriverConfigRepository {
    val config: Flow<DriverConfig>

    suspend fun getConfig(): DriverConfig

    suspend fun updateConfig(config: DriverConfig)

    suspend fun resetToDefaults()
}
