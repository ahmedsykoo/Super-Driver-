package com.superdriver.app.capture

import com.superdriver.app.calculator.TripOfferInput

data class TripOfferSignature(
    val fareAmount: Double?,
    val pickupKm: Double?,
    val tripKm: Double?,
    val pickupMinutes: Double?,
    val tripMinutes: Double?,
    val platform: String?
) {
    companion object {
        fun fromTripOfferInput(input: TripOfferInput): TripOfferSignature {
            return TripOfferSignature(
                fareAmount = input.fareAmount?.roundForSignature(),
                pickupKm = input.pickupKm?.roundForSignature(),
                tripKm = input.tripKm?.roundForSignature(),
                pickupMinutes = input.pickupMinutes?.roundForSignature(),
                tripMinutes = input.tripMinutes?.roundForSignature(),
                platform = input.platform?.trim()?.lowercase()
            )
        }

        private fun Double.roundForSignature(): Double {
            return kotlin.math.round(this * 10.0) / 10.0
        }
    }
}
