package com.superdriver.app.calculator

data class TripDecisionResult(
    val decision: DriverDecision,
    val fareAmount: Double?,
    val egpPerKm: Double?,
    val egpPerHour: Double?,
    val estimatedCost: Double?,
    val estimatedNetProfit: Double?,
    val totalKm: Double?,
    val totalMinutes: Double?,
    val rejectionReasons: List<String>,
    val reviewReasons: List<String>
)

