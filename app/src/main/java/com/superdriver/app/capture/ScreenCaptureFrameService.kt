package com.superdriver.app.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.DisplayMetrics
import androidx.core.app.NotificationCompat
import com.superdriver.app.ocr.MlKitScreenTextRecognizer
import com.superdriver.app.ocr.OcrStatus
import com.superdriver.app.ocr.OcrTextResult
import java.util.concurrent.atomic.AtomicBoolean

class ScreenCaptureFrameService : Service() {
    private var mediaProjection: MediaProjection? = null
    private var projectionCallback: MediaProjection.Callback? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var captureThread: HandlerThread? = null
    private val textRecognizer = MlKitScreenTextRecognizer()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_CAPTURE_ONCE) {
            stopSelf()
            return START_NOT_STICKY
        }

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_RESULT_DATA)
        }
        if (resultCode == 0 || resultData == null) {
            publishError("إذن MediaProjection غير متاح.")
            stopSelf()
            return START_NOT_STICKY
        }

        ensureNotificationChannel()
        startCaptureForeground()
        captureFrame(resultCode, resultData)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        releaseCaptureResources()
        super.onDestroy()
    }

    private fun captureFrame(resultCode: Int, resultData: Intent) {
        val manager = getSystemService(MediaProjectionManager::class.java)
        val projection = runCatching { manager.getMediaProjection(resultCode, resultData) }
            .onFailure { error ->
                publishError(error.message ?: "تعذر إنشاء MediaProjection.")
                stopSelf()
            }
            .getOrNull() ?: return

        mediaProjection = projection
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels.takeIf { it > 0 } ?: FALLBACK_CAPTURE_SIZE
        val height = metrics.heightPixels.takeIf { it > 0 } ?: FALLBACK_CAPTURE_SIZE
        val densityDpi = metrics.densityDpi.takeIf { it > 0 } ?: DisplayMetrics.DENSITY_DEFAULT
        val finished = AtomicBoolean(false)
        captureThread = HandlerThread("ScreenCaptureOnce").apply { start() }
        val captureHandler = Handler(requireNotNull(captureThread).looper)

        fun finish(sessionIntent: Intent) {
            if (!finished.compareAndSet(false, true)) {
                return
            }

            sendBroadcast(sessionIntent.setPackage(packageName))
            stopSelf()
        }

        projectionCallback = object : MediaProjection.Callback() {
            override fun onStop() {
                if (!finished.get()) {
                    finish(buildErrorIntent("توقفت جلسة الالتقاط قبل الحصول على إطار."))
                }
            }
        }
        projection.registerCallback(requireNotNull(projectionCallback), captureHandler)

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 1).apply {
            setOnImageAvailableListener(
                { reader ->
                    val image = runCatching { reader.acquireLatestImage() }.getOrNull()
                    if (image == null) {
                        finish(buildErrorIntent("تعذر قراءة الإطار الملتقط."))
                        return@setOnImageAvailableListener
                    }

                    val frameWidth = image.width
                    val frameHeight = image.height
                    val capturedAtMillis = System.currentTimeMillis()
                    val bitmap = runCatching { image.toBitmap() }
                        .onFailure { error ->
                            image.close()
                            finish(buildErrorIntent(error.message ?: "تعذر تجهيز الإطار لتحليل OCR."))
                        }
                        .getOrNull() ?: return@setOnImageAvailableListener
                    image.close()

                    textRecognizer.recognizeText(bitmap) { ocrResult ->
                        finish(
                            buildCaptureResultIntent(
                                frameWidth = frameWidth,
                                frameHeight = frameHeight,
                                capturedAtMillis = capturedAtMillis,
                                ocrResult = ocrResult
                            )
                        )
                    }
                },
                captureHandler
            )
        }

        runCatching {
            virtualDisplay = projection.createVirtualDisplay(
                "SuperDriverFrameCapture",
                width,
                height,
                densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null,
                captureHandler
            )
        }.onFailure { error ->
            finish(buildErrorIntent(error.message ?: "تعذر بدء التقاط الشاشة."))
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "التقاط الشاشة",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "يلتقط إطارًا مصرحًا به للتحليل المحلي."
            setShowBadge(false)
        }

        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun startCaptureForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("جارٍ التقاط الشاشة")
            .setContentText("جارٍ التقاط إطار مصرح به للتحليل المحلي.")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun publishError(message: String) {
        sendBroadcast(buildErrorIntent(message).setPackage(packageName))
    }

    private fun buildErrorIntent(message: String): Intent {
        return Intent(ACTION_CAPTURE_RESULT).apply {
            putExtra(EXTRA_STATUS, ScreenCaptureStatus.ERROR.name)
            putExtra(EXTRA_ERROR_MESSAGE, message)
            putExtra(EXTRA_OCR_STATUS, OcrStatus.ERROR.name)
            putExtra(EXTRA_OCR_ERROR_MESSAGE, message)
        }
    }

    private fun buildCaptureResultIntent(
        frameWidth: Int,
        frameHeight: Int,
        capturedAtMillis: Long,
        ocrResult: OcrTextResult
    ): Intent {
        return Intent(ACTION_CAPTURE_RESULT).apply {
            putExtra(EXTRA_STATUS, ScreenCaptureStatus.CAPTURE_AVAILABLE.name)
            putExtra(EXTRA_FRAME_WIDTH, frameWidth)
            putExtra(EXTRA_FRAME_HEIGHT, frameHeight)
            putExtra(EXTRA_CAPTURED_AT_MILLIS, capturedAtMillis)
            putExtra(EXTRA_OCR_STATUS, ocrResult.status.name)
            putExtra(EXTRA_RECOGNIZED_TEXT, ocrResult.rawText)
            putExtra(EXTRA_OCR_ERROR_MESSAGE, ocrResult.errorMessage)
        }
    }

    private fun releaseCaptureResources() {
        runCatching { virtualDisplay?.release() }
        runCatching { imageReader?.close() }
        projectionCallback?.let { callback ->
            runCatching { mediaProjection?.unregisterCallback(callback) }
        }
        runCatching { mediaProjection?.stop() }
        runCatching { captureThread?.quitSafely() }

        virtualDisplay = null
        imageReader = null
        projectionCallback = null
        mediaProjection = null
        captureThread = null
    }

    companion object {
        const val ACTION_CAPTURE_ONCE = "com.superdriver.app.capture.CAPTURE_ONCE"
        const val ACTION_CAPTURE_RESULT = "com.superdriver.app.capture.CAPTURE_RESULT"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        const val EXTRA_STATUS = "extra_status"
        const val EXTRA_FRAME_WIDTH = "extra_frame_width"
        const val EXTRA_FRAME_HEIGHT = "extra_frame_height"
        const val EXTRA_CAPTURED_AT_MILLIS = "extra_captured_at_millis"
        const val EXTRA_ERROR_MESSAGE = "extra_error_message"
        const val EXTRA_OCR_STATUS = "extra_ocr_status"
        const val EXTRA_RECOGNIZED_TEXT = "extra_recognized_text"
        const val EXTRA_OCR_ERROR_MESSAGE = "extra_ocr_error_message"

        private const val NOTIFICATION_CHANNEL_ID = "screen_capture_frame_service"
        private const val NOTIFICATION_ID = 4201
        private const val FALLBACK_CAPTURE_SIZE = 1
    }
}

private fun Image.toBitmap(): Bitmap {
    val plane = planes.first()
    val buffer = plane.buffer
    val pixelStride = plane.pixelStride
    val rowStride = plane.rowStride
    val bitmapWidth = rowStride / pixelStride
    val paddedBitmap = Bitmap.createBitmap(bitmapWidth, height, Bitmap.Config.ARGB_8888)
    paddedBitmap.copyPixelsFromBuffer(buffer)
    return if (bitmapWidth == width) {
        paddedBitmap
    } else {
        Bitmap.createBitmap(paddedBitmap, 0, 0, width, height).also {
            paddedBitmap.recycle()
        }
    }
}
