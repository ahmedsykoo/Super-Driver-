package com.superdriver.app.ocr

data class OcrTextResult(
    val status: OcrStatus,
    val rawText: String? = null,
    val errorMessage: String? = null
)
