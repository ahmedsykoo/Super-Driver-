package com.superdriver.app.capture

data class ScreenCaptureFrame(
    val width: Int,
    val height: Int,
    val capturedAtMillis: Long
)
