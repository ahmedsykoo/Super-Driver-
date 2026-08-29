package com.superdriver.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.provider.Settings
import android.view.Gravity
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
    private var windowManager: WindowManager? = null
    private var lastSignature = ""
    private var lastShownAt = 0L

    private val priceAfterNumber = Pattern.compile(
        "(?<![0-9٠-٩۰-۹])([0-9٠-٩۰-۹]+(?:[.,٫][0-9٠-٩۰-۹]+)?)\\s*(?:ج\\s*\\.?\\s*م|ج\\.م|EGP|جنيه)",
        Pattern.CASE_INSENSITIVE
    )
    private val priceBeforeNumber = Pattern.compile(
        "(?:ج\\s*\\.?\\s*م|ج\\.م|EGP|جنيه)\\s*([0-9٠-٩۰-۹]+(?:[.,٫][0-9٠-٩۰-۹]+)?)",
        Pattern.CASE_INSENSITIVE
    )
    private val tripDistance = Pattern.compile(
        "(?:المسافة|مسافة|distance|trip distance)\\s*[:：]?\\s*([0-9٠-٩۰-۹]+(?:[.,٫][0-9٠-٩۰-۹]+)?)\\s*(?:كم|كلم|km)",
        Pattern.CASE_INSENSITIVE
    )
    private val fallbackDistance = Pattern.compile(
        "([0-9٠-٩۰-۹]+(?:[.,٫][0-9٠-٩۰-۹]+)?)\\s*(?:كم|كلم|km)",
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
        if (!isSupportedRideApp(packageName) || !isMonitoringStored()) {
            hideOverlay()
            return
        }

        val root = rootInActiveWindow ?: return
        val text = StringBuilder()
        collectText(root, text, 0)
        latestText = text.toString().trim()

        val trip = parseTrip(latestText) ?: run {
            // Do not leave an old offer floating over a new/non-offer screen.
            if (System.currentTimeMillis() - lastShownAt > 1800L) hideOverlay()
            return
        }
        showResult(trip.first, trip.second, packageName)
    }

    private fun isSupportedRideApp(packageName: String): Boolean {
        return packageName == "com.ubercab.driver" ||
            packageName == "com.uber.client" ||
            packageName == "com.didiglobal.driver" ||
            packageName == "sinet.startup.inDriver"
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

    private fun parseTrip(raw: String): Pair<Double, Double>? {
        val text = normalizeDigits(raw)
        val price = findPrice(text) ?: return null
        val distance = tripDistance.matcher(text).let { matcher ->
            if (matcher.find()) matcher.group(1)?.replace(',', '.')?.toDoubleOrNull()
            else fallbackDistance.matcher(text).let { fallback ->
                if (fallback.find()) fallback.group(1)?.replace(',', '.')?.toDoubleOrNull() else null
            }
        }
        if (price <= 0.0 || distance == null || distance <= 0.0) return null
        return Pair(price, distance)
    }

    private fun findPrice(text: String): Double? {
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
        if (!enabled) hideOverlay()
    }

    private fun showResult(price: Double, distance: Double, packageName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            hideOverlay()
            return
        }

        val prefs = getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE)
        val minPrice = readDouble(prefs.all["flutter.minPrice"], 7.5).coerceAtLeast(0.0)
        val discount = readDouble(prefs.all["flutter.uberDiscount"], 0.0).coerceIn(0.0, 100.0)

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
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                windowType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                y = 130
                x = 16
            }
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
            setOnClickListener { hideOverlay() }
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

    private fun fmt(value: Double): String = String.format(Locale.US, "%.2f", value)

    private fun readDouble(value: Any?, fallback: Double): Double = when (value) {
        is Number -> value.toDouble()
        is String -> value.replace(',', '.').toDoubleOrNull() ?: fallback
        else -> fallback
    }

    private fun hideOverlay() {
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
        latestText = ""
        instance = null
        super.onDestroy()
    }
}
