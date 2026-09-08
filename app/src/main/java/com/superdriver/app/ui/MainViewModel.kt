package com.superdriver.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.superdriver.app.calculator.DriverProfitCalculator
import com.superdriver.app.calculator.TripDecisionResult
import com.superdriver.app.calculator.TripOfferInput
import com.superdriver.app.capture.ScreenCaptureMonitorResult
import com.superdriver.app.capture.ScreenCaptureMonitorStatus
import com.superdriver.app.capture.ScreenCaptureSession
import com.superdriver.app.capture.ScreenCaptureStatus
import com.superdriver.app.config.DriverConfig
import com.superdriver.app.config.DriverConfigFormField
import com.superdriver.app.config.DriverConfigFormState
import com.superdriver.app.config.DriverConfigFormValidationResult
import com.superdriver.app.config.DriverConfigRepository
import com.superdriver.app.ocr.OcrStatus
import com.superdriver.app.ocr.TripOfferAnalysisResult
import com.superdriver.app.ocr.TripOfferDecisionPipeline
import com.superdriver.app.overlay.OverlayCardState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(
    private val configRepository: DriverConfigRepository,
    private val calculator: DriverProfitCalculator = DriverProfitCalculator(),
    private val ocrDecisionPipeline: TripOfferDecisionPipeline = TripOfferDecisionPipeline(
        calculator = calculator
    )
) : ViewModel() {
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            configRepository.config.collect { config ->
                _uiState.update {
                    it.copy(
                        lastConfig = config,
                        configForm = DriverConfigFormState.fromConfig(config)
                    )
                }
            }
        }
    }

    fun refreshOverlayPermission(canDrawOverlays: Boolean) {
        _uiState.update {
            it.copy(
                overlayPermissionStatus = if (canDrawOverlays) {
                    "ممنوح"
                } else {
                    "ناقص إذن"
                }
            )
        }
    }

    fun markOverlayStarted() {
        _uiState.update { it.copy(serviceStatus = "النافذة العائمة مفعلة") }
    }

    fun markOverlayStopped() {
        _uiState.update { it.copy(serviceStatus = "متوقف") }
    }

    fun markOverlayPermissionMissing() {
        _uiState.update {
            it.copy(
                overlayPermissionStatus = "ناقص إذن",
                serviceStatus = "لم يبدأ: إذن النافذة العائمة ناقص"
            )
        }
    }

    fun markScreenCapturePending() {
        _uiState.update {
            it.copy(
                screenCapturePermissionStatus = "التقاط الشاشة قيد الانتظار",
                screenCaptureErrorMessage = null,
                lastCapturedFrameWidth = null,
                lastCapturedFrameHeight = null,
                lastCapturedFrameTimestamp = null,
                ocrStatus = OcrStatus.IDLE.toArabicStatus(),
                lastRecognizedText = null,
                ocrErrorMessage = null
            )
        }
    }

    fun updateScreenCaptureSession(session: ScreenCaptureSession) {
        val frame = session.lastFrame
        _uiState.update {
            it.copy(
                screenCapturePermissionStatus = session.status.toArabicStatus(),
                screenCaptureErrorMessage = session.errorMessage,
                lastCapturedFrameWidth = frame?.width,
                lastCapturedFrameHeight = frame?.height,
                lastCapturedFrameTimestamp = frame?.capturedAtMillis,
                ocrStatus = session.ocrStatus.toArabicStatus(),
                lastRecognizedText = session.recognizedText,
                ocrErrorMessage = session.ocrErrorMessage
            )
        }
    }

    fun markMonitorWaitingPermission() {
        _uiState.update {
            it.copy(
                monitorStatus = ScreenCaptureMonitorStatus.WAITING_PERMISSION.toArabicStatus(),
                hasMonitoringSessionStarted = true,
                monitorErrorMessage = null,
                monitorOverlayStatus = null
            )
        }
    }

    fun markMonitorPermissionDenied() {
        _uiState.update {
            it.copy(
                monitorStatus = ScreenCaptureMonitorStatus.ERROR.toArabicStatus(),
                monitorErrorMessage = "تم إلغاء إذن التقاط الشاشة أو لم يتم منحه.",
                monitorOverlayStatus = null
            )
        }
    }

    fun markMonitorStopped() {
        _uiState.update {
            it.copy(
                monitorStatus = ScreenCaptureMonitorStatus.STOPPED.toArabicStatus(),
                monitorErrorMessage = null,
                monitorOverlayStatus = null
            )
        }
    }

    fun updateScreenCaptureMonitor(result: ScreenCaptureMonitorResult) {
        _uiState.update {
            it.copy(
                monitorStatus = result.status.toArabicStatus(),
                hasMonitoringSessionStarted = true,
                monitorLastRecognizedText = result.recognizedText
                    ?: it.monitorLastRecognizedText,
                monitorErrorMessage = result.errorMessage,
                monitorOverlayStatus = result.toOverlayStatusMessage(),
                ocrStatus = result.ocrStatus.toArabicStatus(),
                lastRecognizedText = result.recognizedText ?: it.lastRecognizedText,
                ocrErrorMessage = result.errorMessage,
                lastDecision = result.decisionResult ?: it.lastDecision,
                lastRealDecision = result.decisionResult ?: it.lastRealDecision,
                decisionStatusMessage = result.decisionResult?.toAnalysisStatusMessage()
                    ?: it.decisionStatusMessage
            )
        }
    }

    fun updateDriverConfigInput(
        field: DriverConfigFormField,
        value: String
    ) {
        _uiState.update {
            it.copy(
                configForm = it.configForm.update(field, value),
                configStatusMessage = null
            )
        }
    }

    fun resetConfigToDefaults() {
        viewModelScope.launch {
            configRepository.resetToDefaults()
            _uiState.update {
                it.copy(configStatusMessage = "تمت استعادة الإعدادات")
            }
        }
    }

    fun saveDriverConfigForm() {
        val state = _uiState.value
        val currentConfig = state.lastConfig ?: DriverConfig.default()

        when (val result = state.configForm.toDriverConfig(currentConfig)) {
            is DriverConfigFormValidationResult.Invalid -> {
                _uiState.update {
                    it.copy(configStatusMessage = result.message)
                }
            }
            is DriverConfigFormValidationResult.Valid -> {
                viewModelScope.launch {
                    configRepository.updateConfig(result.config)
                    _uiState.update {
                        it.copy(configStatusMessage = "تم حفظ الإعدادات")
                    }
                }
            }
        }
    }

    fun runSimulatedTripDecision(): OverlayCardState {
        val config = _uiState.value.lastConfig ?: DriverConfig.default()
        val simulatedInput = TripOfferInput(
            fareAmount = 230.84,
            pickupKm = 2.5,
            tripKm = 29.0,
            pickupMinutes = 7.0,
            tripMinutes = 37.0,
            platform = "uber",
            rawText = "230.84 EGP 7 min 2.5 km 37 min 29.0 km"
        )

        val result = calculator.calculate(
            input = simulatedInput,
            config = config
        )

        _uiState.update {
            it.copy(
                lastDecision = result,
                lastConfig = config,
                decisionStatusMessage = "تم حساب تجربة العرض"
            )
        }
        return OverlayCardState.fromDecisionResult(result)
    }

    fun analyzeLastRecognizedText() {
        val state = _uiState.value
        val config = state.lastConfig ?: DriverConfig.default()

        when (val analysis = ocrDecisionPipeline.analyzeRecognizedText(state.lastRecognizedText, config)) {
            TripOfferAnalysisResult.NoText -> {
                _uiState.update {
                    it.copy(
                        lastConfig = config,
                        decisionStatusMessage = "لا يوجد نص OCR لتحليله"
                    )
                }
            }
            TripOfferAnalysisResult.NoTripDetected -> {
                _uiState.update {
                    it.copy(
                        lastConfig = config,
                        decisionStatusMessage = "لم يتم التعرف على عرض رحلة في OCR"
                    )
                }
            }
            is TripOfferAnalysisResult.DecisionReady -> {
                val result = analysis.result
                _uiState.update {
                    it.copy(
                        lastDecision = result,
                        lastRealDecision = result,
                        lastConfig = config,
                        decisionStatusMessage = result.toAnalysisStatusMessage()
                    )
                }
            }
        }
    }

    fun buildLastRealDecisionOverlayState(): OverlayCardState? {
        val result = _uiState.value.lastRealDecision
        if (result == null) {
            _uiState.update {
                it.copy(decisionStatusMessage = "لا يوجد قرار فعلي محسوب لعرضه في النافذة العائمة")
            }
            return null
        }

        return OverlayCardState.fromDecisionResult(result)
    }

    private fun ScreenCaptureStatus.toArabicStatus(): String {
        return when (this) {
            ScreenCaptureStatus.PENDING -> "التقاط الشاشة قيد الانتظار"
            ScreenCaptureStatus.AUTHORIZED -> "التقاط الشاشة مصرح"
            ScreenCaptureStatus.CAPTURE_AVAILABLE -> "التقاط الشاشة متاح"
            ScreenCaptureStatus.STOPPED -> "التقاط الشاشة متوقفة"
            ScreenCaptureStatus.ERROR -> "خطأ في التقاط الشاشة"
        }
    }

    private fun OcrStatus.toArabicStatus(): String {
        return when (this) {
            OcrStatus.IDLE -> "التحليل متوقف"
            OcrStatus.PROCESSING -> "تحليل الشاشة"
            OcrStatus.TEXT_DETECTED -> "تم اكتشاف نص"
            OcrStatus.NO_TEXT -> "لم يتم اكتشاف نص"
            OcrStatus.ERROR -> "خطأ في التحليل"
        }
    }

    private fun ScreenCaptureMonitorStatus.toArabicStatus(): String {
        return when (this) {
            ScreenCaptureMonitorStatus.STOPPED -> "متوقف"
            ScreenCaptureMonitorStatus.WAITING_PERMISSION -> "في انتظار الإذن"
            ScreenCaptureMonitorStatus.MONITORING -> "جارٍ المراقبة"
            ScreenCaptureMonitorStatus.ANALYZING -> "جارٍ التحليل"
            ScreenCaptureMonitorStatus.OFFER_DETECTED -> "تم اكتشاف عرض"
            ScreenCaptureMonitorStatus.INCOMPLETE_DATA -> "بيانات غير مكتملة"
            ScreenCaptureMonitorStatus.NO_OFFER_DETECTED -> "لم يتم اكتشاف عرض"
            ScreenCaptureMonitorStatus.ERROR -> "خطأ"
        }
    }

    private fun ScreenCaptureMonitorResult.toOverlayStatusMessage(): String? {
        return when {
            status == ScreenCaptureMonitorStatus.OFFER_DETECTED && overlayUpdated -> {
                "تم تحديث النافذة العائمة بالعرض المكتشف"
            }
            status == ScreenCaptureMonitorStatus.OFFER_DETECTED -> {
                "تم عرض العرض بالفعل، ولن يتم تكرار النافذة العائمة"
            }
            status == ScreenCaptureMonitorStatus.INCOMPLETE_DATA -> {
                if (overlayUpdated) {
                    "وضع التشخيص: نافذة عائمة ببيانات غير مكتملة"
                } else {
                    "بيانات غير مكتملة: لم يتم عرض النافذة العائمة"
                }
            }
            status == ScreenCaptureMonitorStatus.NO_OFFER_DETECTED -> {
                "لم يتم اكتشاف عرض في الدورات الأخيرة"
            }
            else -> null
        }
    }

    private fun TripDecisionResult.toAnalysisStatusMessage(): String {
        val missingDataReasons = (rejectionReasons + reviewReasons)
            .filter { reason ->
                reason.contains("غير مكتملة", ignoreCase = true) || reason.contains("غير مقروءة", ignoreCase = true) ||
                    reason.contains("غير مكتملة", ignoreCase = true)
            }

        return when {
            missingDataReasons.isNotEmpty() -> "تم حساب قرار OCR ببيانات ناقصة: ${missingDataReasons.first()}"
            rejectionReasons.isNotEmpty() -> "تم حساب قرار OCR: رفض بسبب ${rejectionReasons.first()}"
            reviewReasons.isNotEmpty() -> "تم حساب قرار OCR: مراجعة بسبب ${reviewReasons.first()}"
            else -> "تم حساب قرار OCR ببيانات كاملة"
        }
    }
}
