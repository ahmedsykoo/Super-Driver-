package com.superdriver.app.ocr

import com.superdriver.app.calculator.DriverProfitCalculator
import com.superdriver.app.calculator.TripDecisionResult
import com.superdriver.app.calculator.TripOfferInput
import com.superdriver.app.config.DriverConfig
import com.superdriver.app.debug.SuperDriverDebugLogger
import com.superdriver.app.zones.AvoidZoneMatcher

class TripOfferDecisionPipeline(
    private val parser: TripOfferTextParser = TripOfferTextParser(),
    private val presenceValidator: TripOfferPresenceValidator = TripOfferPresenceValidator(),
    private val calculator: DriverProfitCalculator = DriverProfitCalculator(),
    private val zoneMatcher: AvoidZoneMatcher = AvoidZoneMatcher()
) {
    fun analyzeRecognizedText(
        rawText: String?,
        config: DriverConfig,
        traceId: String? = null
    ): TripOfferAnalysisResult {
        SuperDriverDebugLogger.log(
            "pipeline raw text",
            "traceId=${traceId.orNone()}, present=${!rawText.isNullOrBlank()}, textLength=${rawText?.length ?: 0}"
        )
        if (rawText.isNullOrBlank()) {
            SuperDriverDebugLogger.log("pipeline presence result", "traceId=${traceId.orNone()}, activeOffer=false, reason=NoText")
            return TripOfferAnalysisResult.NoText
        }

        val presence = presenceValidator.evaluate(rawText)
        SuperDriverDebugLogger.log(
            "pipeline presence score",
            "traceId=${traceId.orNone()}, isLikelyOffer=${presence.isLikelyOffer}, " +
                "score=${presence.score}, hasActionMarker=${presence.hasActionMarker}, reasons=${presence.reasons}"
        )
        if (!presence.isLikelyOffer) {
            SuperDriverDebugLogger.log(
                "pipeline presence result",
                "traceId=${traceId.orNone()}, activeOffer=false, score=${presence.score}, reasons=${presence.reasons}"
            )
            return TripOfferAnalysisResult.NoTripDetected
        }
        SuperDriverDebugLogger.log(
            "pipeline presence result",
            "traceId=${traceId.orNone()}, activeOffer=true, score=${presence.score}, " +
                "hasActionMarker=${presence.hasActionMarker}, reasons=${presence.reasons}"
        )

        val candidate = parser.parse(rawText, traceId)
            ?: run {
                SuperDriverDebugLogger.log("pipeline result", "traceId=${traceId.orNone()}, result=NoTripDetected")
                return TripOfferAnalysisResult.NoTripDetected
            }
        SuperDriverDebugLogger.log(
            "pipeline candidate summary",
            "traceId=${traceId.orNone()}, fare=${candidate.fareAmount}, pickupKm=${candidate.pickupKm}, " +
                "tripKm=${candidate.tripKm}, pickupMinutes=${candidate.pickupMinutes}, " +
                "tripMinutes=${candidate.tripMinutes}, platform=${candidate.platform}, confidence=${candidate.confidence}"
        )

        val input = candidate.toTripOfferInput()
        SuperDriverDebugLogger.log(
            "pipeline input summary",
            "traceId=${traceId.orNone()}, fare=${input.fareAmount}, pickupKm=${input.pickupKm}, " +
                "tripKm=${input.tripKm}, pickupMinutes=${input.pickupMinutes}, " +
                "tripMinutes=${input.tripMinutes}, platform=${input.platform}"
        )

        val zoneMatch = zoneMatcher.findMatch(candidate.rawText, config.avoidZones)
        SuperDriverDebugLogger.log("pipeline zone match", "traceId=${traceId.orNone()}, zoneMatch=$zoneMatch")

        val result = calculator.calculate(
            input = input,
            config = config,
            zoneMatch = zoneMatch
        )
        SuperDriverDebugLogger.log(
            "pipeline decision result summary",
            "traceId=${traceId.orNone()}, decision=${result.decision}, fare=${result.fareAmount}, " +
                "egpPerKm=${result.egpPerKm}, egpPerHour=${result.egpPerHour}, totalKm=${result.totalKm}, " +
                "totalMinutes=${result.totalMinutes}, rejectionReasons=${result.rejectionReasons}, " +
                "reviewReasons=${result.reviewReasons}"
        )

        return TripOfferAnalysisResult.DecisionReady(
            result = result,
            input = input
        )
    }

    private fun TripOfferCandidate.toTripOfferInput(): TripOfferInput {
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

    private fun String?.orNone(): String = this ?: "none"
}

sealed interface TripOfferAnalysisResult {
    data object NoText : TripOfferAnalysisResult
    data object NoTripDetected : TripOfferAnalysisResult
    data class DecisionReady(
        val result: TripDecisionResult,
        val input: TripOfferInput
    ) : TripOfferAnalysisResult
}
