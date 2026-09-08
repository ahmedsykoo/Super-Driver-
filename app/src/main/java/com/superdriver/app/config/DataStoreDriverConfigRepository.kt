package com.superdriver.app.config

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.superdriver.app.zones.AvoidZonePolicy
import com.superdriver.app.zones.AvoidZoneRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private const val DRIVER_CONFIG_DATA_STORE_NAME = "driver_config"

private val Context.driverConfigDataStore: DataStore<Preferences> by preferencesDataStore(
    name = DRIVER_CONFIG_DATA_STORE_NAME
)

class DataStoreDriverConfigRepository(
    context: Context? = null,
    private val dataStore: DataStore<Preferences>? = null,
    private val fallbackConfig: DriverConfig = DriverConfig.default()
) : DriverConfigRepository {
    private val resolvedDataStore: DataStore<Preferences> by lazy {
        dataStore ?: requireNotNull(context?.applicationContext) {
            "DataStoreDriverConfigRepository requires either a Context or a DataStore."
        }.driverConfigDataStore
    }

    override val config: Flow<DriverConfig> = resolvedDataStore.data
        .map { preferences ->
            runCatching { preferences.toDriverConfig() }.getOrDefault(fallbackConfig)
        }
        .catch { error ->
            emit(fallbackConfig)
        }

    override suspend fun getConfig(): DriverConfig = runCatching { config.first() }.getOrDefault(fallbackConfig)

    override suspend fun updateConfig(config: DriverConfig) {
        runCatching {
            resolvedDataStore.edit { preferences ->
                preferences.clear()
                preferences.writeConfig(config)
                preferences[initializedKey] = true
            }
        }
    }

    override suspend fun resetToDefaults() {
        updateConfig(fallbackConfig)
    }

    private fun Preferences.toDriverConfig(): DriverConfig {
        val isInitialized = this[initializedKey] == true
        val storedAvoidZones = this[avoidZonesKey].orEmpty().decodeAvoidZones()

        return fallbackConfig.copy(
            minEgpPerKm = this[minEgpPerKmKey] ?: fallbackConfig.minEgpPerKm,
            minEgpPerHour = this[minEgpPerHourKey] ?: fallbackConfig.minEgpPerHour,
            minNetProfit = this[minNetProfitKey] ?: fallbackConfig.minNetProfit,
            costPerKm = this[costPerKmKey] ?: fallbackConfig.costPerKm,
            costPerMinute = this[costPerMinuteKey] ?: fallbackConfig.costPerMinute,
            reviewTolerancePercent = this[reviewTolerancePercentKey] ?: fallbackConfig.reviewTolerancePercent,
            rejectIfUnknownFare = this[rejectIfUnknownFareKey] ?: fallbackConfig.rejectIfUnknownFare,
            rejectIfUnknownDistance = this[rejectIfUnknownDistanceKey] ?: fallbackConfig.rejectIfUnknownDistance,
            rejectIfAvoidZoneDetected = this[rejectIfAvoidZoneDetectedKey]
                ?: fallbackConfig.rejectIfAvoidZoneDetected,
            avoidZones = if (isInitialized) storedAvoidZones else fallbackConfig.avoidZones
        )
    }

    private fun MutablePreferences.writeConfig(config: DriverConfig) {
        this[minEgpPerKmKey] = config.minEgpPerKm
        this[minEgpPerHourKey] = config.minEgpPerHour
        this[minNetProfitKey] = config.minNetProfit
        this[costPerKmKey] = config.costPerKm
        this[costPerMinuteKey] = config.costPerMinute
        this[reviewTolerancePercentKey] = config.reviewTolerancePercent
        this[rejectIfUnknownFareKey] = config.rejectIfUnknownFare
        this[rejectIfUnknownDistanceKey] = config.rejectIfUnknownDistance
        this[rejectIfAvoidZoneDetectedKey] = config.rejectIfAvoidZoneDetected
        this[avoidZonesKey] = config.avoidZones.encodeAvoidZones()
    }

    private fun Set<String>.decodeAvoidZones(): List<AvoidZoneRule> {
        return asSequence()
            .mapNotNull { record ->
                val parts = record.split(RECORD_SEPARATOR)
                if (parts.size != 7) {
                    return@mapNotNull null
                }

                val policy = runCatching { AvoidZonePolicy.valueOf(parts[2]) }
                    .getOrDefault(AvoidZonePolicy.REJECT)
                val keywords = parts[6]
                    .takeIf { it.isNotBlank() }
                    ?.split(KEYWORD_SEPARATOR)
                    .orEmpty()
                    .mapNotNull { decodeToken(it) }

                AvoidZoneRule(
                    id = decodeToken(parts[0]).orEmpty(),
                    name = decodeToken(parts[1]).orEmpty(),
                    keywords = keywords,
                    policy = policy,
                    activeFrom = decodeToken(parts[3]).orEmpty().takeIf { it.isNotBlank() },
                    activeTo = decodeToken(parts[4]).orEmpty().takeIf { it.isNotBlank() },
                    enabled = parts[5].toBooleanStrictOrNull() ?: true
                )
            }
            .sortedBy { it.id }
            .toList()
    }

    private fun List<AvoidZoneRule>.encodeAvoidZones(): Set<String> {
        return asSequence()
            .sortedBy { it.id }
            .map { rule ->
                listOf(
                    encodeToken(rule.id),
                    encodeToken(rule.name),
                    rule.policy.name,
                    encodeToken(rule.activeFrom.orEmpty()),
                    encodeToken(rule.activeTo.orEmpty()),
                    rule.enabled.toString(),
                    rule.keywords.joinToString(KEYWORD_SEPARATOR) { encodeToken(it) }
                ).joinToString(RECORD_SEPARATOR)
            }
            .toSet()
    }

    private fun encodeToken(value: String): String {
        return runCatching {
            URLEncoder.encode(value, StandardCharsets.UTF_8.name())
        }.getOrDefault(value)
    }

    private fun decodeToken(value: String): String? {
        return runCatching {
            URLDecoder.decode(value, StandardCharsets.UTF_8.name())
        }.getOrNull()
    }

    companion object {
        private val initializedKey = booleanPreferencesKey("driver_config_initialized")
        private val minEgpPerKmKey = doublePreferencesKey("min_egp_per_km")
        private val minEgpPerHourKey = doublePreferencesKey("min_egp_per_hour")
        private val minNetProfitKey = doublePreferencesKey("min_net_profit")
        private val costPerKmKey = doublePreferencesKey("cost_per_km")
        private val costPerMinuteKey = doublePreferencesKey("cost_per_minute")
        private val reviewTolerancePercentKey = doublePreferencesKey("review_tolerance_percent")
        private val rejectIfUnknownFareKey = booleanPreferencesKey("reject_if_unknown_fare")
        private val rejectIfUnknownDistanceKey = booleanPreferencesKey("reject_if_unknown_distance")
        private val rejectIfAvoidZoneDetectedKey = booleanPreferencesKey("reject_if_avoid_zone_detected")
        private val avoidZonesKey = stringSetPreferencesKey("avoid_zones")

        private const val RECORD_SEPARATOR = "|"
        private const val KEYWORD_SEPARATOR = ","
    }
}
