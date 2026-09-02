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
import android.util.TypedValue
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
    private var consecutiveMisses = 0
    private val mainHandler = Handler(Looper.getMainLooper())
    private val clearStaleResult = Runnable { hideOverlay() }

    private data class ServiceProfile(
        val settingsKey: String,
        val label: String,
        val aliases: List<String>,
        val fallbackMinPrice: Double
    )

    private val uberProfiles = listOf(
        ServiceProfile("uberx_saver", "UberX Saver", listOf("uberx saver", "uber saver", "خدمة uberx saver", "saver", "سيفر", "اوبر سيفر", "أوبر سيفر", "اوبر توفير", "أوبر توفير", "توفير"), 7.5),
        ServiceProfile("uberx_priority", "UberX أولوية", listOf("uberx priority", "أولوية uberx", "اولوية uberx", "priority", "أولوية", "اولوية", "أولويه", "اولويات"), 7.0),
        ServiceProfile("comfort", "Uber Comfort", listOf("comfort", "كومفورت", "اوبر كومفورت", "أوبر كومفورت", "راحة"), 8.5),
        ServiceProfile("uberxl", "UberXL", listOf("uberxl", "uber xl", "اوبر اكس ال", "أوبر إكس إل", "اكس ال", "إكس إل", "xl"), 9.0),
        ServiceProfile("intercity", "Intercity", listOf("intercity", "بين المدن", "اوبر بين المدن", "أوبر بين المدن", "سفر", "بين المحافظات"), 8.5),
        ServiceProfile("connect", "Uber Connect", listOf("connect", "كونكت", "اوبر كونكت", "أوبر كونكت", "طرد", "توصيل طرد", "package", "delivery"), 6.5),
        ServiceProfile("scooter", "Uber Moto", listOf("moto", "موتو", "اوبر موتو", "أوبر موتو", "scooter", "سكوتر", "اوبر سكوتر", "أوبر سكوتر"), 4.5),
        ServiceProfile("uberx", "UberX", listOf("uberx", "uber x", "اوبر اكس", "أوبر إكس", "أوبر اكس", "اكس"), 6.5),
        ServiceProfile("uber", "Uber", listOf("uber", "أوبر", "اوبر"), 7.5),
    )

    private val numPattern = "([0-9]+(?:\\.[0-9]+)?)"
    private val currPattern = "(?:EGP|LE|L\\.E\\.?|ج\\s*[\\.,،]?\\s*م\\s*[\\.,،]?|جنيه|جنيه\\s*مصر[يى])"
    private val unitPattern = "(?:كم|كلم|كيلومتر|كيلو|كم\\.|كلم\\.|km|kms|mi|miles?)"
    private val durPattern = "(?:د(?:قيقة|قائق)?|دق|h|hr|hrs|hours?|ساعة|ساعات|س|min(?:utes?)?)"

    // Price regexes
    private val priceByLabel = Pattern.compile(
        "(?:القبول\\s+مقابل|قبول\\s+مقابل|قبول\\s+المشوار\\s+مقابل|القبول|قبول|السعر|سعر\\s+(?:الرحلة|المشوار)|المجموع|الإجمالي|المبلغ|الأجرة|الاجرة|أجرة\\s+الرحلة|اجرة\\s+الرحلة|fare|trip\\s+price|price|total|accept\\s+for)\\s*[:：\\-]?\\s*(?:" +
            currPattern + "\\s*)?" + numPattern + "(?:\\s*" + currPattern + ")?",
        Pattern.CASE_INSENSITIVE
    )
    private val pricePrefixCurrency = Pattern.compile(
        currPattern + "\\s*[:：\\-]?\\s*" + numPattern,
        Pattern.CASE_INSENSITIVE
    )
    private val priceSuffixCurrency = Pattern.compile(
        numPattern + "\\s*" + currPattern,
        Pattern.CASE_INSENSITIVE
    )

    // Pickup patterns
    private val pickupPatterns = listOf(
        Pattern.compile(
            "(?:على\\s+بعد|يبعد|يَبْعُد|بعد\\s+عنك|الوصول\\s+إلى|استلام|البيك\\s*اب|pickup|pick[\\s-]?up)\\s*(?:distance)?\\s*[:：\\-]?\\s*(?:[0-9]+\\s*" +
                durPattern + "\\s*)?\\(?\\s*" + numPattern + "\\s*" + unitPattern + "\\)?",
            Pattern.CASE_INSENSITIVE
        ),
        Pattern.compile(
            "(?:[0-9]+\\s*" + durPattern + "\\s*)?\\(?\\s*" + numPattern + "\\s*" + unitPattern +
                "\\)?\\s*(?:away|pickup|pick[\\s-]?up|على\\s+بعد|يبعد|بعد\\s+عنك)",
            Pattern.CASE_INSENSITIVE
        )
    )

    // Trip distance patterns
    private val tripPatterns = listOf(
        Pattern.compile(
            "(?:مسافة\\s+الرحلة|مسافة\\s+المشوار|المشوار|الرحلة|trip\\s+distance|route\\s+distance|dropoff|drop-off)\\s*[:：\\-]?\\s*" +
                numPattern + "\\s*" + unitPattern,
            Pattern.CASE_INSENSITIVE
        ),
        Pattern.compile(
            "(?:مشوار|رحلة|مشوار\\s+لمدة|رحلة\\s+لمدة|trip)\\s*(?:لمدة\\s+)?[0-9]+\\s*" + durPattern +
                "\\s*\\(?\\s*(?:(?:المسافة|مسافة|distance)\\s+)?" + numPattern + "\\s*" + unitPattern + "\\)?",
            Pattern.CASE_INSENSITIVE
        ),
        Pattern.compile(
            "(?:[0-9]+\\s*" + durPattern + "\\s*)?\\(?\\s*(?:(?:المسافة|مسافة|distance)\\s+)?" +
                numPattern + "\\s*" + unitPattern + "\\)?\\s*(?:trip|مشوار|رحلة|dropoff|drop-off)",
            Pattern.CASE_INSENSITIVE
        ),
        Pattern.compile(
            "[0-9]+\\s*" + durPattern + "\\s*\\(\\s*(?:(?:المسافة|مسافة|distance)\\s+)?" +
                numPattern + "\\s*" + unitPattern + "\\s*\\)",
            Pattern.CASE_INSENSITIVE
        ),
        Pattern.compile(
            "(?:المسافة|مسافة|distance|route)\\s*[:：\\-]?\\s*" + numPattern + "\\s*" + unitPattern,
            Pattern.CASE_INSENSITIVE
        )
    )

    private val bareDistance = Pattern.compile(
        "~?\\s*" + numPattern + "\\s*" + unitPattern,
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
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                AccessibilityEvent.TYPE_WINDOWS_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 80
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        }
        applyMonitoringState(isMonitoringStored())
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isMonitoringStored()) {
            hideOverlay()
            return
        }

        val text = collectAllVisibleText()
        if (text.isNotBlank()) {
            latestText = text
            val trip = parseTrip(text, "com.ubercab.driver") ?: parseTrip(text, "com.uber.client")
            if (trip != null) {
                consecutiveMisses = 0
                mainHandler.removeCallbacks(clearStaleResult)
                showResult(trip.first, trip.second, "com.ubercab.driver", text)
                return
            }
        }

        consecutiveMisses += 1
        if (consecutiveMisses >= 6) {
            mainHandler.removeCallbacks(clearStaleResult)
            mainHandler.postDelayed(clearStaleResult, 6000L)
        }
    }

    private val pollIntervalMs = 1000L
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

    private fun pollForUpdates() {
        if (!isMonitoringStored()) return
        val text = collectAllVisibleText()
        if (text.isBlank()) return
        latestText = text
        val trip = parseTrip(text, "com.ubercab.driver") ?: parseTrip(text, "com.uber.client") ?: return
        consecutiveMisses = 0
        mainHandler.removeCallbacks(clearStaleResult)
        showResult(trip.first, trip.second, "com.ubercab.driver", text)
    }

    private fun collectAllVisibleText(): String {
        val out = StringBuilder()
        val activeRoot = rootInActiveWindow
        if (activeRoot != null) {
            try {
                collectText(activeRoot, out, 0)
            } catch (_: Exception) { }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                for (window in windows) {
                    val root = window.root ?: continue
                    try {
                        collectText(root, out, 0)
                    } catch (_: Exception) { }
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

    private fun settingsKey(packageName: String): String = "uber"

    private fun detectServiceProfile(text: String): ServiceProfile {
        val lower = cleanAndNormalizeText(text).lowercase(Locale.ROOT)
        for (profile in uberProfiles) {
            if (profile.aliases.any { lower.contains(it.lowercase(Locale.ROOT)) }) {
                return profile
            }
        }
        return uberProfiles.last()
    }

    private fun isPickupDistanceEnabled(settingsKey: String): Boolean {
        val prefs = getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE)
        return prefs.all["flutter.includePickupDistance_$settingsKey"] as? Boolean ?: false
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
            collectText(child, out, depth + 1)
        }
    }

    private fun parseTrip(raw: String, packageName: String): Pair<Double, Double>? {
        val text = cleanAndNormalizeText(raw)
        val price = findPrice(text) ?: return null
        val profile = detectServiceProfile(text)
        val includePickupDistance = isPickupDistanceEnabled(profile.settingsKey)
        val distance = extractTripDistance(text, includePickupDistance) ?: return null
        if (price <= 0.0 || distance <= 0.0) return null
        return Pair(price, distance)
    }

    private data class Hit(val value: Double, val start: Int, val end: Int)

    private fun collectHits(re: Pattern, text: String): List<Hit> {
        val out = mutableListOf<Hit>()
        val m = re.matcher(text)
        while (m.find()) {
            for (i in 1..m.groupCount()) {
                val s = m.group(i)
                if (s != null) {
                    val v = s.toDoubleOrNull()
                    if (v != null && v > 0) {
                        out.add(Hit(v, m.start(), m.end()))
                        break
                    }
                }
            }
        }
        return out
    }

    private fun extractTripDistance(text: String, includePickupDistance: Boolean): Double? {
        val pickupHits = mutableListOf<Hit>()
        for (p in pickupPatterns) {
            pickupHits.addAll(collectHits(p, text))
        }
        val pickupVal = if (pickupHits.isNotEmpty()) pickupHits.maxOf { it.value } else null

        val tripHits = mutableListOf<Hit>()
        for (tp in tripPatterns) {
            for (h in collectHits(tp, text)) {
                val overlaps = pickupHits.any { p ->
                    (p.start <= h.start && h.start < p.end) || (h.start <= p.start && p.start < h.end)
                }
                if (!overlaps) {
                    tripHits.add(h)
                }
            }
            if (tripHits.isNotEmpty()) break
        }

        val tripVal = if (tripHits.isNotEmpty()) tripHits.maxOf { it.value } else null

        if (tripVal != null) {
            if (includePickupDistance && pickupVal != null && pickupVal > 0) {
                return tripVal + pickupVal
            }
            return tripVal
        }

        val bareHits = collectHits(bareDistance, text).filter { h ->
            !pickupHits.any { p ->
                (p.start <= h.start && h.start < p.end) || (h.start <= p.start && p.start < h.end)
            }
        }
        if (bareHits.isNotEmpty()) {
            val bare = bareHits.last().value
            if (includePickupDistance && pickupVal != null && pickupVal > 0) {
                return bare + pickupVal
            }
            return bare
        }

        if (includePickupDistance && pickupVal != null) {
            return pickupVal
        }

        return null
    }

    private fun findPrice(text: String): Double? {
        for (pattern in listOf(priceByLabel, pricePrefixCurrency, priceSuffixCurrency)) {
            val hits = collectHits(pattern, text)
            if (hits.isNotEmpty()) {
                return hits.first().value
            }
        }
        return null
    }

    private fun cleanAndNormalizeText(value: String): String {
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
                ch == '٬' -> Unit // thousand separator
                ch == '\u200E' || ch == '\u200F' || ch == '\u061C' ||
                    (ch.code in 0x202A..0x202E) ||
                    (ch.code in 0x2066..0x2069) ||
                    ch == '\u200B' || ch == '\uFEFF' || ch == '\u0640' ||
                    (ch.code in 0x064B..0x065F) -> Unit
                ch == '\u00A0' || ch == '\u202F' || (ch.code in 0x2000..0x200A) -> out.append(' ')
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
            hideOverlay()
            stopPolling()
        } else {
            startPolling()
        }
    }

    private fun showStatusOverlay(enabled: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) return
        val view = statusOverlayView ?: TextView(this).apply {
            text = "SD"
            textSize = 14f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(18, 14, 18, 14)
            elevation = 16f
        }.also { statusOverlayView = it }

        view.text = if (enabled) "SD ✓" else "SD"
        view.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (enabled) Color.rgb(22, 163, 74) else Color.rgb(220, 38, 38))
            setStroke(dpToPx(1.5f), Color.argb(180, 255, 255, 255))
        }

        if (view.parent == null) {
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

    private fun showResult(price: Double, distance: Double, packageName: String, screenText: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            hideOverlay()
            return
        }

        val prefs = getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE)
        val profile = detectServiceProfile(screenText)
        val appSettingsKey = settingsKey(packageName)

        val minPrice = readDouble(
            prefs.all["flutter.minPrice_${profile.settingsKey}"]
                ?: prefs.all["flutter.minPrice_$appSettingsKey"]
                ?: prefs.all["flutter.minPrice"],
            profile.fallbackMinPrice
        ).coerceAtLeast(0.0)

        val discount = readDouble(
            prefs.all["flutter.discount_${profile.settingsKey}"]
                ?: prefs.all["flutter.discount_${profile.settingsKey}_percent"]
                ?: prefs.all["flutter.discount_$appSettingsKey"]
                ?: prefs.all["flutter.discount_${appSettingsKey}_percent"]
                ?: prefs.all["flutter.uberDiscount"],
            0.0
        ).coerceIn(0.0, 100.0)

        val fareAfterDiscount = price * (1.0 - discount / 100.0)
        val net = if (distance > 0) fareAfterDiscount / distance else 0.0
        val gross = if (distance > 0) price / distance else 0.0
        val suitable = net >= minPrice
        val appLabel = profile.label

        val signature = "$packageName|${fmt(price)}|${fmt(distance)}|${fmt(net)}|$suitable"
        val now = System.currentTimeMillis()
        if (signature == lastSignature && now - lastShownAt < 1000L && overlayView?.parent != null) return
        lastSignature = signature
        lastShownAt = now

        val root = overlayView as? LinearLayout ?: createOverlay().also { overlayView = it }
        val badge = root.findViewWithTag<TextView>("badge")
        val mainLine = root.findViewWithTag<TextView>("mainLine")
        val rateLine = root.findViewWithTag<TextView>("rateLine")
        val targetLine = root.findViewWithTag<TextView>("targetLine")

        badge.text = if (suitable) "✓ مناسب • $appLabel" else "✕ غير مناسب • $appLabel"
        mainLine.text = "💰 ${fmt(price)} ج.م   •   📍 ${fmt(distance)} كم"
        rateLine.text = if (discount > 0) {
            "الصافي: ${fmt(net)} ج.م/كم (خصم ${fmt(discount)}%)"
        } else {
            "السعر: ${fmt(net)} ج.م/كم"
        }
        targetLine.text = if (suitable) {
            "✓ أعلى من الحد المطلوب (${fmt(minPrice)} ج.م/كم)"
        } else {
            "المطلوب: ${fmt(minPrice)} ج.م/كم  |  الحالي: ${fmt(net)} ج.م/كم"
        }

        val bgColor = if (suitable) Color.rgb(20, 108, 54) else Color.rgb(175, 28, 28)
        root.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(16f).toFloat()
            setColor(bgColor)
            setStroke(dpToPx(1.5f), Color.argb(180, 255, 255, 255))
        }
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
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dpToPx(18f), dpToPx(12f), dpToPx(18f), dpToPx(12f))
            elevation = 20f
        }

        // Header with title and close button
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val badge = TextView(this).apply {
            tag = "badge"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val closeBtn = TextView(this).apply {
            text = "✕"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.argb(220, 255, 255, 255))
            setPadding(dpToPx(8f), dpToPx(2f), dpToPx(8f), dpToPx(2f))
            setOnClickListener { hideOverlay() }
        }

        header.addView(badge)
        header.addView(closeBtn)
        container.addView(header)

        // Divider
        val divider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(1f)
            ).apply {
                setMargins(0, dpToPx(6f), 0, dpToPx(8f))
            }
            setBackgroundColor(Color.argb(80, 255, 255, 255))
        }
        container.addView(divider)

        // Price & Distance row
        val mainLine = TextView(this).apply {
            tag = "mainLine"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, dpToPx(2f), 0, dpToPx(4f))
        }
        container.addView(mainLine)

        // Calculated Rate row
        val rateLine = TextView(this).apply {
            tag = "rateLine"
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(254, 240, 138)) // Light yellow
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dpToPx(2f))
        }
        container.addView(rateLine)

        // Target comparison line
        val targetLine = TextView(this).apply {
            tag = "targetLine"
            textSize = 12f
            typeface = Typeface.DEFAULT
            setTextColor(Color.argb(230, 255, 255, 255))
            gravity = Gravity.CENTER
        }
        container.addView(targetLine)

        return container
    }

    private fun dpToPx(dp: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics).toInt()

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
                    if (kotlin.math.abs(dx) > 6 || kotlin.math.abs(dy) > 6) moved = true
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
            } catch (_: Exception) { }
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
        latestOcrText = ""
        instance = null
        super.onDestroy()
    }
}
