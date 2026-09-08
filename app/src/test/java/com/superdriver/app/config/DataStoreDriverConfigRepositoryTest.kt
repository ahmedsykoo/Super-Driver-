package com.superdriver.app.config

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.superdriver.app.calculator.DriverDecision
import com.superdriver.app.calculator.DriverProfitCalculator
import com.superdriver.app.calculator.TripOfferInput
import com.superdriver.app.zones.AvoidZonePolicy
import com.superdriver.app.zones.AvoidZoneRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.util.UUID

class DataStoreDriverConfigRepositoryTest {
    private val calculator = DriverProfitCalculator()

    @Test
    fun returnsDefaultConfigOnFirstRead() = runBlocking {
        val repository = createRepository()

        assertEquals(DriverConfig.default(), repository.getConfig())
        assertEquals(DriverConfig.default(), repository.config.first())
    }

    @Test
    fun persistsConfigChanges() = runBlocking {
        val repository = createRepository()
        val updatedConfig = DriverConfig.default().copy(
            minEgpPerKm = 750.0,
            minEgpPerHour = 8000.0,
            costPerKm = 310.0,
            costPerMinute = 35.0,
            reviewTolerancePercent = 12.0,
            avoidZones = listOf(
                AvoidZoneRule(
                    id = "zone-1",
                    name = "Zona prueba",
                    keywords = listOf("Retiro", "Centro"),
                    policy = AvoidZonePolicy.REVIEW,
                    activeFrom = "06:00",
                    activeTo = "22:00",
                    enabled = true
                )
            )
        )

        repository.updateConfig(updatedConfig)

        assertEquals(updatedConfig, repository.getConfig())
        assertEquals(updatedConfig, repository.config.first())
    }

    @Test
    fun resetRestoresDefaults() = runBlocking {
        val repository = createRepository()
        repository.updateConfig(
            DriverConfig.default().copy(
                minEgpPerKm = 900.0
            )
        )

        repository.resetToDefaults()

        assertEquals(DriverConfig.default(), repository.getConfig())
    }

    @Test
    fun supportsMultipleWrites() = runBlocking {
        val repository = createRepository()

        repository.updateConfig(DriverConfig.default().copy(minEgpPerKm = 700.0))
        repository.updateConfig(DriverConfig.default().copy(minEgpPerKm = 800.0))

        assertEquals(800.0, repository.getConfig().minEgpPerKm, 0.001)
    }

    @Test
    fun calculationStillAcceptsReferenceTripWithPersistedDefaults() = runBlocking {
        val repository = createRepository()
        val result = calculator.calculate(
            input = TripOfferInput(
                fareAmount = 5127.0,
                pickupKm = 1.0,
                tripKm = 7.0,
                pickupMinutes = 5.0,
                tripMinutes = 36.0,
                platform = "uber"
            ),
            config = repository.getConfig()
        )

        assertEquals(DriverDecision.ACCEPT, result.decision)
    }

    private fun createRepository(): DataStoreDriverConfigRepository {
        val directory = File("build/tmp/datastore-test/${UUID.randomUUID()}").apply {
            mkdirs()
        }
        val file = File(directory, "driver-config.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            produceFile = { file }
        )

        return DataStoreDriverConfigRepository(dataStore = dataStore)
    }
}
