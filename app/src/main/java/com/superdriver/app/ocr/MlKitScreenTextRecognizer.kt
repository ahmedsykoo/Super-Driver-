package com.superdriver.app.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.superdriver.app.debug.SuperDriverDebugLogger

class MlKitScreenTextRecognizer(
    private val bitmapPreprocessor: OcrBitmapPreprocessor = OcrBitmapPreprocessor(),
    private val recognizer: TextRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
) {
    fun recognizeText(
        bitmap: Bitmap,
        traceId: String? = null,
        onResult: (OcrTextResult) -> Unit
    ) {
        val processedBitmap = bitmapPreprocessor.preprocess(bitmap)
        SuperDriverDebugLogger.log(
            "ocr preprocessing info",
            "traceId=${traceId ?: "none"}, original=${bitmap.width}x${bitmap.height}, " +
                "processed=${processedBitmap.width}x${processedBitmap.height}, reused=${processedBitmap === bitmap}"
        )
        val image = InputImage.fromBitmap(processedBitmap, 0)

        recognizer.process(image)
            .addOnSuccessListener { recognizedText ->
                val rawText = recognizedText.text.trim()
                onResult(
                    if (rawText.isBlank()) {
                        OcrTextResult(status = OcrStatus.NO_TEXT)
                    } else {
                        OcrTextResult(
                            status = OcrStatus.TEXT_DETECTED,
                            rawText = rawText
                        )
                    }
                )
            }
            .addOnFailureListener { error ->
                onResult(
                    OcrTextResult(
                        status = OcrStatus.ERROR,
                        errorMessage = error.message ?: "تعذر التعرف على النص في الإطار."
                    )
                )
            }
            .addOnCompleteListener {
                if (processedBitmap !== bitmap) {
                    processedBitmap.recycle()
                }
            }
    }

    fun close() {
        recognizer.close()
    }
}
