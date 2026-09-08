package com.superdriver.app.capture

import com.superdriver.app.calculator.TripOfferInput

class TripOfferDetectionState {
    private var lastShownSignature: TripOfferSignature? = null

    fun shouldShowOverlay(input: TripOfferInput): Boolean {
        val signature = TripOfferSignature.fromTripOfferInput(input)
        return signature != lastShownSignature
    }

    fun markOverlayShown(input: TripOfferInput) {
        lastShownSignature = TripOfferSignature.fromTripOfferInput(input)
    }

    fun reset() {
        lastShownSignature = null
    }
}
