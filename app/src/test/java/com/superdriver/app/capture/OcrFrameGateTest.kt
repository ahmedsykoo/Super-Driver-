package com.superdriver.app.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrFrameGateTest {
    @Test
    fun allowsFirstFrame() {
        val gate = OcrFrameGate()

        val decision = gate.evaluate(signature(10), nowMillis = 0L)

        assertTrue(decision.shouldRunOcr)
    }

    @Test
    fun skipsSimilarFrameAfterRecentOcr() {
        val gate = OcrFrameGate()

        gate.evaluate(signature(10), nowMillis = 0L)
        val decision = gate.evaluate(signature(11), nowMillis = 1_000L)

        assertFalse(decision.shouldRunOcr)
        assertEquals("stable frame", decision.reason)
    }

    @Test
    fun skipsLowVisualChange() {
        val gate = OcrFrameGate()

        gate.evaluate(signature(10), nowMillis = 0L)
        val decision = gate.evaluate(localizedSignature(baseValue = 10, changedValue = 35), nowMillis = 4_000L)

        assertFalse(decision.shouldRunOcr)
        assertEquals("stable frame", decision.reason)
    }

    @Test
    fun allowsMediumVisualChangeWithoutWaitingForSafetyScan() {
        val gate = OcrFrameGate()

        gate.evaluate(signature(10), nowMillis = 0L)
        val decision = gate.evaluate(localizedSignature(baseValue = 10, changedValue = 50), nowMillis = 4_000L)

        assertTrue(decision.shouldRunOcr)
        assertEquals("visual candidate", decision.reason)
    }

    @Test
    fun allowsLikelyNewScreenshotDuringCooldown() {
        val gate = OcrFrameGate()

        gate.evaluate(signature(10), nowMillis = 0L)
        val decision = gate.evaluate(localizedSignature(baseValue = 10, changedValue = 80), nowMillis = 1_000L)

        assertTrue(decision.shouldRunOcr)
        assertEquals("visual candidate", decision.reason)
        assertTrue(decision.cooldownActive)
    }

    @Test
    fun canWaitForStableCandidateWhenConfigured() {
        val gate = OcrFrameGate(stableFramesRequired = 2)

        gate.evaluate(signature(10), nowMillis = 0L)
        val firstChangedFrame = gate.evaluate(signature(90), nowMillis = 11_000L)
        val stableChangedFrame = gate.evaluate(signature(91), nowMillis = 11_350L)

        assertFalse(firstChangedFrame.shouldRunOcr)
        assertTrue(stableChangedFrame.shouldRunOcr)
    }

    @Test
    fun respectsCooldownForNonStrongChanges() {
        val gate = OcrFrameGate(
            visualChangeThreshold = 10.0,
            strongVisualChangeThreshold = 80.0,
            stableFramesRequired = 1,
            postOcrCooldownMillis = 10_000L
        )

        gate.evaluate(signature(10), nowMillis = 0L)
        val decision = gate.evaluate(signature(45), nowMillis = 1_000L)

        assertFalse(decision.shouldRunOcr)
        assertEquals("cooldown", decision.reason)
    }

    @Test
    fun allowsPeriodicSafetyScan() {
        val gate = OcrFrameGate(safetyScanIntervalMillis = 5_000L)

        gate.evaluate(signature(10), nowMillis = 0L)
        val decision = gate.evaluate(signature(10), nowMillis = 5_000L)

        assertTrue(decision.shouldRunOcr)
    }

    private fun signature(value: Int): FrameSignature {
        return FrameSignature(List(120) { value })
    }

    private fun localizedSignature(
        baseValue: Int,
        changedValue: Int,
        changedCells: Int = 4
    ): FrameSignature {
        return FrameSignature(
            List(120) { index ->
                if (index < changedCells) changedValue else baseValue
            }
        )
    }
}
