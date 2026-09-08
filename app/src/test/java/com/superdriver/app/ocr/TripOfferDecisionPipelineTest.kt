package com.superdriver.app.ocr

import com.superdriver.app.calculator.DriverDecision
import com.superdriver.app.config.DriverConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripOfferDecisionPipelineTest {
    private val pipeline = TripOfferDecisionPipeline()
    // Keep legacy parser/pipeline fixtures deterministic while production defaults target Egypt/EGP.
    private val config = DriverConfig(
        minEgpPerKm = 600.0,
        minEgpPerHour = 7000.0,
        minNetProfit = 1000.0,
        costPerKm = 280.0,
        costPerMinute = 30.0,
        reviewTolerancePercent = 10.0,
        rejectIfUnknownFare = true,
        rejectIfUnknownDistance = false,
        rejectIfAvoidZoneDetected = true,
        avoidZones = emptyList()
    )

    @Test
    fun calculatesDecisionFromRecognizedText() {
        val result = pipeline.analyzeRecognizedText(
            rawText = """
                Uber EGP 10.000 5 min 1 km 35 min 7 km
                Aceptar
            """.trimIndent(),
            config = config
        )

        val decision = result as TripOfferAnalysisResult.DecisionReady
        assertEquals(DriverDecision.ACCEPT, decision.result.decision)
        assertEquals(10000.0, decision.result.fareAmount ?: 0.0, 0.001)
        assertEquals(8.0, decision.result.totalKm ?: 0.0, 0.001)
        assertEquals(40.0, decision.result.totalMinutes ?: 0.0, 0.001)
    }

    @Test
    fun returnsNoTextWhenRecognizedTextIsBlank() {
        val result = pipeline.analyzeRecognizedText(
            rawText = "   ",
            config = config
        )

        assertEquals(TripOfferAnalysisResult.NoText, result)
    }

    @Test
    fun returnsNoTripDetectedWhenParserFindsNoOfferFields() {
        val result = pipeline.analyzeRecognizedText(
            rawText = "Pantalla sin datos de viaje",
            config = config
        )

        assertEquals(TripOfferAnalysisResult.NoTripDetected, result)
    }

    @Test
    fun calculatesDecisionFromStrongEvidenceWithoutActionMarker() {
        val result = pipeline.analyzeRecognizedText(
            rawText = """
                UberX
                7.124 EGP
                A 6 min (2.5 km) de distancia
                Viaje de 24 min (12.2 km)
            """.trimIndent(),
            config = config
        )

        val decision = result as TripOfferAnalysisResult.DecisionReady
        assertEquals(7124.0, decision.result.fareAmount ?: 0.0, 0.001)
        assertEquals(14.7, decision.result.totalKm ?: 0.0, 0.001)
        assertEquals(30.0, decision.result.totalMinutes ?: 0.0, 0.001)
    }

    @Test
    fun calculatesStructuredOfferWithoutActionMarker() {
        val result = pipeline.analyzeRecognizedText(
            rawText = """
                2 Comfort Exclusivo
                8.780 EGP
                A6 min (2.5 km) de distancia
                Viaje de 26 min (12.2 km)
            """.trimIndent(),
            config = config
        )

        val decision = result as TripOfferAnalysisResult.DecisionReady
        assertEquals(8780.0, decision.result.fareAmount ?: 0.0, 0.001)
        assertEquals(14.7, decision.result.totalKm ?: 0.0, 0.001)
        assertEquals(32.0, decision.result.totalMinutes ?: 0.0, 0.001)
    }

    @Test
    fun letsCalculatorReviewPartialParsedData() {
        val result = pipeline.analyzeRecognizedText(
            rawText = """
                Uber EGP 5.127 8 km
                Aceptar
            """.trimIndent(),
            config = config
        )

        val decision = result as TripOfferAnalysisResult.DecisionReady
        assertEquals(DriverDecision.REVIEW, decision.result.decision)
        assertTrue(decision.result.reviewReasons.any { it.contains("غير مكتمل") })
    }

    @Test
    fun calculatesDecisionFromStructuredOfferWithoutPlatform() {
        val result = pipeline.analyzeRecognizedText(
            rawText = """
                2 Comfort Exclusivo
                8.780 EGP
                A6 min (2.5 km) de distancia
                Viaje de 26 min (12.2 km)
                Aceptar
            """.trimIndent(),
            config = config
        )

        val decision = result as TripOfferAnalysisResult.DecisionReady
        assertEquals(DriverDecision.REVIEW, decision.result.decision)
        assertEquals(8780.0, decision.result.fareAmount ?: 0.0, 0.001)
        assertEquals(14.7, decision.result.totalKm ?: 0.0, 0.001)
        assertEquals(32.0, decision.result.totalMinutes ?: 0.0, 0.001)
        assertTrue(decision.result.reviewReasons.none { it.contains("الوقت غير مكتمل") })
        assertTrue(decision.result.reviewReasons.none { it.contains("منصة") })
    }

    @Test
    fun calculatesDecisionFromStructuredOfferWithEmparejarVariants() {
        listOf(
            "Emparejar",
            "Ejar",
            "Empajar",
            "Emparej ar"
        ).forEach { actionMarker ->
            val result = pipeline.analyzeRecognizedText(
                rawText = """
                    2 Comfort Exclusivo
                    8.780 EGP
                    A6 min (2.5 km) de distancia
                    Viaje de 26 min (12.2 km)
                    $actionMarker
                """.trimIndent(),
                config = config
            )

            val decision = result as TripOfferAnalysisResult.DecisionReady
            assertEquals(actionMarker, 8780.0, decision.result.fareAmount ?: 0.0, 0.001)
            assertEquals(actionMarker, 32.0, decision.result.totalMinutes ?: 0.0, 0.001)
        }
    }

    @Test
    fun calculatesDecisionFromNewStructuredOfferWhenOldOverlayTextIsPresent() {
        val result = pipeline.analyzeRecognizedText(
            rawText = """
                REVISAR
                ${'$'} 5472
                ${'$'} 531/km - ${'$'} -/h
                - min - 10,3 km
                Tiempo incompleto

                2 Comfort Exclusivo
                8.780 EGP
                A6 min (2.5 km) de distancia
                Viaje de 26 min (12.2 km)
                Aceptar
            """.trimIndent(),
            config = config
        )

        val decision = result as TripOfferAnalysisResult.DecisionReady
        assertEquals(8780.0, decision.result.fareAmount ?: 0.0, 0.001)
        assertEquals(14.7, decision.result.totalKm ?: 0.0, 0.001)
        assertEquals(32.0, decision.result.totalMinutes ?: 0.0, 0.001)
        assertTrue(decision.result.reviewReasons.none { it.contains("الوقت غير مكتمل") })
    }

    @Test
    fun calculatesDecisionFromUberOfferWhenOwnOverlayTextIsPresent() {
        val result = pipeline.analyzeRecognizedText(
            rawText = """
                RECHAZAR
                ${'$'} 10
                ${'$'} 5/h - ${'$'} 0/km
                131,5 min - 44,1 km
                ${'$'}/km por debajo del minimo

                UberX
                7.124 EGP
                A 6 min (2.5 km) de distancia
                Viaje de 24 min (12.2 km)
                Emparejar
            """.trimIndent(),
            config = config
        )

        val decision = result as TripOfferAnalysisResult.DecisionReady
        assertEquals(DriverDecision.REVIEW, decision.result.decision)
        assertEquals(7124.0, decision.result.fareAmount ?: 0.0, 0.001)
        assertEquals(14.7, decision.result.totalKm ?: 0.0, 0.001)
        assertEquals(30.0, decision.result.totalMinutes ?: 0.0, 0.001)
        assertEquals(484.626, decision.result.egpPerKm ?: 0.0, 0.001)
        assertEquals(14248.0, decision.result.egpPerHour ?: 0.0, 0.001)
        assertTrue(decision.result.reviewReasons.any { it.contains("جنيه/كم منخفض") })
    }

    @Test
    fun calculatesDecisionFromUberOfferIgnoringManyExtraNumbers() {
        val result = pipeline.analyzeRecognizedText(
            rawText = """
                RECHAZAR
                ${'$'} 10
                ${'$'} 5/h - ${'$'} 0/km
                131,5 min - 44,1 km
                4,98 (253)
                14:35
                87%
                UberX
                7.124 EGP
                A 6 min (2.5 km) de distancia
                Viaje de 24 min (12.2 km)
                453.2 km
                56.4 km
                Emparejar
            """.trimIndent(),
            config = config
        )

        val decision = result as TripOfferAnalysisResult.DecisionReady
        assertEquals(DriverDecision.REVIEW, decision.result.decision)
        assertEquals(7124.0, decision.result.fareAmount ?: 0.0, 0.001)
        assertEquals(14.7, decision.result.totalKm ?: 0.0, 0.001)
        assertEquals(30.0, decision.result.totalMinutes ?: 0.0, 0.001)
        assertEquals(484.626, decision.result.egpPerKm ?: 0.0, 0.001)
        assertEquals(14248.0, decision.result.egpPerHour ?: 0.0, 0.001)
        assertTrue(decision.result.reviewReasons.any { it.contains("جنيه/كم منخفض") })
    }

    @Test
    fun calculatesDecisionFromCurrentRealUberOffer() {
        val result = pipeline.analyzeRecognizedText(
            rawText = """
                UberX
                5.472 EGP
                DNI verificado
                4,89 (18)
                A 3 min (1.3 km) de distancia
                Cabo 1ro Daniel A. Romero, Pilar
                Viaje de 26 min (11.9 km)
                Dr Francisco J. Muñiz 5802, Jose C. Paz
                Aceptar
            """.trimIndent(),
            config = config
        )

        val decision = result as TripOfferAnalysisResult.DecisionReady
        assertEquals(DriverDecision.REJECT, decision.result.decision)
        assertEquals(5472.0, decision.result.fareAmount ?: 0.0, 0.001)
        assertEquals(13.2, decision.result.totalKm ?: 0.0, 0.001)
        assertEquals(29.0, decision.result.totalMinutes ?: 0.0, 0.001)
        assertEquals(414.545, decision.result.egpPerKm ?: 0.0, 0.001)
        assertEquals(11321.379, decision.result.egpPerHour ?: 0.0, 0.001)
    }

    @Test
    fun calculatesDecisionFromCurrentUberOfferIgnoringOldOverlay() {
        val result = pipeline.analyzeRecognizedText(
            rawText = """
                REVISAR
                ${'$'} 5472
                ${'$'} 531/km - ${'$'} -/h
                - min - 10,3 km
                Tiempo incompleto

                UberX
                5.472 EGP
                A 3 min (1.3 km) de distancia
                Viaje de 26 min (11.9 km)
                Aceptar
            """.trimIndent(),
            config = config
        )

        val decision = result as TripOfferAnalysisResult.DecisionReady
        assertEquals(DriverDecision.REJECT, decision.result.decision)
        assertEquals(5472.0, decision.result.fareAmount ?: 0.0, 0.001)
        assertEquals(13.2, decision.result.totalKm ?: 0.0, 0.001)
        assertEquals(29.0, decision.result.totalMinutes ?: 0.0, 0.001)
    }
}
