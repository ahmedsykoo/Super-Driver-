package com.superdriver.app.capture

import android.content.Intent
import com.superdriver.app.ocr.OcrStatus

data class ScreenCaptureSession(
    val status: ScreenCaptureStatus,
    val resultCode: Int? = null,
    val resultData: Intent? = null,
    val lastFrame: ScreenCaptureFrame? = null,
    val ocrStatus: OcrStatus = OcrStatus.IDLE,
    val recognizedText: String? = null,
    val ocrErrorMessage: String? = null,
    val errorMessage: String? = null
)
