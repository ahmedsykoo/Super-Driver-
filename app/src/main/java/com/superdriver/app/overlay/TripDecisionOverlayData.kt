package com.superdriver.app.overlay

import com.superdriver.app.calculator.TripDecisionResult

fun TripDecisionResult.hasCompleteOverlayData(): Boolean {
    return fareAmount != null &&
        totalKm != null &&
        totalMinutes != null &&
        egpPerKm != null &&
        egpPerHour != null
}
