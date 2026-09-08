package com.superdriver.app.config

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class LocalDriverConfigRepository(
    private val fallbackConfig: DriverConfig = DriverConfig.default()
) : DriverConfigRepository {
    private val configState = MutableStateFlow(fallbackConfig)

    override val config: Flow<DriverConfig> = configState

    override suspend fun getConfig(): DriverConfig = configState.value

    override suspend fun updateConfig(config: DriverConfig) {
        configState.value = config
    }

    override suspend fun resetToDefaults() {
        configState.value = fallbackConfig
    }
}
