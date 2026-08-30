package com.superdriver.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors
import java.util.regex.Pattern

class UberAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile var latestText: String = ""
            private set
        // Last raw text produced by the OCR pass for the debug screen.
        @Volatile var latestOcrText: String = ""
            private set
        @Volatile private var instance: UberAccessibilityService? = null

        fun setMonitoringEnabled(enabled: Boolean) {
            instance?.applyMonitoringState(enabled)
        }
    }

    private var overlayView: View? = null
    private var statusOverlayView: TextView? = null
    private var windowManager: WindowManager? = null
    private var statusParams: WindowManager.LayoutParams? = null
    private var resultParams: WindowManager.LayoutParams? = null
    private var lastSignature = ""
    private var lastShownAt = 0L
    private var screenshotInProgress = false
    private val ocrExecutor = Executors.newSingleThreadExecutor()
    private var textRecognizer: TextRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val clearStaleResult = Runnable { hideOverlay() }

    private val number = "([0-9٠-٩۰-۹]+(?:[.,٫][0-9٠-٩۰-۹]+)?)"

    // Trip distance patterns – strong labels first, then parenthesised
    // (المسافة 4.5 كلم), then generic "المسافة 4.5 كلم", then the
    // "مشوار لمدة X د (المسافة X كلم)" duration block. These win
    // outright when present.
    private val tripDistanceLabeledStrong = Pattern.compile(
        "(?:مسافة\\s+الرحلة|trip\\s+distance|route\\s+distance)\\s*[:：\\-]?\\s*" + number +
            "\\s*(?:كم|كلم|km|ميل|mi)",
        Pattern.CASE_INSENSITIVE
    )
    private val tripDistanceParen = Pattern.compile(
        "\\(\\s*(?:المسافة|مسافة|distance)\\s+" + number +
            "\\s*(?:كم|كلم|km|ميل|mi)\\s*\\)",
        Pattern.CASE_INSENSITIVE
    )
    private val tripDistanceAny = Pattern.compile(
        "(?:المسافة|مسافة|distance|route)\\s*[:：\\-]?\\s*" + number +
            "\\s*(?:كم|كلم|km|ميل|mi)",
        Pattern.CASE_INSENSITIVE
    )
    private val tripInDurationAr = Pattern.compile(
        "مشوار\\s+لمدة\\s+[0-9٠-٩۰-۹]+\\s*(?:د(?:قيقة)?|دق|h|hr|ساعة|س(?:اعة)?)" +
            "\\s*\\(\\s*(?:المسافة|مسافة|distance)\\s+" + number +
            "\\s*(?:كم|كلم|km|ميل|mi)\\s*\\)",
        Pattern.CASE_INSENSITIVE
    )

    // Pickup labels are kept separate from trip-distance labels so the
    // parser does not accidentally swallow the trip distance.
    private val pickupArOnDistance = Pattern.compile(
        "على\\s+بعد\\s+[0-9٠-٩۰-۹]+\\s*د\\s*" +
            "\\(?\\s*" + number + "\\s*\\)?\\s*(?:كم|كلم|km|ميل|mi)",
        Pattern.CASE_INSENSITIVE
    )
    private val pickupArMinutesOnly = Pattern.compile(
        "على\\s+بعد\\s+[0-9٠-٩۰-۹]+\\s*(?:د(?:قيقة)?|دق|د)\\b",
        Pattern.CASE_INSENSITIVE
    )
    private val pickupArYibad = Pattern.compile(
        "يبعد\\s+" + number + "\\s*(?:كم|كلم|km|ميل|mi)",
        Pattern.CASE_INSENSITIVE
    )
    private val pickupArBaad = Pattern.compile(
        "بعد\\s+عنك\\s+" + number + "\\s*(?:كم|كلم|km|ميل|mi)",
        Pattern.CASE_INSENSITIVE
    )
    private val pickupEn = Pattern.compile(
        "(?:pickup|pick[\\s-]?up)\\s*(?:distance)?\\s*[:：\\-]?\\s*" + number +
            "\\s*(?:كم|كلم|km|ميل|mi)",
        Pattern.CASE_INSENSITIVE
    )

    // Bare "X km" fallback where the trip distance is next to the price.
    private val bareDistance = Pattern.compile(
        "~?\\s*" + number + "\\s*(?:كم|كلم|km|ميل|mi)",
        Pattern.CASE_INSENSITIVE
    )

    // Price patterns – labelled fare first, then a bare currency amount.
    private val priceByLabel = Pattern.compile(
        "(?:السعر|سعر\\s+الرحلة|المجموع|الإجمالي|المبلغ|القبول\\s+مقابل|" +
            "total|fare|trip\\s+price|price)\\s*[:：\\-]?\\s*" + number +
            "\\s*(?:ج\\s*\\.?\\s*م|ج\\.م|EGP|جنيه|جنيه\\s*مصري)",
        Pattern.CASE_INSENSITIVE
    )
    private val priceAfterNumber = Pattern.compile(
        "(?<![0-9٠-٩۰-۹])" + number +
            "\\s*(?:ج\\s*\\.?\\s*م|ج\\.م|EGP|جنيه|جنيه\\s*مصري)",
        Pattern.CASE_INSENSITIVE
    )

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 120
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        applyMonitoringState(isMonitoringStored())
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return
        if (!isMonitoringStored()) {
            hideOverlay()
            return
        }
        if (!isSupportedRideApp(packageName) || !isAppEnabled(packageName)) {
            // Ignore system/status-bar accessibility events while the active window
            // is still a supported ride app; otherwise the result would flash.
            val activePackage = rootInActiveWindow?.packageName?.toString()
            if (activePackage == null || !isSupportedRideApp(activePackage)) hideOverlay()
            return
        }

        val text = collectCurrentWindowText()
        latestText = text
        val trip = parseTrip(text, packageName)
        if (trip != null) {
            mainHandler.removeCallbacks(clearStaleResult)
            showResult(trip.first, trip.second, packageName)
        } else {
            // Some live-offer distances are painted in a canvas/WebView and do not
            // exist in the Accessibility tree. OCR the current display as a fallback.
            requestLiveOcr(packageName, text)
            mainHandler.removeCallbacks(clearStaleResult)
            mainHandler.postDelayed(clearStaleResult, 5000L)
        }
    }

    private var lastOcrAt = 0L
    private var lastPolledText = ""
    private val pollIntervalMs = 1500L
    private val pollRunnable: Runnable = object : Runnable {
        override fun run() {
            try {
                pollForUpdates()
            } finally {
                if (isMonitoringStored()) {
                    mainHandler.postDelayed(this, pollIntervalMs)
                }
            }
        }
    }

    /**
     * Polls the active window's accessibility tree every
     * [pollIntervalMs] and re-runs the parser when the text changes.
     * Necessary because Uber's live-offer card updates in place – the
     * Accessibility tree may not fire a TYPE_WINDOW_CONTENT_CHANGED
     * event for the new offer, so without polling we would only see
     * the historical trip the user happened to have open when the
     * service started.
     */
    private fun pollForUpdates() {
        if (!isMonitoringStored()) return
        // Look at EVERY window for a com.ubercab.driver or
        // com.uber.client package – not just the active one, because
        // the live offer card may live in a system-managed window
        // that the activity itself never sees.
        val packageName = findUberPackageInWindows() ?: return
        val text = collectAllTextForPackage(packageName)
        if (text.isBlank() || text == lastPolledText) return
        lastPolledText = text
        latestText = text
        val trip = parseTrip(text, packageName) ?: return
        mainHandler.removeCallbacks(clearStaleResult)
        showResult(trip.first, trip.second, packageName)
    }

    /**
     * Scans the system windows (and the active window) for one whose
     * root belongs to Uber (driver or rider). Returns the package
     * name or null if none was found.
     */
    private fun findUberPackageInWindows(): String? {
        val activePkg = rootInActiveWindow?.packageName?.toString()
        if (activePkg == "com.ubercab.driver" || activePkg == "com.uber.client") {
            return activePkg
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return null
        try {
            for (window in windows) {
                val pkg = window.root?.packageName?.toString()
                window.root?.recycle()
                if (pkg == "com.ubercab.driver" || pkg == "com.uber.client") {
                    return pkg
                }
            }
        } catch (_: Exception) { }
        return null
    }

    /**
     * Collects text from every accessibility window whose root
     * belongs to the given package. This is broader than
     * [collectCurrentWindowText] and is what the polling loop uses
     * so we never miss a window that the system shows but the
     * activity does not own.
     */
    private fun collectAllTextForPackage(targetPackage: String): String {
        val out = StringBuilder()
        // 1. Active window first.
        val activeRoot = rootInActiveWindow
        if (activeRoot != null && activeRoot.packageName?.toString() == targetPackage) {
            try {
                collectText(activeRoot, out, 0)
            } finally {
                activeRoot.recycle()
            }
        } else {
            activeRoot?.recycle()
        }
        // 2. Every other window.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                for (window in windows) {
                    val root = window.root ?: continue
                    val pkg = root.packageName?.toString()
                    if (pkg != targetPackage) {
                        root.recycle()
                        continue
                    }
                    try {
                        collectText(root, out, 0)
                    } finally {
                        root.recycle()
                    }
                }
            } catch (_: Exception) { }
        }
        return out.toString().trim()
    }

    private fun startPolling() {
        mainHandler.removeCallbacks(pollRunnable)
        mainHandler.postDelayed(pollRunnable, pollIntervalMs)
    }

    private fun stopPolling() {
        mainHandler.removeCallbacks(pollRunnable)
    }

    private fun requestLiveOcr(packageName: String, accessibilityText: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || screenshotInProgress) return
        // Throttle: max one OCR pass per 3 seconds – screenshot +
        // recognition are expensive and the screen doesn't change that
        // fast while an offer card is on screen.
        val now = System.currentTimeMillis()
        if (now - lastOcrAt < 3000L) return
        lastOcrAt = now
        screenshotInProgress = true
        try {
            takeScreenshot(
            android.view.Display.DEFAULT_DISPLAY,
            ocrExecutor,
            object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    val buffer = result.hardwareBuffer ?: run {
                        screenshotInProgress = false
                        return
                    }
                    val bitmap = try {
                        Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                    } catch (_: Throwable) {
                        null
                    } finally {
                        buffer.close()
                    }
                    if (bitmap == null) {
                        screenshotInProgress = false
                        return
                    }
                    val image = InputImage.fromBitmap(bitmap, 0)
                    val recognizer = try {
                        textRecognizer ?: TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).also { textRecognizer = it }
                    } catch (_: Throwable) {
                        bitmap.recycle()
                        screenshotInProgress = false
                        return
                    }
                    recognizer.process(image)
                        .addOnSuccessListener(ocrExecutor) { recognized ->
                            val combined = "$accessibilityText ${recognized.text}"
                            // Publish the OCR result with bounding-box
                            // info so the debug screen can show the
                            // on-screen positions of scale bars, the
                            // pickup address, the destination, etc.
                            val sb = StringBuilder()
                            sb.append("=== OCR TEXT ===\n")
                            sb.append(recognized.text)
                            sb.append("\n\n=== OCR ELEMENTS (x, y, w, h, text) ===\n")
                            for (block in recognized.textBlocks) {
                                for (line in block.lines) {
                                    val r = line.boundingBox
                                    if (r == null) {
                                        sb.append("(no box) ").append(line.text).append('\n')
                                    } else {
                                        sb.append("(x=${r.left},y=${r.top},w=${r.width()},h=${r.height()}) ")
                                            .append(line.text).append('\n')
                                    }
                                }
                            }
                            latestOcrText = sb.toString()
                            val trip = parseTrip(combined, packageName)
                            if (trip != null) {
                                mainHandler.post {
                                    mainHandler.removeCallbacks(clearStaleResult)
                                    showResult(trip.first, trip.second, packageName)
                                }
                            }
                            bitmap.recycle()
                            screenshotInProgress = false
                        }
                        .addOnFailureListener(ocrExecutor) {
                            bitmap.recycle()
                            screenshotInProgress = false
                        }
                }

                override fun onFailure(errorCode: Int) {
                    screenshotInProgress = false
                }
            }
        )
        } catch (_: Throwable) {
            screenshotInProgress = false
        }
    }

    private fun isSupportedRideApp(packageName: String): Boolean {
        // Uber-only mode while the Uber flow is being tuned.
        return packageName == "com.ubercab.driver" ||
            packageName == "com.uber.client"
    }

    private fun settingsKey(packageName: String): String = "uber"

    private fun isAppEnabled(packageName: String): Boolean {
        val prefs = getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE)
        return prefs.all["flutter.enabled_${settingsKey(packageName)}"] as? Boolean ?: true
    }

    private fun collectCurrentWindowText(): String {
        val out = StringBuilder()
        val activeRoot = rootInActiveWindow
        if (activeRoot != null) {
            try {
                collectText(activeRoot, out, 0)
            } finally {
                activeRoot.recycle()
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                for (window in windows) {
                    val root = window.root ?: continue
                    try {
                        collectText(root, out, 0)
                    } finally {
                        root.recycle()
                    }
                }
            } catch (_: Exception) { }
        }
        return out.toString().trim()
    }

    private fun collectText(node: AccessibilityNodeInfo, out: StringBuilder, depth: Int) {
        if (depth > 80) return
        node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { out.append(it).append(' ') }
        node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { out.append(it).append(' ') }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            node.hintText?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { out.append(it).append(' ') }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                collectText(child, out, depth + 1)
            } finally {
                child.recycle()
            }
        }
    }

    private fun parseTrip(raw: String, packageName: String): Pair<Double, Double>? {
        val text = normalizeDigits(raw)
        val price = findPrice(text) ?: return null
        // Pickup distance is ALWAYS added to the trip distance so the
        // price/km reflects the total distance the driver will travel
        // (pickup + trip). The per-app includePickupDistance setting is
        // kept for back-compat with the settings UI but is no longer
        // consulted here.
        val distance = extractTripDistance(text, includePickupDistance = true) ?: return null
        if (price <= 0.0 || distance <= 0.0) return null
        return Pair(price, distance)
    }

    /**
     * Returns the total distance the driver will travel to fulfil this
     * trip: the pickup distance to the rider PLUS the trip distance to
     * the destination. If only one is available, that value is used as-is.
     */
    private fun extractTripDistance(text: String, includePickupDistance: Boolean): Double? {
        val pickup = pickupMax(text)

        // 1. Strong trip labels win outright.
        maxFrom(tripDistanceLabeledStrong, text)?.let { return combineWithPickup(it, pickup, includePickupDistance) }
        maxFrom(tripDistanceParen, text)?.let { return combineWithPickup(it, pickup, includePickupDistance) }
        maxFrom(tripDistanceAny, text)?.let { return combineWithPickup(it, pickup, includePickupDistance) }

        // 2. Uber's duration block – the inner distance is the trip.
        maxFrom(tripInDurationAr, text)?.let { return combineWithPickup(it, pickup, includePickupDistance) }

        // 3. Bare distance fallback, used only when it is not part of a pickup label.
        val pricePos = firstPricePos(text)
        val bare = pickBareClosestToPrice(text, pricePos, pickup)
        if (bare != null) {
            return combineWithPickup(bare, pickup, includePickupDistance)
        }

        // 4. Last resort: if we have a pickup but no trip, use the pickup.
        if (includePickupDistance) return pickup
        return null
    }

    /**
     * Adds the pickup distance to the trip distance when the user opted
     * in (or by default — pickup is now always added to the trip).
     */
    private fun combineWithPickup(trip: Double, pickup: Double?, includePickupDistance: Boolean): Double {
        if (includePickupDistance && pickup != null && pickup > 0) return trip + pickup
        return trip
    }

    private data class Hit(val value: Double, val start: Int, val end: Int)

    private fun collect(re: Pattern, text: String): List<Hit> {
        val out = mutableListOf<Hit>()
        val m = re.matcher(text)
        while (m.find()) {
            val v = m.group(1)?.replace(',', '.')?.toDoubleOrNull()
            if (v != null && v > 0) out.add(Hit(v, m.start(), m.end()))
        }
        return out
    }

    private fun maxFrom(re: Pattern, text: String): Double? {
        var best: Double? = null
        for (h in collect(re, text)) {
            if (best == null || h.value > best) best = h.value
        }
        return best
    }

    private fun pickupMax(text: String): Double? {
        var best: Double? = null
        // Patterns that already include the number
        for (re in arrayOf(pickupArOnDistance, pickupArYibad, pickupArBaad, pickupEn)) {
            for (h in collect(re, text)) {
                if (best == null || h.value > best) best = h.value
            }
        }
        // "على بعد 5 د" alone (without the distance) – we can't read the
        // distance from it directly, so skip.
        return best
    }

    private fun firstPricePos(text: String): Int? {
        val pm = priceByLabel.matcher(text)
        if (pm.find()) return pm.start()
        val am = priceAfterNumber.matcher(text)
        if (am.find()) return am.start()
        return null
    }

    private fun pickBareClosestToPrice(text: String, pricePos: Int?, ignorePickup: Double?): Double? {
        val pickupRanges = collect(pickupArOnDistance, text) +
            collect(pickupArYibad, text) +
            collect(pickupArBaad, text) +
            collect(pickupEn, text)
        val bare = collect(bareDistance, text).filter { b ->
            pickupRanges.none { it.start < b.end && b.start < it.end }
        }
        if (bare.isEmpty()) return null
        if (ignorePickup != null) {
            // The caller asked for pickup+pickup-distance mode; the bare
            // fallback is irrelevant.
            return null
        }
        if (pricePos == null) {
            // No price anchor – take the smallest remaining distance.
            var v = bare.first().value
            for (h in bare) if (h.value < v) v = h.value
            return v
        }
        var best = bare.first()
        for (b in bare) {
            val gapBest = kotlin.math.abs(best.start - pricePos)
            val gapHere = kotlin.math.abs(b.start - pricePos)
            if (gapHere < gapBest || (gapHere == gapBest && b.value < best.value)) {
                best = b
            }
        }
        return best.value
    }

    private fun findPrice(text: String): Double? {
        val labeled = priceByLabel.matcher(text)
        if (labeled.find()) return labeled.group(1)?.replace(',', '.')?.toDoubleOrNull()
        // No labelled fare – use the FIRST bare "<number> EGP" match.
        // Taking the first match reads the headline offer price.
        val after = priceAfterNumber.matcher(text)
        if (after.find()) return after.group(1)?.replace(',', '.')?.toDoubleOrNull()
        return null
    }

    private fun normalizeDigits(value: String): String {
        val arabic = "٠١٢٣٤٥٦٧٨٩"
        val persian = "۰۱۲۳۴۵۶۷۸۹"
        val out = StringBuilder(value.length)
        for (ch in value) {
            val a = arabic.indexOf(ch)
            val p = persian.indexOf(ch)
            when {
                a >= 0 -> out.append(('0'.code + a).toChar())
                p >= 0 -> out.append(('0'.code + p).toChar())
                ch == '،' || ch == '٫' -> out.append('.')
                ch == '٬' -> Unit
                else -> out.append(ch)
            }
        }
        return out.toString()
    }

    private fun isMonitoringStored(): Boolean =
        getSharedPreferences("super_driver", MODE_PRIVATE).getBoolean("monitoring_enabled", false)

    private fun applyMonitoringState(enabled: Boolean) {
        showStatusOverlay(enabled)
        if (!enabled) {
            overlayView?.let { view ->
                try {
                    if (view.parent != null) windowManager?.removeView(view)
                } catch (_: Exception) { }
                overlayView = null
                resultParams = null
            }
            stopPolling()
        } else {
            startPolling()
        }
    }

    private fun showStatusOverlay(enabled: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) return
        val view = statusOverlayView ?: TextView(this).apply {
            text = "SD"
            textSize = 15f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(18, 14, 18, 14)
            elevation = 12f
        }.also { statusOverlayView = it }
        view.text = if (enabled) "SD ✓" else "SD"
        view.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (enabled) Color.rgb(20, 150, 75) else Color.rgb(205, 45, 45))
        }
        if (view.parent == null) {
            val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
            val saved = getSharedPreferences("super_driver", MODE_PRIVATE)
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                windowType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                y = saved.getInt("status_y", 180)
                x = saved.getInt("status_x", 18)
            }
            statusParams = params
            attachDrag(view, params, "status")
            try {
                windowManager?.addView(view, params)
            } catch (_: Exception) {
                statusOverlayView = null
            }
        }
    }

    private fun showResult(price: Double, distance: Double, packageName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            hideOverlay()
            return
        }

        val prefs = getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE)
        val settingsKey = settingsKey(packageName)
        val minPrice = readDouble(
            prefs.all["flutter.minPrice_$settingsKey"] ?: prefs.all["flutter.minPrice"],
            7.5
        ).coerceAtLeast(0.0)
        val discount = readDouble(
            prefs.all["flutter.discount_$settingsKey"]
                ?: prefs.all["flutter.discount_${settingsKey}_percent"]
                ?: prefs.all["flutter.uberDiscount"],
            0.0
        ).coerceIn(0.0, 100.0)

        // Apply the company percentage to the fare first, then divide by
        // the configured trip distance. Pickup distance is excluded by default.
        val fareAfterDiscount = price * (1.0 - discount / 100.0)
        val net = fareAfterDiscount / distance
        val suitable = net >= minPrice
        val appLabel = "Uber"

        val signature = "$packageName|${fmt(price)}|${fmt(distance)}|${fmt(net)}|$suitable"
        val now = System.currentTimeMillis()
        if (signature == lastSignature && now - lastShownAt < 1000L) return
        lastSignature = signature
        lastShownAt = now

        val root = overlayView as? LinearLayout ?: createOverlay().also { overlayView = it }
        val title = root.findViewWithTag<TextView>("title")
        val details = root.findViewWithTag<TextView>("details")

        title.text = if (suitable) "✓ مناسب • $appLabel" else "✕ غير مناسب • $appLabel"
        details.text = if (suitable) {
            "السعر مناسب\n${fmt(net)} ج.م/كم بعد الخصم"
        } else {
            "السعر غير مناسب\nالمطلوب ${fmt(minPrice)} ج.م/كم\nالحالي ${fmt(net)} ج.م/كم بعد الخصم"
        }
        title.setTextColor(Color.WHITE)
        details.setTextColor(Color.WHITE)
        root.setBackgroundColor(if (suitable) Color.rgb(20, 132, 72) else Color.rgb(198, 48, 48))
        root.visibility = View.VISIBLE

        if (root.parent == null) {
            val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            }
            val saved = getSharedPreferences("super_driver", MODE_PRIVATE)
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                windowType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                y = saved.getInt("result_y", 130)
                x = saved.getInt("result_x", 16)
            }
            resultParams = params
            attachDrag(root, params, "result")
            try {
                windowManager?.addView(root, params)
            } catch (_: Exception) {
                overlayView = null
            }
        }
    }

    private fun createOverlay(): LinearLayout {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(26, 14, 26, 14)
            elevation = 12f
        }
        val title = TextView(this).apply {
            tag = "title"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        val details = TextView(this).apply {
            tag = "details"
            textSize = 14f
            typeface = Typeface.DEFAULT
            gravity = Gravity.CENTER
            setPadding(0, 5, 0, 0)
        }
        container.addView(title, LinearLayout.LayoutParams(-2, -2))
        container.addView(details, LinearLayout.LayoutParams(-2, -2))
        return container
    }

    private fun attachDrag(view: View, params: WindowManager.LayoutParams, key: String) {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    if (kotlin.math.abs(dx) > 4 || kotlin.math.abs(dy) > 4) moved = true
                    params.x = (startX + dx).coerceAtLeast(0)
                    params.y = (startY + dy).coerceAtLeast(0)
                    try { windowManager?.updateViewLayout(view, params) } catch (_: Exception) { }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (moved) {
                        getSharedPreferences("super_driver", MODE_PRIVATE).edit()
                            .putInt("${key}_x", params.x).putInt("${key}_y", params.y).apply()
                    }
                    true
                }
                else -> true
            }
        }
    }

    private fun fmt(value: Double): String = String.format(Locale.US, "%.2f", value)

    private fun readDouble(value: Any?, fallback: Double): Double = when (value) {
        is Number -> value.toDouble()
        is String -> value.replace("%", "").replace(',', '.').trim().toDoubleOrNull() ?: fallback
        else -> fallback
    }

    private fun hideOverlay() {
        mainHandler.removeCallbacks(clearStaleResult)
        overlayView?.let { view ->
            try {
                if (view.parent != null) windowManager?.removeView(view)
            } catch (_: Exception) {
                // The Android window may already have been removed.
            }
        }
        overlayView = null
        lastSignature = ""
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        hideOverlay()
        textRecognizer?.close()
        textRecognizer = null
        ocrExecutor.shutdownNow()
        statusOverlayView?.let { view ->
            try {
                if (view.parent != null) windowManager?.removeView(view)
            } catch (_: Exception) { }
        }
        statusOverlayView = null
        latestText = ""
        latestOcrText = ""
        instance = null
        super.onDestroy()
    }
}
