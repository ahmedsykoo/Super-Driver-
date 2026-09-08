package com.superdriver.app.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class OcrTraceIdGeneratorTest {
    @Test
    fun nextBuildsStableTraceIdWithTimestampAndSequence() {
        val generator = OcrTraceIdGenerator(prefix = "test")

        assertEquals("test-1234-1", generator.next(nowMillis = 1234L))
        assertEquals("test-1234-2", generator.next(nowMillis = 1234L))
    }

    @Test
    fun nextReturnsUniqueValuesForConsecutiveAttempts() {
        val generator = OcrTraceIdGenerator()

        val first = generator.next(nowMillis = 42L)
        val second = generator.next(nowMillis = 42L)

        assertNotEquals(first, second)
    }
}
