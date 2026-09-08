package com.superdriver.app.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.superdriver.app.calculator.DriverDecision
import com.superdriver.app.overlay.OverlayVisualState.DECISION
import com.superdriver.app.overlay.OverlayVisualState.INCOMPLETE_DATA

class DriverDecisionOverlayService : Service() {
    private var currentState: OverlayCardState? = null
    private var overlayView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private val windowManager: WindowManager by lazy {
        getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        val state = intent?.toOverlayCardState() ?: OverlayCardState.simulatedAccept()
        ensureNotificationChannel()
        startAsForegroundService(state)
        updateOverlayState(state)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        removeOverlay()
        currentState = null
        super.onDestroy()
    }

    fun updateOverlayState(state: OverlayCardState) {
        currentState = state
        showOverlay(state)
    }

    private fun showOverlay(state: OverlayCardState) {
        removeOverlay()

        val view = buildOverlayView(state)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = 140
        }

        view.enableDragging(params)
        windowManager.addView(view, params)
        overlayView = view
        layoutParams = params
    }

    private fun removeOverlay() {
        overlayView?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        overlayView = null
        layoutParams = null
    }

    private fun buildOverlayView(state: OverlayCardState): View {
        val accentColor = state.toAccentColor()
        val background = GradientDrawable().apply {
            cornerRadius = 22f
            setColor(state.toBackgroundColor())
            setStroke(3, accentColor)
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 18, 22, 18)
            this.background = background
            elevation = 14f
            minimumWidth = 250

            addView(
                overlayText(
                    text = state.titleText,
                    sizeSp = 18f,
                    color = accentColor,
                    style = Typeface.BOLD
                )
            )
            addView(overlayText(text = state.fareText, sizeSp = 28f, style = Typeface.BOLD))
            addView(metricRow("${state.egpPerKmText}/km", "${state.egpPerHourText}/h"))
            addView(metricRow(state.totalTimeText, state.totalKmText))

            state.shortReason?.takeIf { it.isNotBlank() }?.let { reason ->
                addView(separator(accentColor))
                addView(reasonText(reason))
            }
        }
    }

    private fun metricRow(
        leftText: String,
        rightText: String
    ): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 5, 0, 0)
            addView(
                overlayText(
                    text = leftText,
                    sizeSp = 16f,
                    color = Color.rgb(238, 242, 246),
                    style = Typeface.BOLD
                )
            )
            addView(
                overlayText(
                    text = "  -  ",
                    sizeSp = 16f,
                    color = Color.rgb(178, 188, 198)
                )
            )
            addView(
                overlayText(
                    text = rightText,
                    sizeSp = 16f,
                    color = Color.rgb(238, 242, 246),
                    style = Typeface.BOLD
                )
            )
        }
    }

    private fun separator(accentColor: Int): View {
        return FrameLayout(this).apply {
            setPadding(0, 8, 0, 4)
            addView(
                View(context).apply {
                    background = GradientDrawable().apply {
                        setColor(
                            Color.argb(
                                150,
                                Color.red(accentColor),
                                Color.green(accentColor),
                                Color.blue(accentColor)
                            )
                        )
                        cornerRadius = 2f
                    }
                },
                FrameLayout.LayoutParams(
                    260,
                    2
                )
            )
        }
    }

    private fun reasonText(reason: String): TextView {
        return overlayText(
            text = reason,
            sizeSp = 14f,
            color = Color.rgb(230, 234, 238)
        ).apply {
            maxLines = 2
            maxWidth = 260
            ellipsize = TextUtils.TruncateAt.END
        }
    }

    private fun ensureNotificationChannel() {

        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "النافذة العائمة للقرار",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "يحافظ على ظهور توصية الرحلة فوق التطبيق."
            setShowBadge(false)
        }

        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun startAsForegroundService(state: OverlayCardState) {
        val notification = buildForegroundNotification(state)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildForegroundNotification(state: OverlayCardState): Notification {
        val stopIntent = Intent(this, DriverDecisionOverlayService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            STOP_REQUEST_CODE,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("النافذة العائمة للقرار مفعلة")
            .setContentText("${state.titleText} - ${state.fareText}")
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

    private fun overlayText(
        text: String,
        sizeSp: Float,
        color: Int = Color.WHITE,
        style: Int = Typeface.NORMAL
    ): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = sizeSp
            setTextColor(color)
            typeface = Typeface.create(Typeface.DEFAULT, style)
            includeFontPadding = false
            setPadding(0, 2, 0, 2)
        }
    }

    private fun View.enableDragging(params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(view, params)
                    true
                }
                else -> false
            }
        }
    }

    private fun Intent.toOverlayCardState(): OverlayCardState? {
        val decision = getStringExtra(EXTRA_DECISION)
            ?.let { runCatching { DriverDecision.valueOf(it) }.getOrNull() }
            ?: return null

        return OverlayCardState(
            decision = decision,
            visualState = getStringExtra(EXTRA_VISUAL_STATE)
                ?.let { runCatching { OverlayVisualState.valueOf(it) }.getOrNull() }
                ?: DECISION,
            titleText = getStringExtra(EXTRA_TITLE_TEXT)
                .orDefault(decision.toArabicLabel()),
            fareText = getStringExtra(EXTRA_FARE_TEXT).orDefault("جنيه -"),
            egpPerHourText = getStringExtra(EXTRA_EGP_PER_HOUR_TEXT).orDefault("جنيه -"),
            egpPerKmText = getStringExtra(EXTRA_EGP_PER_KM_TEXT).orDefault("جنيه -"),
            totalTimeText = getStringExtra(EXTRA_TOTAL_TIME_TEXT).orDefault("- min"),
            totalKmText = getStringExtra(EXTRA_TOTAL_KM_TEXT).orDefault("- km"),
            shortReason = getStringExtra(EXTRA_SHORT_REASON)
        )
    }

    private fun String?.orDefault(defaultValue: String): String {
        return takeUnless { it.isNullOrBlank() } ?: defaultValue
    }

    private fun DriverDecision.toArabicLabel(): String {
        return when (this) {
            DriverDecision.ACCEPT -> "قبول"
            DriverDecision.REJECT -> "رفض"
            DriverDecision.REVIEW -> "مراجعة"
        }
    }

    private fun OverlayCardState.toAccentColor(): Int {
        return when (visualState) {
            INCOMPLETE_DATA -> Color.rgb(248, 250, 252)
            DECISION -> when (decision) {
                DriverDecision.ACCEPT -> Color.rgb(40, 190, 120)
                DriverDecision.REJECT -> Color.rgb(235, 83, 83)
                DriverDecision.REVIEW -> Color.rgb(245, 178, 58)
            }
        }
    }

    private fun OverlayCardState.toBackgroundColor(): Int {
        return when (visualState) {
            INCOMPLETE_DATA -> Color.argb(242, 48, 52, 57)
            DECISION -> Color.argb(242, 18, 22, 26)
        }
    }

    companion object {
        const val ACTION_STOP = "com.superdriver.app.overlay.STOP"
        const val EXTRA_DECISION = "extra_decision"
        const val EXTRA_VISUAL_STATE = "extra_visual_state"
        const val EXTRA_TITLE_TEXT = "extra_title_text"
        const val EXTRA_FARE_TEXT = "extra_fare_text"
        const val EXTRA_EGP_PER_HOUR_TEXT = "extra_egp_per_hour_text"
        const val EXTRA_EGP_PER_KM_TEXT = "extra_egp_per_km_text"
        const val EXTRA_TOTAL_TIME_TEXT = "extra_total_time_text"
        const val EXTRA_TOTAL_KM_TEXT = "extra_total_km_text"
        const val EXTRA_SHORT_REASON = "extra_short_reason"

        private const val NOTIFICATION_CHANNEL_ID = "decision_overlay_service"
        private const val NOTIFICATION_ID = 4101
        private const val STOP_REQUEST_CODE = 4102
    }
}
