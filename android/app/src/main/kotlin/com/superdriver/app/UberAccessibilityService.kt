package com.superdriver.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
import java.util.regex.Pattern

class UberAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile var latestText: String = ""
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
    private val mainHandler = Handler(Looper.getMainLooper())
    private val clearStaleResult = Runnable { hideOverlay() }

    private val priceAfterNumber = Pattern.compile(
        "(?<![0-9٠-٩۰-۹])([0-9٠-٩۰-۹]+(?:[.,٫][0-9٠-٩۰-۹]+)?)\\s*(?:ج\\s*\\.?\\s*م|ج\\.م|EGP|جنيه|جنيه\\s*مصري)",
        Pattern.CASE_INSENSITIVE
    )
    private val priceBeforeNumber = Pattern.compile(
        "(?:ج\\s*\\.?\\s*م|ج\\.م|EGP|جنيه|جنيه\\s*مصري)\\s*([0-9٠-٩۰-۹]+(?:[.,٫][0-9٠-٩۰-۹]+)?)",
        Pattern.CASE_INSENSITIVE
    )
    private val priceByLabel = Pattern.compile(
        "(?:السعر|سعر الرحلة|المجموع|الإجمالي|المبلغ|total|fare|trip price)\\s*[:：]?\\s*([0-9٠-٩۰-۹]+(?:[.,٫][0-9٠-٩۰-۹]+)?)",
        Pattern.CASE_INSENSITIVE
    )
    private val tripDistance = Pattern.compile(
        "(?:المسافة|مسافة|distance|trip distance|miles|mi|km)\\s*[:：]?\\s*([0-9٠-٩۰-۹]+(?:[.,٫][0-9٠-٩۰-۹]+)?)\\s*(?:كم|كلم|km|ميل|mi)?",
        Pattern.CASE_INSENSITIVE
    )
    private val fallbackDistance = Pattern.compile(
        "([0-9٠-٩۰-۹]+(?:[.,٫][0-9٠-٩۰-۹]+)?)\\s*(?:كم|كلم|km|ميل|mi)",
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
            // The live offer can be populated through several accessibility events.
            // Give it time to finish loading, then remove stale data if no valid offer appears.
            mainHandler.removeCallbacks(clearStaleResult)
            mainHandler.postDelayed(clearStaleResult, 5000L)
        }
    }

    private fun isSupportedRideApp(packageName: String): Boolean {
        return packageName == "com.ubercab.driver" ||
            packageName == "com.uber.client" ||
            packageName == "com.didiglobal.driver" ||
            packageName == "sinet.startup.inDriver"
    }

    private fun settingsKey(packageName: String): String = when (packageName) {
        "sinet.startup.inDriver" -> "indrive"
        "com.didiglobal.driver" -> "didi"
        else -> "uber"
    }

    private fun isAppEnabled(packageName: String): Boolean {
        val prefs = getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE)
        return prefs.all["flutter.enabled_${settingsKey(packageName)}"] as? Boolean ?: true
    }

    private fun collectCurrentWindowText(): String {
        val out = StringBuilder()
        rootInActiveWindow?.let { root -> collectText(root, out, 0) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                for (window in windows) {
                    val root = window.root ?: continue
                    collectText(root, out, 0)
                    root.recycle()
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
        val prefs = getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE)
        val includePickupDistance = prefs.all["flutter.includePickupDistance_${settingsKey(packageName)}"] as? Boolean ?: false
        val distances = mutableListOf<Double>()
        val labeled = tripDistance.matcher(text)
        while (labeled.find()) labeled.group(1)?.replace(',', '.')?.toDoubleOrNull()?.let { distances.add(it) }
        val fallback = fallbackDistance.matcher(text)
        while (fallback.find()) fallback.group(1)?.replace(',', '.')?.toDoubleOrNull()?.let { distances.add(it) }
        // Uber, inDrive, and DiDi may expose pickup and trip distances together.
        // By default use only the trip distance; the page option can include pickup distance.
        val tripDistance = distances.lastOrNull()
        val distance = if (includePickupDistance) distances.sum() else tripDistance
        if (price <= 0.0 || distance == null || distance <= 0.0) return null
        return Pair(price, distance)
    }

    private fun findPrice(text: String): Double? {
        val labeled = priceByLabel.matcher(text)
        if (labeled.find()) return labeled.group(1)?.replace(',', '.')?.toDoubleOrNull()
        val after = priceAfterNumber.matcher(text)
        if (after.find()) return after.group(1)?.replace(',', '.')?.toDoubleOrNull()
        val before = priceBeforeNumber.matcher(text)
        if (before.find()) return before.group(1)?.replace(',', '.')?.toDoubleOrNull()
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
        if (!enabled) overlayView?.let { view ->
            try {
                if (view.parent != null) windowManager?.removeView(view)
            } catch (_: Exception) { }
            overlayView = null
            resultParams = null
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
        val prefsKey = "flutter.includePickupDistance_$settingsKey"
        val includePickupDistance = prefs.all[prefsKey] as? Boolean ?: false
        val minPrice = readDouble(
            prefs.all["flutter.minPrice_$settingsKey"] ?: prefs.all["flutter.minPrice"],
            7.5
        ).coerceAtLeast(0.0)
        val discount = readDouble(
            prefs.all["flutter.discount_$settingsKey"] ?: prefs.all["flutter.uberDiscount"],
            0.0
        ).coerceIn(0.0, 100.0)

        val gross = price / distance
        val net = gross * (1.0 - discount / 100.0)
        val suitable = net >= minPrice
        val appLabel = when (packageName) {
            "com.didiglobal.driver" -> "DiDi"
            "sinet.startup.inDriver" -> "inDrive"
            else -> "Uber"
        }

        val signature = "$packageName|${fmt(price)}|${fmt(distance)}|${fmt(net)}|$suitable"
        val now = System.currentTimeMillis()
        if (signature == lastSignature && now - lastShownAt < 1000L) return
        lastSignature = signature
        lastShownAt = now

        val root = overlayView as? LinearLayout ?: createOverlay().also { overlayView = it }
        val title = root.findViewWithTag<TextView>("title")
        val details = root.findViewWithTag<TextView>("details")

        title.text = if (suitable) "✓ مناسب • $appLabel" else "✕ غير مناسب • $appLabel"
        details.text = "${fmt(price)} ج.م  •  ${fmt(distance)} كم\n${fmt(gross)} ج.م/كم  •  بعد الخصم ${fmt(net)} ج.م/كم"
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
        is String -> value.replace(',', '.').toDoubleOrNull() ?: fallback
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
        statusOverlayView?.let { view ->
            try {
                if (view.parent != null) windowManager?.removeView(view)
            } catch (_: Exception) { }
        }
        statusOverlayView = null
        latestText = ""
        instance = null
        super.onDestroy()
    }
}
