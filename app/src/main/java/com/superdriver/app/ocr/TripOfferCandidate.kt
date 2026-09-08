package com.superdriver.app.ocr

data class TripOfferCandidate(
    val fareAmount: Double?,
    val pickupKm: Double?,
    val tripKm: Double?,
    val pickupMinutes: Double?,
    val tripMinutes: Double?,
    val platform: String?,
    val rawText: String,
    val confidence: Double
)

