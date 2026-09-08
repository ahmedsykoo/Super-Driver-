package com.superdriver.app.overlay

import com.superdriver.app.calculator.DriverDecision
import com.superdriver.app.calculator.TripDecisionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayCardStateTest {
    @Test
    fun fromDecisionResultShowsPlaceholdersForNullValues() {
        val state = OverlayCardState.fromDecisionResult(
            TripDecisionResult(
                decision = DriverDecision.REVIEW,
                fareAmount = null,
                egpPerKm = null,
                egpPerHour = null,
                estimatedCost = null,
                estimatedNetProfit = null,
                totalKm = null,
                totalMinutes = null,
                rejectionReasons = emptyList(),
                reviewReasons = listOf("بيانات غير مكتملة")
            )
        )

        assertEquals("-", state.fareText)
        assertEquals("-", state.egpPerKmText)
        assertEquals("-", state.egpPerHourText)
        assertEquals("- min", state.totalTimeText)
        assertEquals("- km", state.totalKmText)
        assertEquals(OverlayVisualState.INCOMPLETE_DATA, state.visualState)
        assertEquals("بيانات غير مكتملة", state.titleText)
    }

    @Test
    fun fromDecisionResultUsesIncompleteVisualStateWhenFareIsMissing() {
        val state = OverlayCardState.fromDecisionResult(
            decisionResult(
                fareAmount = null,
                totalKm = null,
                totalMinutes = null,
                egpPerKm = null,
                egpPerHour = null
            )
        )

        assertEquals(DriverDecision.REJECT, state.decision)
        assertEquals(OverlayVisualState.INCOMPLETE_DATA, state.visualState)
        assertEquals("بيانات غير مكتملة", state.titleText)
        assertEquals("الأجرة غير مقروءة", state.shortReason)
    }

    @Test
    fun fromDecisionResultUsesDistanceMissingReasonBeforeGenericIncompleteReason() {
        val state = OverlayCardState.fromDecisionResult(
            decisionResult(
                fareAmount = 7124.0,
                totalKm = null,
                totalMinutes = 30.0,
                egpPerKm = null,
                egpPerHour = 14248.0
            )
        )

        assertEquals(OverlayVisualState.INCOMPLETE_DATA, state.visualState)
        assertEquals("المسافة ناقصة", state.shortReason)
    }

    @Test
    fun fromDecisionResultUsesTimeMissingReasonWhenOnlyTimeDataIsMissing() {
        val state = OverlayCardState.fromDecisionResult(
            decisionResult(
                fareAmount = 7124.0,
                totalKm = 14.7,
                totalMinutes = null,
                egpPerKm = 484.6,
                egpPerHour = null
            )
        )

        assertEquals(OverlayVisualState.INCOMPLETE_DATA, state.visualState)
        assertEquals("الوقت ناقص", state.shortReason)
    }

    @Test
    fun fromDecisionResultKeepsDecisionVisualStateForCompleteData() {
        val state = OverlayCardState.fromDecisionResult(
            decisionResult(
                fareAmount = 7124.0,
                totalKm = 14.7,
                totalMinutes = 30.0,
                egpPerKm = 484.6,
                egpPerHour = 14248.0
            )
        )

        assertEquals(OverlayVisualState.DECISION, state.visualState)
        assertEquals("رفض", state.titleText)
        assertEquals(DriverDecision.REJECT, state.decision)
        assertEquals(null, state.shortReason)
    }

    @Test
    fun completeOverlayDataRequiresAllDisplayedMetrics() {
        assertTrue(
            decisionResult(
                fareAmount = 7124.0,
                totalKm = 14.7,
                totalMinutes = 30.0,
                egpPerKm = 484.6,
                egpPerHour = 14248.0
            ).hasCompleteOverlayData()
        )
        assertFalse(
            decisionResult(
                fareAmount = 7124.0,
                totalKm = null,
                totalMinutes = 30.0,
                egpPerKm = null,
                egpPerHour = 14248.0
            ).hasCompleteOverlayData()
        )
    }

    private fun decisionResult(
        fareAmount: Double?,
        totalKm: Double?,
        totalMinutes: Double?,
        egpPerKm: Double?,
        egpPerHour: Double?
    ): TripDecisionResult {
        return TripDecisionResult(
            decision = DriverDecision.REJECT,
            fareAmount = fareAmount,
            egpPerKm = egpPerKm,
            egpPerHour = egpPerHour,
            estimatedCost = null,
            estimatedNetProfit = null,
            totalKm = totalKm,
            totalMinutes = totalMinutes,
            rejectionReasons = emptyList(),
            reviewReasons = emptyList()
        )
    }
}
