package com.superdriver.app.ui

import com.superdriver.app.calculator.TripDecisionResult
import com.superdriver.app.config.DriverConfig
import com.superdriver.app.config.DriverConfigFormState

data class MainUiState(
    val overlayPermissionStatus: String = "Pendiente",
    val screenCapturePermissionStatus: String = "Pendiente",
    val screenCaptureErrorMessage: String? = null,
    val lastCapturedFrameWidth: Int? = null,
    val lastCapturedFrameHeight: Int? = null,
    val lastCapturedFrameTimestamp: Long? = null,
    val ocrStatus: String = "التحليل متوقف",
    val lastRecognizedText: String? = null,
    val ocrErrorMessage: String? = null,
    val monitorStatus: String = "Detenido",
    val hasMonitoringSessionStarted: Boolean = false,
    val monitorLastRecognizedText: String? = null,
    val monitorErrorMessage: String? = null,
    val monitorOverlayStatus: String? = null,
    val serviceStatus: String = "Detenido",
    val lastDecision: TripDecisionResult? = null,
    val lastRealDecision: TripDecisionResult? = null,
    val decisionStatusMessage: String? = null,
    val lastConfig: DriverConfig? = null,
    val configForm: DriverConfigFormState = DriverConfigFormState(),
    val configStatusMessage: String? = null
)
