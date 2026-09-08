package com.superdriver.app.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import com.superdriver.app.debug.SuperDriverDebugLogger

class OcrBitmapPreprocessor(
    private val cropTopRatio: Float = DEFAULT_CROP_TOP_RATIO,
    private val scaleFactor: Float = DEFAULT_SCALE_FACTOR,
    private val contrast: Float = DEFAULT_CONTRAST,
    private val brightness: Float = DEFAULT_BRIGHTNESS
) {
    fun preprocess(bitmap: Bitmap): Bitmap {
        val cropped = cropLowerArea(bitmap)
        val scaled = scale(cropped)
        if (cropped !== bitmap) {
            cropped.recycle()
        }
        val enhanced = enhanceContrast(scaled)
        if (enhanced !== scaled) {
            scaled.recycle()
        }

        SuperDriverDebugLogger.log(
            "ocr bitmap preprocessing",
            "original=${bitmap.width}x${bitmap.height}, processed=${enhanced.width}x${enhanced.height}, " +
                "cropTopRatio=$cropTopRatio, scaleFactor=$scaleFactor, contrast=$contrast, brightness=$brightness"
        )
        return enhanced
    }

    private fun cropLowerArea(bitmap: Bitmap): Bitmap {
        val startY = (bitmap.height * cropTopRatio)
            .toInt()
            .coerceIn(0, bitmap.height - 1)
        val height = (bitmap.height - startY).coerceAtLeast(1)
        return Bitmap.createBitmap(bitmap, 0, startY, bitmap.width, height)
    }

    private fun scale(bitmap: Bitmap): Bitmap {
        val targetWidth = (bitmap.width * scaleFactor).toInt().coerceAtLeast(bitmap.width)
        val targetHeight = (bitmap.height * scaleFactor).toInt().coerceAtLeast(bitmap.height)
        if (targetWidth == bitmap.width && targetHeight == bitmap.height) return bitmap
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    private fun enhanceContrast(bitmap: Bitmap): Bitmap {
        val enhanced = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val translate = (-0.5f * contrast + 0.5f) * 255f + brightness
        val colorMatrix = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, translate,
                0f, contrast, 0f, 0f, translate,
                0f, 0f, contrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(colorMatrix)
        }
        Canvas(enhanced).drawBitmap(bitmap, 0f, 0f, paint)
        return enhanced
    }

    private companion object {
        private const val DEFAULT_CROP_TOP_RATIO = 0.45f
        private const val DEFAULT_SCALE_FACTOR = 1.5f
        private const val DEFAULT_CONTRAST = 1.35f
        private const val DEFAULT_BRIGHTNESS = 8.0f
    }
}
