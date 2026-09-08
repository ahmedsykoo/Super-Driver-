package com.superdriver.app.capture

import android.content.Intent
import com.superdriver.app.calculator.DriverDecision
import com.superdriver.app.calculator.TripDecisionResult
import com.superdriver.app.ocr.OcrStatus

data class ScreenCaptureMonitorResult(
    val status: ScreenCaptureMonitorStatus,
    val recognizedText: String? = null,
    val ocrStatus: OcrStatus = OcrStatus.IDLE,
    val errorMessage: String? = null,
    val decisionResult: TripDecisionResult? = null,
    val overlayUpdated: Boolean = false
) {
    companion object {
        fun fromIntent(intent: Intent): ScreenCaptureMonitorResult {
            val status = intent.getStringExtra(ScreenCaptureMonitorService.EXTRA_MONITOR_STATUS)
                ?.let { runCatching { ScreenCaptureMonitorStatus.valueOf(it) }.getOrNull() }
                ?: ScreenCaptureMonitorStatus.ERROR
            val ocrStatus = intent.getStringExtra(ScreenCaptureMonitorService.EXTRA_OCR_STATUS)
                ?.let { runCatching { OcrStatus.valueOf(it) }.getOrNull() }
                ?: OcrStatus.IDLE
            val decision = intent.getStringExtra(ScreenCaptureMonitorService.EXTRA_DECISION)
                ?.let { runCatching { DriverDecision.valueOf(it) }.getOrNull() }

            return ScreenCaptureMonitorResult(
                status = status,
                recognizedText = intent.getStringExtra(ScreenCaptureMonitorService.EXTRA_RECOGNIZED_TEXT),
                ocrStatus = ocrStatus,
                errorMessage = intent.getStringExtra(ScreenCaptureMonitorService.EXTRA_ERROR_MESSAGE),
                decisionResult = decision?.let {
                    TripDecisionResult(
                        decision = it,
                        fareAmount = intent.doubleExtraOrNull(ScreenCaptureMonitorService.EXTRA_FARE_AMOUNT),
                        egpPerKm = intent.doubleExtraOrNull(ScreenCaptureMonitorService.EXTRA_EGP_PER_KM),
                        egpPerHour = intent.doubleExtraOrNull(ScreenCaptureMonitorService.EXTRA_EGP_PER_HOUR),
                        estimatedCost = intent.doubleExtraOrNull(ScreenCaptureMonitorService.EXTRA_ESTIMATED_COST),
                        estimatedNetProfit = intent.doubleExtraOrNull(
                            ScreenCaptureMonitorService.EXTRA_ESTIMATED_NET_PROFIT
                        ),
                        totalKm = intent.doubleExtraOrNull(ScreenCaptureMonitorService.EXTRA_TOTAL_KM),
                        totalMinutes = intent.doubleExtraOrNull(ScreenCaptureMonitorService.EXTRA_TOTAL_MINUTES),
                        rejectionReasons = intent.getStringArrayListExtra(
                            ScreenCaptureMonitorService.EXTRA_REJECTION_REASONS
                        ).orEmpty(),
                        reviewReasons = intent.getStringArrayListExtra(
                            ScreenCaptureMonitorService.EXTRA_REVIEW_REASONS
                        ).orEmpty()
                    )
                },
                overlayUpdated = intent.getBooleanExtra(
                    ScreenCaptureMonitorService.EXTRA_OVERLAY_UPDATED,
                    false
                )
            )
        }

        private fun Intent.doubleExtraOrNull(name: String): Double? {
            return if (hasExtra(name)) getDoubleExtra(name, 0.0) else null
        }
    }
}
