package com.superdriver.app.zones

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AvoidZoneMatcherTest {
    private val matcher = AvoidZoneMatcher()

    @Test
    fun detectsExactKeyword() {
        val result = matcher.findMatch(
            rawText = "Destino: Retiro",
            rules = listOf(rule(keywords = listOf("Retiro")))
        )

        assertNotNull(result)
        assertEquals("Retiro", result?.matchedKeyword)
    }

    @Test
    fun detectsKeywordIgnoringCase() {
        val result = matcher.findMatch(
            rawText = "destino: retiro",
            rules = listOf(rule(keywords = listOf("RETIRO")))
        )

        assertNotNull(result)
    }

    @Test
    fun detectsKeywordIgnoringAccents() {
        val result = matcher.findMatch(
            rawText = "Destino: Jose C Paz",
            rules = listOf(rule(keywords = listOf("José C Paz")))
        )

        assertNotNull(result)
    }

    @Test
    fun doesNotDetectDisabledZone() {
        val result = matcher.findMatch(
            rawText = "Destino: Retiro",
            rules = listOf(rule(keywords = listOf("Retiro"), enabled = false))
        )

        assertNull(result)
    }

    @Test
    fun returnsRejectPolicy() {
        val result = matcher.findMatch(
            rawText = "Destino: Retiro",
            rules = listOf(rule(keywords = listOf("Retiro"), policy = AvoidZonePolicy.REJECT))
        )

        assertEquals(AvoidZonePolicy.REJECT, result?.policy)
    }

    @Test
    fun returnsReviewPolicy() {
        val result = matcher.findMatch(
            rawText = "Destino: Centro",
            rules = listOf(rule(keywords = listOf("Centro"), policy = AvoidZonePolicy.REVIEW))
        )

        assertEquals(AvoidZonePolicy.REVIEW, result?.policy)
    }

    private fun rule(
        keywords: List<String>,
        policy: AvoidZonePolicy = AvoidZonePolicy.REJECT,
        enabled: Boolean = true
    ): AvoidZoneRule {
        return AvoidZoneRule(
            id = "zone-1",
            name = "Zona de prueba",
            keywords = keywords,
            policy = policy,
            enabled = enabled
        )
    }
}

