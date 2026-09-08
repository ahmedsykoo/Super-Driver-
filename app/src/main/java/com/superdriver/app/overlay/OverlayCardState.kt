package com.superdriver.app.overlay

import com.superdriver.app.calculator.DriverDecision
import com.superdriver.app.calculator.TripDecisionResult
import kotlin.math.roundToInt

data class OverlayCardState(
    val decision: DriverDecision,
    val visualState: OverlayVisualState,
    val titleText: String,
    val fareText: String,
    val egpPerHourText: String,
    val egpPerKmText: String,
    val totalTimeText: String,
    val totalKmText: String,
    val shortReason: String? = null
) {
    companion object {
        fun fromDecisionResult(result: TripDecisionResult): OverlayCardState {
            val hasCompleteData = result.hasCompleteOverlayData()
            return OverlayCardState(
                decision = result.decision,
                visualState = if (hasCompleteData) {
                    OverlayVisualState.DECISION
                } else {
                    OverlayVisualState.INCOMPLETE_DATA
                },
                titleText = if (hasCompleteData) {
                    result.decision.toArabicLabel()
                } else {
                    "بيانات غير مكتملة"
                },
                fareText = result.fareAmount.toMoneyText(),
                egpPerHourText = result.egpPerHour.toMoneyText(),
                egpPerKmText = result.egpPerKm.toMoneyText(),
                totalTimeText = "${result.totalMinutes.toDisplayNumber()} min",
                totalKmText = "${result.totalKm.toDisplayNumber()} km",
                shortReason = result.rejectionReasons.firstOrNull()
                    ?: result.reviewReasons.firstOrNull()
                    ?: result.firstMissingOverlayDataReason()
            )
        }

        fun simulatedAccept(): OverlayCardState {
            return OverlayCardState(
                decision = DriverDecision.ACCEPT,
                visualState = OverlayVisualState.DECISION,
                titleText = DriverDecision.ACCEPT.toArabicLabel(),
                fareText = "230.84 جنيه",
                egpPerHourText = "314.78 جنيه",
                egpPerKmText = "7.33 جنيه",
                totalTimeText = "41.0 min",
                totalKmText = "8.0 km"
            )
        }

        private fun Double?.toMoneyText(): String {
            return this?.roundToInt()?.let { "$it جنيه" } ?: "-"
        }

        private fun Double?.toDisplayNumber(): String {
            return this?.let { "%.1f".format(it) } ?: "-"
        }

        private fun TripDecisionResult.firstMissingOverlayDataReason(): String? {
            return when {
                hasCompleteOverlayData() -> null
                fareAmount == null -> "الأجرة غير مقروءة"
                totalKm == null || egpPerKm == null -> "المسافة ناقصة"
                totalMinutes == null || egpPerHour == null -> "الوقت ناقص"
                else -> "بيانات غير مكتملة"
            }
        }

        private fun DriverDecision.toArabicLabel(): String {
            return when (this) {
                DriverDecision.ACCEPT -> "قبول"
                DriverDecision.REJECT -> "رفض"
                DriverDecision.REVIEW -> "مراجعة"
            }
        }
    }
}

enum class OverlayVisualState {
    DECISION,
    INCOMPLETE_DATA
}
