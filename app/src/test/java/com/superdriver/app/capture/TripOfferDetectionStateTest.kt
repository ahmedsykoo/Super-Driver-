package com.superdriver.app.capture

import com.superdriver.app.calculator.TripOfferInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripOfferDetectionStateTest {
    @Test
    fun signatureDoesNotChangeForSameParsedOffer() {
        val first = TripOfferSignature.fromTripOfferInput(offerInput())
        val second = TripOfferSignature.fromTripOfferInput(
            offerInput(rawText = "UberX\ntexto OCR con ruido distinto")
        )

        assertEquals(first, second)
    }

    @Test
    fun signatureChangesWhenFareChanges() {
        val first = TripOfferSignature.fromTripOfferInput(offerInput(fareAmount = 5127.0))
        val second = TripOfferSignature.fromTripOfferInput(offerInput(fareAmount = 6127.0))

        assertNotEquals(first, second)
    }

    @Test
    fun signatureChangesWhenPickupDistanceChanges() {
        val first = TripOfferSignature.fromTripOfferInput(offerInput(pickupKm = 1.0))
        val second = TripOfferSignature.fromTripOfferInput(offerInput(pickupKm = 1.5))

        assertNotEquals(first, second)
    }

    @Test
    fun signatureChangesWhenTripDistanceChanges() {
        val first = TripOfferSignature.fromTripOfferInput(offerInput(tripKm = 7.0))
        val second = TripOfferSignature.fromTripOfferInput(offerInput(tripKm = 8.0))

        assertNotEquals(first, second)
    }

    @Test
    fun signatureChangesWhenMinutesChange() {
        val first = TripOfferSignature.fromTripOfferInput(offerInput(tripMinutes = 36.0))
        val second = TripOfferSignature.fromTripOfferInput(offerInput(tripMinutes = 40.0))

        assertNotEquals(first, second)
    }

    @Test
    fun signatureChangesWhenPlatformChanges() {
        val first = TripOfferSignature.fromTripOfferInput(offerInput(platform = "uber"))
        val second = TripOfferSignature.fromTripOfferInput(offerInput(platform = "didi"))

        assertNotEquals(first, second)
    }

    @Test
    fun detectionStateAllowsOnlyFirstOverlayAfterMarkingShown() {
        val state = TripOfferDetectionState()
        val input = offerInput()

        assertTrue(state.shouldShowOverlay(input))
        state.markOverlayShown(input)
        assertFalse(state.shouldShowOverlay(input.copy(rawText = "ruido OCR distinto")))
    }

    @Test
    fun detectionStateAllowsOverlayAfterOfferChanges() {
        val state = TripOfferDetectionState()
        val input = offerInput()

        assertTrue(state.shouldShowOverlay(input))
        state.markOverlayShown(input)
        assertTrue(state.shouldShowOverlay(input.copy(fareAmount = 6127.0)))
    }

    @Test
    fun detectionStateAllowsSameOfferAfterReset() {
        val state = TripOfferDetectionState()
        val input = offerInput()

        assertTrue(state.shouldShowOverlay(input))
        state.markOverlayShown(input)
        state.reset()
        assertTrue(state.shouldShowOverlay(input))
    }

    private fun offerInput(
        fareAmount: Double = 5127.0,
        pickupKm: Double = 1.0,
        tripKm: Double = 7.0,
        pickupMinutes: Double = 5.0,
        tripMinutes: Double = 36.0,
        platform: String? = "uber",
        rawText: String? = "Uber EGP 5.127 5 min 1 km 36 min 7 km"
    ): TripOfferInput {
        return TripOfferInput(
            fareAmount = fareAmount,
            pickupKm = pickupKm,
            tripKm = tripKm,
            pickupMinutes = pickupMinutes,
            tripMinutes = tripMinutes,
            platform = platform,
            rawText = rawText
        )
    }
}
