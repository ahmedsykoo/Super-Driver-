package com.superdriver.app.capture

import com.superdriver.app.calculator.DriverDecision
import com.superdriver.app.calculator.TripDecisionResult
import com.superdriver.app.calculator.TripOfferInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OcrCandidateBufferTest {
    @Test
    fun addSelectsCompleteCandidateImmediately() {
        val buffer = OcrCandidateBuffer(retentionMillis = 700L)
        val snapshot = snapshot(
            traceId = "trace-complete",
            createdAtMillis = 100L,
            input = input(
                fareAmount = 5000.0,
                pickupKm = 1.0,
                tripKm = 7.0,
                pickupMinutes = 4.0,
                tripMinutes = 25.0,
                platform = "uber"
            ),
            result = result(
                fareAmount = 5000.0,
                totalKm = 8.0,
                totalMinutes = 29.0,
                egpPerKm = 625.0,
                egpPerHour = 10344.0
            )
        )

        val update = buffer.add(snapshot, nowMillis = 100L)

        assertEquals("trace-complete", update.selected?.traceId)
        assertEquals("complete immediate", update.selectedReason)
    }

    @Test
    fun selectReadyWaitsUntilRetentionWindowElapsesForIncompleteCandidate() {
        val buffer = OcrCandidateBuffer(retentionMillis = 700L)
        buffer.add(incompleteSnapshot(traceId = "trace-incomplete", createdAtMillis = 100L), nowMillis = 100L)

        assertNull(buffer.selectReady(nowMillis = 799L).selected)

        val selection = buffer.selectReady(nowMillis = 800L)

        assertEquals("trace-incomplete", selection.selected?.traceId)
        assertEquals("buffer window elapsed", selection.selectedReason)
    }

    @Test
    fun selectReadyChoosesCandidateWithMoreCompleteFields() {
        val buffer = OcrCandidateBuffer(retentionMillis = 700L)
        buffer.add(
            incompleteSnapshot(
                traceId = "trace-weak",
                createdAtMillis = 100L,
                input = input(fareAmount = 5000.0)
            ),
            nowMillis = 100L
        )
        buffer.add(
            incompleteSnapshot(
                traceId = "trace-better",
                createdAtMillis = 200L,
                input = input(fareAmount = 5000.0, pickupKm = 1.0, tripKm = 7.0, pickupMinutes = 4.0)
            ),
            nowMillis = 200L
        )

        val selection = buffer.selectReady(nowMillis = 800L)

        assertEquals("trace-better", selection.selected?.traceId)
    }

    @Test
    fun selectReadyPrefersMostRecentCandidateWhenFieldCountTies() {
        val buffer = OcrCandidateBuffer(retentionMillis = 700L)
        buffer.add(
            incompleteSnapshot(
                traceId = "trace-old",
                createdAtMillis = 100L,
                input = input(fareAmount = 5000.0, pickupKm = 1.0)
            ),
            nowMillis = 100L
        )
        buffer.add(
            incompleteSnapshot(
                traceId = "trace-new",
                createdAtMillis = 200L,
                input = input(fareAmount = 5000.0, tripKm = 7.0)
            ),
            nowMillis = 200L
        )

        val selection = buffer.selectReady(nowMillis = 800L)

        assertEquals("trace-new", selection.selected?.traceId)
    }

    @Test
    fun addKeepsAtMostThreeCandidates() {
        val buffer = OcrCandidateBuffer(maxCandidates = 3, retentionMillis = 700L)
        buffer.add(incompleteSnapshot(traceId = "trace-1", createdAtMillis = 100L), nowMillis = 100L)
        buffer.add(incompleteSnapshot(traceId = "trace-2", createdAtMillis = 200L), nowMillis = 200L)
        buffer.add(incompleteSnapshot(traceId = "trace-3", createdAtMillis = 300L), nowMillis = 300L)

        val update = buffer.add(incompleteSnapshot(traceId = "trace-4", createdAtMillis = 400L), nowMillis = 400L)

        assertEquals(listOf("trace-1"), update.expiredTraceIds)
    }

    @Test
    fun addExpiresCandidatesOutsideRetentionWindow() {
        val buffer = OcrCandidateBuffer(retentionMillis = 700L)
        buffer.add(incompleteSnapshot(traceId = "trace-old", createdAtMillis = 100L), nowMillis = 100L)

        val update = buffer.add(incompleteSnapshot(traceId = "trace-new", createdAtMillis = 900L), nowMillis = 900L)

        assertEquals(listOf("trace-old"), update.expiredTraceIds)
    }

    @Test
    fun addReportsReplacedCandidateWhenNewSnapshotBecomesBest() {
        val buffer = OcrCandidateBuffer(retentionMillis = 700L)
        buffer.add(
            incompleteSnapshot(
                traceId = "trace-weak",
                createdAtMillis = 100L,
                input = input(fareAmount = 5000.0)
            ),
            nowMillis = 100L
        )

        val update = buffer.add(
            incompleteSnapshot(
                traceId = "trace-better",
                createdAtMillis = 200L,
                input = input(fareAmount = 5000.0, pickupKm = 1.0, tripKm = 7.0)
            ),
            nowMillis = 200L
        )

        assertEquals("trace-weak", update.replacedTraceId)
    }

    @Test
    fun completeFieldCountIncludesFarePickupTripTimeAndPlatform() {
        val count = input(
            fareAmount = 5000.0,
            pickupKm = 1.0,
            tripKm = 7.0,
            pickupMinutes = 4.0,
            tripMinutes = null,
            platform = "uber"
        ).completeFieldCount()

        assertEquals(5, count)
    }

    private fun incompleteSnapshot(
        traceId: String,
        createdAtMillis: Long,
        input: TripOfferInput = input(fareAmount = 5000.0)
    ): OcrCandidateSnapshot {
        return snapshot(
            traceId = traceId,
            createdAtMillis = createdAtMillis,
            input = input,
            result = result(
                fareAmount = input.fareAmount,
                totalKm = null,
                totalMinutes = null,
                egpPerKm = null,
                egpPerHour = null
            )
        )
    }

    private fun snapshot(
        traceId: String,
        createdAtMillis: Long,
        input: TripOfferInput,
        result: TripDecisionResult
    ): OcrCandidateSnapshot {
        return OcrCandidateSnapshot(
            traceId = traceId,
            createdAtMillis = createdAtMillis,
            input = input,
            result = result
        )
    }

    private fun input(
        fareAmount: Double? = null,
        pickupKm: Double? = null,
        tripKm: Double? = null,
        pickupMinutes: Double? = null,
        tripMinutes: Double? = null,
        platform: String? = null
    ): TripOfferInput {
        return TripOfferInput(
            fareAmount = fareAmount,
            pickupKm = pickupKm,
            tripKm = tripKm,
            pickupMinutes = pickupMinutes,
            tripMinutes = tripMinutes,
            platform = platform,
            rawText = "raw text"
        )
    }

    private fun result(
        fareAmount: Double?,
        totalKm: Double?,
        totalMinutes: Double?,
        egpPerKm: Double?,
        egpPerHour: Double?
    ): TripDecisionResult {
        return TripDecisionResult(
            decision = DriverDecision.REVIEW,
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
