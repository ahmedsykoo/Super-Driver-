package com.superdriver.app.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripOfferPresenceValidatorTest {
    private val validator = TripOfferPresenceValidator()

    @Test
    fun acceptsEmparejarNearBottom() {
        val rawText = """
            UberX
            7124 EGP
            A 6 min (2.5 km) de distancia
            Viaje de 24 min (12.2 km)
            Florida, Jose C. Paz
            Emparejar
        """.trimIndent()

        val result = validator.evaluate(rawText)

        assertTrue(result.isLikelyOffer)
        assertTrue(result.hasActionMarker)
        assertEquals(1.0, result.score, 0.0)
    }

    @Test
    fun acceptsAceptarNearBottom() {
        val rawText = """
            UberX
            5472 EGP
            A 3 min (1.3 km) de distancia
            Viaje de 26 min (11.9 km)
            Aceptar
        """.trimIndent()

        val result = validator.evaluate(rawText)

        assertTrue(result.isLikelyOffer)
        assertTrue(result.hasActionMarker)
        assertEquals(1.0, result.score, 0.0)
    }

    @Test
    fun acceptsEmparejarOcrVariantsNearBottom() {
        listOf(
            "Ejar",
            "Empajar",
            "Emparej ar",
            "Empareiar",
            "Enparejar"
        ).forEach { actionMarker ->
            val rawText = """
                2 Comfort Exclusivo
                8780 EGP
                A6 min (2.5 km) de distancia
                Viaje de 26 min (12.2 km)
                $actionMarker
            """.trimIndent()

            val result = validator.evaluate(rawText)

            assertTrue(actionMarker, result.isLikelyOffer)
            assertTrue(actionMarker, result.hasActionMarker)
        }
    }

    @Test
    fun acceptsStrongOfferEvidenceWithoutActionMarker() {
        val rawText = """
            UberX
            5472 EGP
            A 3 min (1.3 km) de distancia
            Viaje de 26 min (11.9 km)
        """.trimIndent()

        val result = validator.evaluate(rawText)

        assertTrue(result.isLikelyOffer)
        assertFalse(result.hasActionMarker)
        assertTrue(result.score >= 0.75)
        assertTrue("fare" in result.reasons)
        assertTrue("km" in result.reasons)
        assertTrue("minutes" in result.reasons)
        assertTrue("trip structure" in result.reasons)
    }

    @Test
    fun acceptsStrongEvidenceEvenWhenBottomTextIsNotActionMarker() {
        val rawText = """
            2 Comfort Exclusivo
            8780 EGP
            A6 min (2.5 km) de distancia
            Viaje de 26 min (12.2 km)
            Ver detalles
        """.trimIndent()

        val result = validator.evaluate(rawText)

        assertTrue(result.isLikelyOffer)
        assertFalse(result.hasActionMarker)
    }

    @Test
    fun acceptsStrongEvidenceWhenAcceptTextIsOutsideBottomSection() {
        val rawText = """
            ACEPTAR
            Overlay anterior
            UberX
            5472 EGP
            A 3 min (1.3 km) de distancia
            Viaje de 26 min (11.9 km)
            Florida, Jose C. Paz
            X
        """.trimIndent()

        val result = validator.evaluate(rawText)

        assertTrue(result.isLikelyOffer)
        assertFalse(result.hasActionMarker)
    }

    @Test
    fun rejectsIsolatedPrice() {
        val rawText = """
            Total estimado
            $ 5472
            Promo disponible
        """.trimIndent()

        val result = validator.evaluate(rawText)

        assertFalse(result.isLikelyOffer)
        assertFalse(result.hasActionMarker)
        assertTrue(result.score < 0.75)
    }

    @Test
    fun rejectsIsolatedKmAndMinutes() {
        val rawText = """
            Resumen semanal
            41 min
            8.0 km
            Sin solicitudes activas
        """.trimIndent()

        val result = validator.evaluate(rawText)

        assertFalse(result.isLikelyOffer)
        assertFalse(result.hasActionMarker)
    }

    @Test
    fun rejectsOwnArabicIncompleteOverlayText() {
        val rawText = """
            بيانات غير مكتملة
            230.84 جنيه
            7 جنيه/km - 315 جنيه/h
            41.0 min - 31.5 km
            المسافة ناقصة
        """.trimIndent()

        val result = validator.evaluate(rawText)

        assertFalse(result.isLikelyOffer)
        assertEquals(listOf("own overlay text"), result.reasons)
    }

    @Test
    fun rejectsOwnArabicDecisionOverlayBeforeActionMarkerCheck() {
        val rawText = """
            قبول
            230.84 جنيه
            7 جنيه/km - 315 جنيه/h
            41.0 min - 31.5 km
        """.trimIndent()

        val result = validator.evaluate(rawText)

        assertFalse(result.isLikelyOffer)
        assertFalse(result.hasActionMarker)
        assertEquals(listOf("own overlay text"), result.reasons)
    }

    @Test
    fun acceptsRealisticEgyptianArabicOffer() {
        val result = validator.evaluate("""
            أوبر
            230.84 EGP
            استلام 7 د (2.5 كم)
            الرحلة 37 دقيقة (29 كم)
            قبول
        """.trimIndent())

        assertTrue(result.isLikelyOffer)
    }

}
