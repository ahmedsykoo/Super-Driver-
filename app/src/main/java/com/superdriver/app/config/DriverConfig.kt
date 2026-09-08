package com.superdriver.app.config

import com.superdriver.app.zones.AvoidZoneRule

data class DriverConfig(
    val minEgpPerKm: Double,
    val minEgpPerHour: Double,
    val minNetProfit: Double,
    val costPerKm: Double,
    val costPerMinute: Double,
    val reviewTolerancePercent: Double,
    val rejectIfUnknownFare: Boolean,
    val rejectIfUnknownDistance: Boolean,
    val rejectIfAvoidZoneDetected: Boolean,
    val avoidZones: List<AvoidZoneRule>
) {
    companion object {
        fun default(): DriverConfig = DriverConfig(
            minEgpPerKm = 8.0,
            minEgpPerHour = 250.0,
            minNetProfit = 50.0,
            costPerKm = 3.0,
            costPerMinute = 1.0,
            reviewTolerancePercent = 10.0,
            rejectIfUnknownFare = true,
            rejectIfUnknownDistance = false,
            rejectIfAvoidZoneDetected = true,
            avoidZones = emptyList()
        )
    }
}
