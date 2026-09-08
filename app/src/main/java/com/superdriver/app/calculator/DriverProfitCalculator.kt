package com.superdriver.app.calculator

import com.superdriver.app.config.DriverConfig
import com.superdriver.app.zones.AvoidZonePolicy
import com.superdriver.app.zones.ZoneMatchResult

class DriverProfitCalculator {
    fun calculate(
        input: TripOfferInput,
        config: DriverConfig,
        zoneMatch: ZoneMatchResult? = null,
        isOcrAmbiguous: Boolean = false
    ): TripDecisionResult {
        val rejectionReasons = mutableListOf<String>()
        val reviewReasons = mutableListOf<String>()
        var hasCriticalMissingData = false

        if (input.fareAmount == null) {
            hasCriticalMissingData = true
            if (config.rejectIfUnknownFare) {
                rejectionReasons += "الأجرة غير مقروءة"
            } else {
                reviewReasons += "الأجرة غير مقروءة"
            }
        }

        val totalKm = sumIfComplete(input.pickupKm, input.tripKm)
        if (totalKm == null) {
            hasCriticalMissingData = true
            if (config.rejectIfUnknownDistance) {
                rejectionReasons += "المسافة غير مكتملة"
            } else {
                reviewReasons += "المسافة غير مكتملة"
            }
        }

        val totalMinutes = sumIfComplete(input.pickupMinutes, input.tripMinutes)
        if (totalMinutes == null) {
            hasCriticalMissingData = true
            reviewReasons += "الوقت غير مكتمل"
        }

        if (isOcrAmbiguous) {
            reviewReasons += "OCR غير واضح"
        }

        val egpPerKm = calculateEgpPerKm(input.fareAmount, totalKm)
        val egpPerHour = calculateEgpPerHour(input.fareAmount, totalMinutes)
        val estimatedCost = calculateEstimatedCost(totalKm, totalMinutes, config)
        val estimatedNetProfit = input.fareAmount?.let { fare ->
            estimatedCost?.let { cost -> fare - cost }
        }

        evaluateEgpPerKm(
            value = egpPerKm,
            input = input,
            egpPerHour = egpPerHour,
            estimatedNetProfit = estimatedNetProfit,
            config = config,
            rejectionReasons = rejectionReasons,
            reviewReasons = reviewReasons
        )

        evaluateMinimumMetric(
            value = egpPerHour,
            minimum = config.minEgpPerHour,
            tolerancePercent = config.reviewTolerancePercent,
            rejectionReason = "جنيه/ساعة أقل من الحد الأدنى",
            reviewReason = "جنيه/ساعة داخل هامش المراجعة",
            rejectionReasons = rejectionReasons,
            reviewReasons = reviewReasons
        )

        if (estimatedNetProfit != null && estimatedNetProfit < config.minNetProfit) {
            rejectionReasons += "صافي الربح أقل من الحد الأدنى"
        }

        if (zoneMatch != null) {
            when (zoneMatch.policy) {
                AvoidZonePolicy.REVIEW -> reviewReasons += "منطقة للمراجعة: ${zoneMatch.rule.name}"
                AvoidZonePolicy.REJECT -> {
                    if (config.rejectIfAvoidZoneDetected && !hasCriticalMissingData && !isOcrAmbiguous) {
                        rejectionReasons += "منطقة محظورة: ${zoneMatch.rule.name}"
                    } else {
                        reviewReasons += "تم اكتشاف منطقة محظورة: ${zoneMatch.rule.name}"
                    }
                }
            }
        }

        val decision = when {
            rejectionReasons.isNotEmpty() -> DriverDecision.REJECT
            reviewReasons.isNotEmpty() -> DriverDecision.REVIEW
            else -> DriverDecision.ACCEPT
        }

        return TripDecisionResult(
            decision = decision,
            fareAmount = input.fareAmount,
            egpPerKm = egpPerKm,
            egpPerHour = egpPerHour,
            estimatedCost = estimatedCost,
            estimatedNetProfit = estimatedNetProfit,
            totalKm = totalKm,
            totalMinutes = totalMinutes,
            rejectionReasons = rejectionReasons,
            reviewReasons = reviewReasons
        )
    }

    private fun sumIfComplete(first: Double?, second: Double?): Double? {
        return if (first != null && second != null) first + second else null
    }

    private fun calculateEgpPerKm(fareAmount: Double?, totalKm: Double?): Double? {
        return fareAmount.divideByPositive(totalKm)
    }

    private fun calculateEgpPerHour(fareAmount: Double?, totalMinutes: Double?): Double? {
        return fareAmount.divideByPositive(totalMinutes?.div(60.0))
    }

    private fun calculateEstimatedCost(
        totalKm: Double?,
        totalMinutes: Double?,
        config: DriverConfig
    ): Double? {
        return if (totalKm != null && totalMinutes != null) {
            (totalKm * config.costPerKm) + (totalMinutes * config.costPerMinute)
        } else {
            null
        }
    }

    private fun Double?.divideByPositive(denominator: Double?): Double? {
        return if (this != null && denominator != null && denominator > 0.0) {
            this / denominator
        } else {
            null
        }
    }

    private fun evaluateMinimumMetric(
        value: Double?,
        minimum: Double,
        tolerancePercent: Double,
        rejectionReason: String,
        reviewReason: String,
        rejectionReasons: MutableList<String>,
        reviewReasons: MutableList<String>
    ) {
        if (value == null) return

        val toleratedMinimum = minimum * (1.0 - (tolerancePercent / 100.0))
        when {
            value < toleratedMinimum -> rejectionReasons += rejectionReason
            value < minimum -> reviewReasons += reviewReason
        }
    }

    private fun evaluateEgpPerKm(
        value: Double?,
        input: TripOfferInput,
        egpPerHour: Double?,
        estimatedNetProfit: Double?,
        config: DriverConfig,
        rejectionReasons: MutableList<String>,
        reviewReasons: MutableList<String>
    ) {
        if (value == null) return

        val toleratedMinimum = config.minEgpPerKm * (1.0 - (config.reviewTolerancePercent / 100.0))
        when {
            value < toleratedMinimum && shouldReviewLowEgpPerKm(
                egpPerKm = value,
                input = input,
                egpPerHour = egpPerHour,
                estimatedNetProfit = estimatedNetProfit,
                config = config
            ) -> reviewReasons += "جنيه/كم منخفض لكن جنيه/ساعة مرتفع: راجع الوجهة"
            value < toleratedMinimum -> rejectionReasons += "جنيه/كم منخفض جدًا للرحلة"
            value < config.minEgpPerKm -> reviewReasons += "جنيه/كم داخل هامش المراجعة"
        }
    }

    private fun shouldReviewLowEgpPerKm(
        egpPerKm: Double,
        input: TripOfferInput,
        egpPerHour: Double?,
        estimatedNetProfit: Double?,
        config: DriverConfig
    ): Boolean {
        if (egpPerHour == null || estimatedNetProfit == null) return false
        if (hasExcessivePickupShare(input)) return false

        val rescueMinimumEgpPerKm = config.minEgpPerKm * LOW_EGP_PER_KM_REVIEW_RESCUE_RATIO
        val highEgpPerHourMinimum = config.minEgpPerHour * HIGH_EGP_PER_HOUR_REVIEW_RESCUE_RATIO

        return egpPerKm >= rescueMinimumEgpPerKm &&
            egpPerHour >= highEgpPerHourMinimum &&
            estimatedNetProfit >= config.minNetProfit
    }

    private fun hasExcessivePickupShare(input: TripOfferInput): Boolean {
        val pickupKm = input.pickupKm ?: return false
        val tripKm = input.tripKm ?: return false

        return pickupKm > tripKm
    }

    private companion object {
        const val LOW_EGP_PER_KM_REVIEW_RESCUE_RATIO = 0.70
        const val HIGH_EGP_PER_HOUR_REVIEW_RESCUE_RATIO = 1.50
    }
}
