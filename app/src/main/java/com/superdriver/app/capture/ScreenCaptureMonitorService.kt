package com.superdriver.app.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Color
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
import android.provider.Settings
import android.util.DisplayMetrics
import androidx.core.app.NotificationCompat
import com.superdriver.app.calculator.TripDecisionResult
import com.superdriver.app.config.DataStoreDriverConfigRepository
import com.superdriver.app.config.DriverConfig
import com.superdriver.app.debug.SuperDriverDebugLogger
import com.superdriver.app.ocr.MlKitScreenTextRecognizer
import com.superdriver.app.ocr.OcrStatus
import com.superdriver.app.ocr.TripOfferAnalysisResult
import com.superdriver.app.ocr.TripOfferDecisionPipeline
import com.superdriver.app.overlay.DriverDecisionOverlayService
import com.superdriver.app.overlay.OverlayCardState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

class ScreenCaptureMonitorService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val textRecognizer = MlKitScreenTextRecognizer()
    private val decisionPipeline = TripOfferDecisionPipeline()
    private val detectionState = TripOfferDetectionState()
    private val frameGate = OcrFrameGate()
    private val traceIdGenerator = OcrTraceIdGenerator()
    private val candidateBuffer = OcrCandidateBuffer()
    private val isProcessingFrame = AtomicBoolean(false)

    @Volatile
    private var currentConfig: DriverConfig = DriverConfig.default()

    private var mediaProjection: MediaProjection? = null
    private var projectionCallback: MediaProjection.Callback? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var captureThread: HandlerThread? = null
    private var captureHandler: Handler? = null
    private var nextFrameAtMillis: Long = 0L
    private var noOfferCycles: Int = 0
    private var overlayVisible: Boolean = false
    private var candidateBufferFlushRunnable: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        DataStoreDriverConfigRepository(context = applicationContext).let { repository ->
            serviceScope.launch {
                repository.config.collect { config ->
                    currentConfig = config
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_MONITORING) {
            publishStatus(ScreenCaptureMonitorStatus.STOPPED)
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent?.action != ACTION_START_MONITORING) {
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
            publishStatus(
                status = ScreenCaptureMonitorStatus.ERROR,
                errorMessage = "إذن MediaProjection غير متاح."
            )
            stopSelf()
            return START_NOT_STICKY
        }

        ensureNotificationChannel()
        startMonitorForeground()
        startMonitoring(resultCode, resultData)
        return START_STICKY
    }

    override fun onDestroy() {
        cancelCandidateBufferFlush()
        candidateBuffer.clear()
        releaseCaptureResources()
        textRecognizer.close()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startMonitoring(resultCode: Int, resultData: Intent) {
        if (mediaProjection != null) {
            publishStatus(ScreenCaptureMonitorStatus.MONITORING)
            return
        }

        val manager = getSystemService(MediaProjectionManager::class.java)
        val projection = runCatching { manager.getMediaProjection(resultCode, resultData) }
            .onFailure { error ->
                publishStatus(
                    status = ScreenCaptureMonitorStatus.ERROR,
                    errorMessage = error.message ?: "تعذر إنشاء MediaProjection."
                )
                stopSelf()
            }
            .getOrNull() ?: return

        mediaProjection = projection
        captureThread = HandlerThread("ScreenCaptureMonitor").apply { start() }
        val handler = Handler(requireNotNull(captureThread).looper)
        captureHandler = handler

        projectionCallback = object : MediaProjection.Callback() {
            override fun onStop() {
                publishStatus(
                    status = ScreenCaptureMonitorStatus.STOPPED,
                    errorMessage = "توقفت جلسة المراقبة."
                )
                stopSelf()
            }
        }
        projection.registerCallback(requireNotNull(projectionCallback), handler)

        val metrics = resources.displayMetrics
        val rawWidth = metrics.widthPixels.takeIf { it > 0 } ?: FALLBACK_CAPTURE_SIZE
        val rawHeight = metrics.heightPixels.takeIf { it > 0 } ?: FALLBACK_CAPTURE_SIZE
        val captureWidth = rawWidth.coerceAtMost(MAX_CAPTURE_WIDTH)
        val captureHeight = ((rawHeight.toFloat() * captureWidth) / rawWidth)
            .roundToInt()
            .coerceAtLeast(FALLBACK_CAPTURE_SIZE)
        val densityDpi = metrics.densityDpi.takeIf { it > 0 } ?: DisplayMetrics.DENSITY_DEFAULT

        imageReader = ImageReader.newInstance(
            captureWidth,
            captureHeight,
            PixelFormat.RGBA_8888,
            IMAGE_BUFFER_SIZE
        ).apply {
            setOnImageAvailableListener({ reader ->
                handleImageAvailable(reader)
            }, handler)
        }

        runCatching {
            virtualDisplay = projection.createVirtualDisplay(
                "SuperDriverScreenMonitor",
                captureWidth,
                captureHeight,
                densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null,
                handler
            )
            frameGate.reset()
            nextFrameAtMillis = 0L
            SuperDriverDebugLogger.log(
                "monitor capture size",
                "raw=${rawWidth}x$rawHeight, capture=${captureWidth}x$captureHeight, maxWidth=$MAX_CAPTURE_WIDTH"
            )
            publishStatus(ScreenCaptureMonitorStatus.MONITORING)
        }.onFailure { error ->
            publishStatus(
                status = ScreenCaptureMonitorStatus.ERROR,
                errorMessage = error.message ?: "تعذر بدء مراقبة الشاشة."
            )
            stopSelf()
        }
    }

    private fun handleImageAvailable(reader: ImageReader) {
        val now = System.currentTimeMillis()
        val image = runCatching { reader.acquireLatestImage() }.getOrNull() ?: return

        if (now < nextFrameAtMillis) {
            image.close()
            return
        }

        val bitmap = runCatching { image.toMonitorBitmap() }
            .onFailure { error ->
                image.close()
                publishStatus(
                    status = ScreenCaptureMonitorStatus.ERROR,
                    errorMessage = error.message ?: "تعذر تجهيز الإطار لتحليل OCR."
                )
            }
            .getOrNull() ?: return
        image.close()

        val gateDecision = runCatching {
            frameGate.evaluate(
                signature = bitmap.toMonitorFrameSignature(),
                nowMillis = now
            )
        }.onFailure { error ->
            bitmap.recycle()
            publishStatus(
                status = ScreenCaptureMonitorStatus.ERROR,
                errorMessage = error.message ?: "تعذر تقييم إطار المراقبة."
            )
        }.getOrNull() ?: return

        nextFrameAtMillis = now + gateDecision.nextFrameDelayMillis

        if (!gateDecision.shouldRunOcr) {
            SuperDriverDebugLogger.log(
                "monitor OCR skipped",
                "reason=${gateDecision.reason}, changeScore=${gateDecision.changeScore}, " +
                    "threshold=${gateDecision.threshold}, cooldownActive=${gateDecision.cooldownActive}, " +
                    "millisSinceLastOcr=${gateDecision.millisSinceLastOcr}, " +
                    "nextDelay=${gateDecision.nextFrameDelayMillis}"
            )
            bitmap.recycle()
            return
        }

        if (!isProcessingFrame.compareAndSet(false, true)) {
            SuperDriverDebugLogger.log("monitor OCR skipped", "reason=ocr already processing")
            bitmap.recycle()
            return
        }

        val traceId = traceIdGenerator.next(now)
        SuperDriverDebugLogger.log(
            "monitor OCR started",
            "traceId=$traceId, reason=${gateDecision.reason}, changeScore=${gateDecision.changeScore}, " +
                "threshold=${gateDecision.threshold}, cooldownActive=${gateDecision.cooldownActive}, " +
                "millisSinceLastOcr=${gateDecision.millisSinceLastOcr}, " +
                "bitmap=${bitmap.width}x${bitmap.height}"
        )
        publishStatus(ScreenCaptureMonitorStatus.ANALYZING)

        textRecognizer.recognizeText(bitmap, traceId) { ocrResult ->
            bitmap.recycle()
            SuperDriverDebugLogger.log(
                "monitor OCR completed",
                "traceId=$traceId, status=${ocrResult.status}, textLength=${ocrResult.rawText?.length ?: 0}, " +
                    "error=${ocrResult.errorMessage}"
            )
            when (ocrResult.status) {
                OcrStatus.TEXT_DETECTED -> analyzeRecognizedText(ocrResult.rawText, traceId)
                OcrStatus.NO_TEXT -> markNoOffer(ocrResult.rawText, ocrResult.status, traceId)
                OcrStatus.ERROR -> publishStatus(
                    status = ScreenCaptureMonitorStatus.ERROR,
                    recognizedText = ocrResult.rawText,
                    ocrStatus = ocrResult.status,
                    errorMessage = ocrResult.errorMessage
                )
                OcrStatus.IDLE,
                OcrStatus.PROCESSING -> publishStatus(
                    status = ScreenCaptureMonitorStatus.MONITORING,
                    recognizedText = ocrResult.rawText,
                    ocrStatus = ocrResult.status
                )
            }
            isProcessingFrame.set(false)
        }
    }

    private fun analyzeRecognizedText(rawText: String?, traceId: String) {
        SuperDriverDebugLogger.log(
            "monitor analyze textLength",
            "traceId=$traceId, textLength=${rawText?.length ?: 0}"
        )
        when (val analysis = decisionPipeline.analyzeRecognizedText(rawText, currentConfig, traceId)) {
            TripOfferAnalysisResult.NoText -> {
                SuperDriverDebugLogger.log("monitor analysis", "traceId=$traceId, result=NoText")
                markNoOffer(rawText, OcrStatus.NO_TEXT, traceId)
            }
            TripOfferAnalysisResult.NoTripDetected -> {
                SuperDriverDebugLogger.log("monitor analysis", "traceId=$traceId, result=NoTripDetected")
                markNoOffer(rawText, OcrStatus.TEXT_DETECTED, traceId)
            }
            is TripOfferAnalysisResult.DecisionReady -> {
                noOfferCycles = 0
                handleDecisionCandidate(analysis, traceId)
            }
        }
    }

    private fun handleDecisionCandidate(
        analysis: TripOfferAnalysisResult.DecisionReady,
        traceId: String
    ) {
        val now = System.currentTimeMillis()
        val snapshot = OcrCandidateSnapshot(
            traceId = traceId,
            createdAtMillis = now,
            input = analysis.input,
            result = analysis.result
        )
        val update = candidateBuffer.add(snapshot, now)
        logExpiredCandidates(update.expiredTraceIds)
        SuperDriverDebugLogger.log(
            "monitor candidate buffered",
            "traceId=$traceId, completeFieldCount=${snapshot.completeFieldCount}, " +
                "hasCompleteOverlayData=${snapshot.hasCompleteOverlayData}, " +
                "missingOverlayFields=${snapshot.result.missingOverlayFields()}"
        )
        update.replacedTraceId?.let { replacedTraceId ->
            SuperDriverDebugLogger.log(
                "monitor candidate replaced",
                "traceId=$traceId, replacedTraceId=$replacedTraceId, completeFieldCount=${snapshot.completeFieldCount}"
            )
        }

        if (update.selected != null) {
            SuperDriverDebugLogger.log(
                "monitor candidate complete immediate",
                "traceId=${update.selected.traceId}, completeFieldCount=${update.selected.completeFieldCount}"
            )
            candidateBuffer.clear()
            cancelCandidateBufferFlush()
            processSelectedCandidate(update.selected, update.selectedReason ?: "complete immediate")
            return
        }

        scheduleCandidateBufferFlush(now)
    }

    private fun flushBufferedCandidate() {
        val now = System.currentTimeMillis()
        val selection = candidateBuffer.selectReady(now)
        logExpiredCandidates(selection.expiredTraceIds)

        val selected = selection.selected
        if (selected == null) {
            scheduleCandidateBufferFlush(now)
            return
        }

        processSelectedCandidate(selected, selection.selectedReason ?: "buffer selected")
    }

    private fun processSelectedCandidate(
        snapshot: OcrCandidateSnapshot,
        selectionReason: String
    ) {
        val result = snapshot.result
        val hasCompleteData = snapshot.hasCompleteOverlayData
        val missingOverlayFields = result.missingOverlayFields()
        val shouldShowOverlay = !hasCompleteData ||
            detectionState.shouldShowOverlay(snapshot.input)
        val overlayReason = when {
            !hasCompleteData -> "incomplete data"
            shouldShowOverlay -> "new decision"
            else -> "duplicate complete offer"
        }
        val overlayUpdated = if (shouldShowOverlay) {
            startDecisionOverlay(result, snapshot.traceId, overlayReason)
        } else {
            SuperDriverDebugLogger.log(
                "monitor overlay shown",
                "traceId=${snapshot.traceId}, shown=false, reason=$overlayReason"
            )
            false
        }
        if (overlayUpdated && hasCompleteData) {
            detectionState.markOverlayShown(snapshot.input)
        }
        SuperDriverDebugLogger.log(
            "monitor candidate selected",
            "traceId=${snapshot.traceId}, reason=$selectionReason, completeFieldCount=${snapshot.completeFieldCount}"
        )
        SuperDriverDebugLogger.log(
            "monitor decision summary",
            "traceId=${snapshot.traceId}, hasCompleteData=$hasCompleteData, " +
                "missingOverlayFields=$missingOverlayFields, shouldShowOverlay=$shouldShowOverlay, " +
                "overlayUpdated=$overlayUpdated, overlayReason=$overlayReason, decision=${result.decision}, " +
                "fare=${result.fareAmount}, egpPerKm=${result.egpPerKm}, egpPerHour=${result.egpPerHour}, " +
                "totalKm=${result.totalKm}, totalMinutes=${result.totalMinutes}"
        )
        publishStatus(
            status = if (hasCompleteData) {
                ScreenCaptureMonitorStatus.OFFER_DETECTED
            } else {
                ScreenCaptureMonitorStatus.INCOMPLETE_DATA
            },
            recognizedText = snapshot.input.rawText,
            ocrStatus = OcrStatus.TEXT_DETECTED,
            decisionResult = result,
            overlayUpdated = overlayUpdated
        )
    }

    private fun scheduleCandidateBufferFlush(nowMillis: Long) {
        val delayMillis = candidateBuffer.nextSelectionDelayMillis(nowMillis) ?: return
        cancelCandidateBufferFlush()
        candidateBufferFlushRunnable = Runnable { flushBufferedCandidate() }.also { runnable ->
            captureHandler?.postDelayed(runnable, delayMillis)
        }
    }

    private fun cancelCandidateBufferFlush() {
        candidateBufferFlushRunnable?.let { runnable ->
            captureHandler?.removeCallbacks(runnable)
        }
        candidateBufferFlushRunnable = null
    }

    private fun logExpiredCandidates(traceIds: List<String>) {
        traceIds.forEach { expiredTraceId ->
            SuperDriverDebugLogger.log(
                "monitor candidate expired",
                "traceId=$expiredTraceId"
            )
        }
    }

    private fun markNoOffer(rawText: String?, ocrStatus: OcrStatus, traceId: String? = null) {
        noOfferCycles += 1
        val status = if (noOfferCycles >= NO_OFFER_STATUS_THRESHOLD) {
            detectionState.reset()
            candidateBuffer.clear()
            cancelCandidateBufferFlush()
            hideDecisionOverlay("no active offer")
            ScreenCaptureMonitorStatus.NO_OFFER_DETECTED
        } else {
            ScreenCaptureMonitorStatus.MONITORING
        }
        SuperDriverDebugLogger.log(
            "monitor no offer",
            "traceId=${traceId ?: "none"}, ocrStatus=$ocrStatus, textLength=${rawText?.length ?: 0}, " +
                "noOfferCycles=$noOfferCycles, status=$status"
        )
        publishStatus(
            status = status,
            recognizedText = rawText,
            ocrStatus = ocrStatus
        )
    }

    private fun startDecisionOverlay(
        result: TripDecisionResult,
        traceId: String,
        reason: String
    ): Boolean {
        if (!Settings.canDrawOverlays(this)) {
            SuperDriverDebugLogger.log(
                "monitor overlay shown",
                "traceId=$traceId, shown=false, reason=missing overlay permission"
            )
            publishStatus(
                status = ScreenCaptureMonitorStatus.ERROR,
                errorMessage = "تم اكتشاف عرض، لكن إذن النافذة العائمة غير متاح لعرض القرار."
            )
            return false
        }

        val overlayState = OverlayCardState.fromDecisionResult(result)
        val intent = Intent(this, DriverDecisionOverlayService::class.java).apply {
            putExtra(DriverDecisionOverlayService.EXTRA_DECISION, overlayState.decision.name)
            putExtra(DriverDecisionOverlayService.EXTRA_VISUAL_STATE, overlayState.visualState.name)
            putExtra(DriverDecisionOverlayService.EXTRA_TITLE_TEXT, overlayState.titleText)
            putExtra(DriverDecisionOverlayService.EXTRA_FARE_TEXT, overlayState.fareText)
            putExtra(DriverDecisionOverlayService.EXTRA_EGP_PER_HOUR_TEXT, overlayState.egpPerHourText)
            putExtra(DriverDecisionOverlayService.EXTRA_EGP_PER_KM_TEXT, overlayState.egpPerKmText)
            putExtra(DriverDecisionOverlayService.EXTRA_TOTAL_TIME_TEXT, overlayState.totalTimeText)
            putExtra(DriverDecisionOverlayService.EXTRA_TOTAL_KM_TEXT, overlayState.totalKmText)
            putExtra(DriverDecisionOverlayService.EXTRA_SHORT_REASON, overlayState.shortReason)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        overlayVisible = true
        SuperDriverDebugLogger.log(
            "monitor overlay shown",
            "traceId=$traceId, shown=true, reason=$reason, visualState=${overlayState.visualState}, " +
                "title=${overlayState.titleText}"
        )
        return true
    }

    private fun hideDecisionOverlay(reason: String) {
        if (!overlayVisible) return
        SuperDriverDebugLogger.log("monitor overlay hidden", reason)
        stopService(Intent(this, DriverDecisionOverlayService::class.java))
        overlayVisible = false
        detectionState.reset()
    }

    private fun publishStatus(
        status: ScreenCaptureMonitorStatus,
        recognizedText: String? = null,
        ocrStatus: OcrStatus = OcrStatus.IDLE,
        errorMessage: String? = null,
        decisionResult: TripDecisionResult? = null,
        overlayUpdated: Boolean = false
    ) {
        val intent = Intent(ACTION_MONITOR_RESULT).apply {
            putExtra(EXTRA_MONITOR_STATUS, status.name)
            putExtra(EXTRA_RECOGNIZED_TEXT, recognizedText)
            putExtra(EXTRA_OCR_STATUS, ocrStatus.name)
            putExtra(EXTRA_ERROR_MESSAGE, errorMessage)
            putExtra(EXTRA_OVERLAY_UPDATED, overlayUpdated)
            decisionResult?.writeToIntent(this)
        }
        sendBroadcast(intent.setPackage(packageName))
    }

    private fun TripDecisionResult.writeToIntent(intent: Intent) {
        intent.putExtra(EXTRA_DECISION, decision.name)
        fareAmount?.let { intent.putExtra(EXTRA_FARE_AMOUNT, it) }
        egpPerKm?.let { intent.putExtra(EXTRA_EGP_PER_KM, it) }
        egpPerHour?.let { intent.putExtra(EXTRA_EGP_PER_HOUR, it) }
        estimatedCost?.let { intent.putExtra(EXTRA_ESTIMATED_COST, it) }
        estimatedNetProfit?.let { intent.putExtra(EXTRA_ESTIMATED_NET_PROFIT, it) }
        totalKm?.let { intent.putExtra(EXTRA_TOTAL_KM, it) }
        totalMinutes?.let { intent.putExtra(EXTRA_TOTAL_MINUTES, it) }
        intent.putStringArrayListExtra(EXTRA_REJECTION_REASONS, ArrayList(rejectionReasons))
        intent.putStringArrayListExtra(EXTRA_REVIEW_REASONS, ArrayList(reviewReasons))
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Monitoreo de pantalla",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "يراقب الشاشة باستخدام التقاط مصرح به لاكتشاف العروض."
            setShowBadge(false)
        }

        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun startMonitorForeground() {
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
        val stopIntent = Intent(this, ScreenCaptureMonitorService::class.java).apply {
            action = ACTION_STOP_MONITORING
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            STOP_REQUEST_CODE,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("Monitoreo de pantalla activo")
            .setContentText("يحلل العروض باستخدام التقاط شاشة مصرح به.")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "إيقاف",
                stopPendingIntent
            )
            .build()
    }

    private fun releaseCaptureResources() {
        frameGate.reset()
        detectionState.reset()
        isProcessingFrame.set(false)
        overlayVisible = false
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
        captureHandler = null
    }

    companion object {
        const val ACTION_START_MONITORING = "com.superdriver.app.capture.START_MONITORING"
        const val ACTION_STOP_MONITORING = "com.superdriver.app.capture.STOP_MONITORING"
        const val ACTION_MONITOR_RESULT = "com.superdriver.app.capture.MONITOR_RESULT"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        const val EXTRA_MONITOR_STATUS = "extra_monitor_status"
        const val EXTRA_RECOGNIZED_TEXT = "extra_recognized_text"
        const val EXTRA_OCR_STATUS = "extra_ocr_status"
        const val EXTRA_ERROR_MESSAGE = "extra_error_message"
        const val EXTRA_OVERLAY_UPDATED = "extra_overlay_updated"
        const val EXTRA_DECISION = "extra_decision"
        const val EXTRA_FARE_AMOUNT = "extra_fare_amount"
        const val EXTRA_EGP_PER_KM = "extra_egp_per_km"
        const val EXTRA_EGP_PER_HOUR = "extra_egp_per_hour"
        const val EXTRA_ESTIMATED_COST = "extra_estimated_cost"
        const val EXTRA_ESTIMATED_NET_PROFIT = "extra_estimated_net_profit"
        const val EXTRA_TOTAL_KM = "extra_total_km"
        const val EXTRA_TOTAL_MINUTES = "extra_total_minutes"
        const val EXTRA_REJECTION_REASONS = "extra_rejection_reasons"
        const val EXTRA_REVIEW_REASONS = "extra_review_reasons"

        private const val NOTIFICATION_CHANNEL_ID = "screen_capture_monitor_service"
        private const val NOTIFICATION_ID = 4301
        private const val STOP_REQUEST_CODE = 4302
        private const val MAX_CAPTURE_WIDTH = 720
        private const val NO_OFFER_STATUS_THRESHOLD = 2
        private const val IMAGE_BUFFER_SIZE = 1
        private const val FALLBACK_CAPTURE_SIZE = 1

        fun buildStartIntent(
            context: Context,
            resultCode: Int,
            resultData: Intent
        ): Intent {
            return Intent(context, ScreenCaptureMonitorService::class.java).apply {
                action = ACTION_START_MONITORING
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, resultData)
            }
        }
    }
}

private fun Image.toMonitorBitmap(): Bitmap {
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

private fun Bitmap.toMonitorFrameSignature(
    columns: Int = 12,
    rows: Int = 10,
    cropTopRatio: Float = 0.42f
): FrameSignature {
    val startY = (height * cropTopRatio).roundToInt().coerceIn(0, height - 1)
    val roiHeight = (height - startY).coerceAtLeast(1)
    val values = ArrayList<Int>(columns * rows)

    repeat(rows) { row ->
        repeat(columns) { column ->
            val x = (((column + 0.5f) / columns) * width)
                .roundToInt()
                .coerceIn(0, width - 1)
            val y = startY + ((((row + 0.5f) / rows) * roiHeight)
                .roundToInt()
                .coerceIn(0, roiHeight - 1))
            values += getPixel(x, y).luminance()
        }
    }

    return FrameSignature(values)
}

private fun Int.luminance(): Int {
    return ((Color.red(this) * 0.299f) +
        (Color.green(this) * 0.587f) +
        (Color.blue(this) * 0.114f))
        .roundToInt()
        .coerceIn(0, 255)
}
