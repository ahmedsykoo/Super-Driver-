package com.superdriver.app

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    companion object {
        private const val CHANNEL = "com.superdriver/accessibility"
        private const val PREFS = "super_driver"
        private const val MONITORING_KEY = "monitoring_enabled"
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
            .setMethodCallHandler { call, result ->
                try {
                    when (call.method) {
                        "isOverlayPermissionGranted" -> result.success(isOverlayPermissionGranted())
                        "openOverlaySettings" -> result.success(openOverlaySettings())
                        "isAccessibilityEnabled" -> result.success(isAccessibilityEnabled())
                        "openAccessibilitySettings" -> result.success(openAccessibilitySettings())
                        "isMonitoringEnabled" -> result.success(isMonitoringEnabled())
                        "setMonitoringEnabled" -> {
                            val enabled = call.argument<Boolean>("enabled") ?: false
                            result.success(setMonitoringEnabled(enabled))
                        }
                        "getAccessibilityText" -> result.success(UberAccessibilityService.latestText)
                        "getOcrText" -> result.success(UberAccessibilityService.latestOcrText)
                        else -> result.notImplemented()
                    }
                } catch (e: Exception) {
                    result.error("NATIVE_ERROR", e.message ?: "Android operation failed", null)
                }
            }
    }

    private fun isOverlayPermissionGranted(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
    }

    /**
     * Android 11+ deliberately ignores package: for ACTION_MANAGE_OVERLAY_PERMISSION.
     * Therefore use the official top-level screen on modern Android and the app-specific
     * screen only on older releases where it is supported.
     */
    private fun openOverlaySettings(): Boolean {
        if (isOverlayPermissionGranted()) return true

        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
        } else {
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                data = Uri.parse("package:$packageName")
            }
        }

        return startSettingsIntent(
            intent,
            "افتح Super Driver من قائمة الظهور فوق التطبيقات ثم فعّل السماح"
        )
    }

    /**
     * Accessibility is a protected Android capability. Third-party apps cannot silently
     * enable their own AccessibilityService. We open Android's Accessibility settings.
     * On builds that support the details action, we also pass the service component as a
     * STRING because Android Settings reads EXTRA_COMPONENT_NAME as a string.
     */
    private fun openAccessibilitySettings(): Boolean {
        // Open the public Accessibility list directly. This is supported by
        // Android and is more reliable across Samsung/Xiaomi/other OEM Settings
        // implementations than the optional details intent.
        return startSettingsIntent(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            "فعّل خدمة Super Driver من إمكانية الوصول ثم ارجع للتطبيق"
        )
    }

    private fun startSettingsIntent(intent: Intent, message: String): Boolean {
        return try {
            if (intent.resolveActivity(packageManager) == null) return false
            startActivity(intent)
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Use Settings.Secure as the source of truth. AccessibilityManager can vary by OEM,
     * while ENABLED_ACCESSIBILITY_SERVICES is the Android setting that records enabled
     * services.
     */
    private fun isAccessibilityEnabled(): Boolean {
        val expected = ComponentName(this, UberAccessibilityService::class.java).flattenToString()
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabledServices.split(':').any { enabled ->
            enabled.equals(expected, ignoreCase = true)
        }
    }

    private fun isMonitoringEnabled(): Boolean {
        return getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(MONITORING_KEY, false)
    }

    private fun setMonitoringEnabled(enabled: Boolean): Boolean {
        if (enabled && (!isOverlayPermissionGranted() || !isAccessibilityEnabled())) {
            Toast.makeText(
                this,
                "فعّل الظهور فوق التطبيقات وإمكانية الوصول أولاً",
                Toast.LENGTH_LONG
            ).show()
            return false
        }

        getSharedPreferences(PREFS, MODE_PRIVATE)
            .edit()
            .putBoolean(MONITORING_KEY, enabled)
            .apply()

        UberAccessibilityService.setMonitoringEnabled(enabled)
        return true
    }
}
