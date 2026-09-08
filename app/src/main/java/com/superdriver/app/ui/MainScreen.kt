package com.superdriver.app.ui

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.superdriver.app.calculator.DriverDecision
import com.superdriver.app.calculator.TripDecisionResult
import com.superdriver.app.capture.ScreenCaptureFrameService
import com.superdriver.app.capture.ScreenCaptureManager
import com.superdriver.app.capture.ScreenCaptureMonitorResult
import com.superdriver.app.capture.ScreenCaptureMonitorService
import com.superdriver.app.config.DriverConfig
import com.superdriver.app.config.DriverConfigFormField
import com.superdriver.app.config.DriverConfigFormState
import com.superdriver.app.overlay.DriverDecisionOverlayService
import com.superdriver.app.overlay.OverlayCardState
import com.superdriver.app.overlay.OverlayPermissionHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val AppBackground = Color(0xFF0B0F14)
private val AppSurface = Color(0xFF121821)
private val AppSurfaceRaised = Color(0xFF18212D)
private val AppSurfaceSubtle = Color(0xFF202B38)
private val AppBorder = Color(0xFF2A3646)
private val AppText = Color(0xFFEAF0F7)
private val AppMutedText = Color(0xFFA8B3C2)
private val AppPrimary = Color(0xFF65D6AD)
private val AppPrimaryDim = Color(0xFF123B33)
private val AppWarning = Color(0xFFF7C948)
private val AppWarningDim = Color(0xFF3B3012)
private val AppErrorDim = Color(0xFF421C24)
private val AppError = Color(0xFFFF8A9A)

private val SuperDriverDarkColorScheme = darkColorScheme(
    primary = AppPrimary,
    onPrimary = Color(0xFF06251F),
    primaryContainer = AppPrimaryDim,
    onPrimaryContainer = AppText,
    secondary = Color(0xFF8DB7FF),
    onSecondary = Color(0xFF071A38),
    secondaryContainer = Color(0xFF172B4D),
    onSecondaryContainer = AppText,
    background = AppBackground,
    onBackground = AppText,
    surface = AppSurface,
    onSurface = AppText,
    surfaceVariant = AppSurfaceSubtle,
    onSurfaceVariant = AppMutedText,
    error = AppError,
    onError = Color(0xFF33000A),
    errorContainer = AppErrorDim,
    onErrorContainer = AppText,
    outline = AppBorder
)

@Composable
fun MainScreen(
    viewModel: MainViewModel? = null
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val factory = remember(appContext) { MainViewModelFactory(appContext) }
    val resolvedViewModel = viewModel ?: viewModel(factory = factory)
    val uiState by resolvedViewModel.uiState.collectAsState()
    val overlayPermissionHelper = remember { OverlayPermissionHelper() }
    val screenCaptureManager = remember(context) { ScreenCaptureManager(context) }
    val screenCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        resolvedViewModel.updateScreenCaptureSession(
            screenCaptureManager.handlePermissionResult(
                resultCode = result.resultCode,
                data = result.data
            )
        )
    }
    val monitorLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            val intent = ScreenCaptureMonitorService.buildStartIntent(
                context = context,
                resultCode = result.resultCode,
                resultData = data
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } else {
            resolvedViewModel.markMonitorPermissionDenied()
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                resolvedViewModel.refreshOverlayPermission(
                    overlayPermissionHelper.canDrawOverlays(context)
                )
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        resolvedViewModel.refreshOverlayPermission(overlayPermissionHelper.canDrawOverlays(context))

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(context, screenCaptureManager) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                if (intent?.action == ScreenCaptureFrameService.ACTION_CAPTURE_RESULT) {
                    resolvedViewModel.updateScreenCaptureSession(
                        screenCaptureManager.handleCaptureResult(intent)
                    )
                }
            }
        }
        val filter = IntentFilter(ScreenCaptureFrameService.ACTION_CAPTURE_RESULT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(receiver, filter)
        }

        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                if (intent?.action == ScreenCaptureMonitorService.ACTION_MONITOR_RESULT) {
                    resolvedViewModel.updateScreenCaptureMonitor(
                        ScreenCaptureMonitorResult.fromIntent(intent)
                    )
                }
            }
        }
        val filter = IntentFilter(ScreenCaptureMonitorService.ACTION_MONITOR_RESULT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(receiver, filter)
        }

        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    MainScreenContent(
        uiState = uiState,
        overlayActionsEnabled = uiState.overlayPermissionStatus == "ممنوح",
        onRequestOverlayPermission = {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                overlayPermissionHelper.buildOverlaySettingsUri(context)
            )
            context.startActivity(intent)
        },
        onRequestScreenCapturePermission = {
            resolvedViewModel.markScreenCapturePending()
            screenCaptureLauncher.launch(screenCaptureManager.buildPermissionIntent())
        },
        onStopScreenCapture = {
            resolvedViewModel.updateScreenCaptureSession(screenCaptureManager.stopSession())
        },
        onCaptureFrame = {
            resolvedViewModel.updateScreenCaptureSession(screenCaptureManager.captureOnce())
        },
        onAnalyzeOcr = resolvedViewModel::analyzeLastRecognizedText,
        onStartMonitoring = {
            resolvedViewModel.markMonitorWaitingPermission()
            monitorLauncher.launch(screenCaptureManager.buildPermissionIntent())
        },
        onStopMonitoring = {
            context.stopService(Intent(context, ScreenCaptureMonitorService::class.java))
            resolvedViewModel.markMonitorStopped()
        },
        onStopService = {
            context.stopService(Intent(context, DriverDecisionOverlayService::class.java))
            resolvedViewModel.markOverlayStopped()
        },
        onRunSimulatedDecision = {
            val overlayState = resolvedViewModel.runSimulatedTripDecision()
            startDecisionOverlay(
                context = context,
                overlayState = overlayState,
                viewModel = resolvedViewModel,
                overlayPermissionHelper = overlayPermissionHelper
            )
        },
        onShowLastRealDecisionOverlay = {
            val overlayState = resolvedViewModel.buildLastRealDecisionOverlayState()
            if (overlayState != null) {
                startDecisionOverlay(
                    context = context,
                    overlayState = overlayState,
                    viewModel = resolvedViewModel,
                    overlayPermissionHelper = overlayPermissionHelper
                )
            }
        },
        onConfigInputChange = resolvedViewModel::updateDriverConfigInput,
        onSaveConfig = resolvedViewModel::saveDriverConfigForm,
        onResetConfig = resolvedViewModel::resetConfigToDefaults
    )
}

@Composable
fun MainScreenContent(
    uiState: MainUiState,
    overlayActionsEnabled: Boolean,
    onRequestOverlayPermission: () -> Unit,
    onRequestScreenCapturePermission: () -> Unit,
    onStopScreenCapture: () -> Unit,
    onCaptureFrame: () -> Unit,
    onAnalyzeOcr: () -> Unit,
    onStartMonitoring: () -> Unit,
    onStopMonitoring: () -> Unit,
    onStopService: () -> Unit,
    onRunSimulatedDecision: () -> Unit,
    onShowLastRealDecisionOverlay: () -> Unit,
    onConfigInputChange: (DriverConfigFormField, String) -> Unit,
    onSaveConfig: () -> Unit,
    onResetConfig: () -> Unit
) {
    var selectedSectionName by rememberSaveable { mutableStateOf(MainSection.HOME.name) }
    val selectedSection = MainSection.valueOf(selectedSectionName)

    MaterialTheme(colorScheme = SuperDriverDarkColorScheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = AppBackground
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
            ) {
                AppHeader()
                MainSectionTabs(
                    selectedSection = selectedSection,
                    onSectionSelected = { section -> selectedSectionName = section.name }
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(
                            start = 20.dp,
                            top = 16.dp,
                            end = 20.dp,
                            bottom = 28.dp
                        ),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (selectedSection) {
                        MainSection.HOME -> HomeSection(
                            uiState = uiState,
                            onRequestOverlayPermission = onRequestOverlayPermission,
                            onRequestScreenCapturePermission = onRequestScreenCapturePermission,
                            onStartMonitoring = onStartMonitoring,
                            onStopMonitoring = onStopMonitoring
                        )
                        MainSection.SETTINGS -> SettingsSection(
                            uiState = uiState,
                            onConfigInputChange = onConfigInputChange,
                            onSaveConfig = onSaveConfig,
                            onResetConfig = onResetConfig
                        )
                        MainSection.DIAGNOSTIC -> DiagnosticToolsSection(
                            uiState = uiState,
                            overlayActionsEnabled = overlayActionsEnabled,
                            onCaptureFrame = onCaptureFrame,
                            onAnalyzeOcr = onAnalyzeOcr,
                            onStopScreenCapture = onStopScreenCapture,
                            onStopService = onStopService,
                            onRunSimulatedDecision = onRunSimulatedDecision,
                            onShowLastRealDecisionOverlay = onShowLastRealDecisionOverlay
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "مساعد السائق",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "مساعد بصري لتقييم عروض رحلات أوبر.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MainSectionTabs(
    selectedSection: MainSection,
    onSectionSelected: (MainSection) -> Unit
) {
    TabRow(
        selectedTabIndex = selectedSection.ordinal,
        containerColor = AppBackground,
        contentColor = AppPrimary
    ) {
        MainSection.entries.forEach { section ->
            Tab(
                selected = selectedSection == section,
                onClick = { onSectionSelected(section) },
                selectedContentColor = AppText,
                unselectedContentColor = AppMutedText,
                text = {
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selectedSection == section) {
                            FontWeight.Bold
                        } else {
                            FontWeight.SemiBold
                        }
                    )
                }
            )
        }
    }
}

private enum class MainSection(
    val title: String
) {
    HOME("Inicio"),
    SETTINGS("الإعدادات"),
    DIAGNOSTIC("Diagnóstico")
}

private fun startDecisionOverlay(
    context: Context,
    overlayState: OverlayCardState,
    viewModel: MainViewModel,
    overlayPermissionHelper: OverlayPermissionHelper
) {
    if (!overlayPermissionHelper.canDrawOverlays(context)) {
        viewModel.markOverlayPermissionMissing()
        return
    }

    val intent = Intent(context, DriverDecisionOverlayService::class.java).apply {
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
        context.startForegroundService(intent)
    } else {
        context.startService(intent)
    }
    viewModel.markOverlayStarted()
}

@Composable
private fun AppCard(
    modifier: Modifier = Modifier,
    containerColor: Color = AppSurface,
    borderColor: Color = AppBorder,
    elevation: Dp = 2.dp,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation)
    ) {
        content()
    }
}

@Composable
private fun darkOutlinedButtonColors() = ButtonDefaults.outlinedButtonColors(
    contentColor = AppText,
    containerColor = AppSurfaceRaised,
    disabledContentColor = AppMutedText.copy(alpha = 0.45f),
    disabledContainerColor = AppSurface.copy(alpha = 0.7f)
)

@Composable
private fun HomeSection(
    uiState: MainUiState,
    onRequestOverlayPermission: () -> Unit,
    onRequestScreenCapturePermission: () -> Unit,
    onStartMonitoring: () -> Unit,
    onStopMonitoring: () -> Unit
) {
    StatusSection(
        uiState = uiState,
        onRequestOverlayPermission = onRequestOverlayPermission,
        onRequestScreenCapturePermission = onRequestScreenCapturePermission
    )
    PrimaryMonitorSection(
        uiState = uiState,
        onStartMonitoring = onStartMonitoring,
        onStopMonitoring = onStopMonitoring
    )
    LastDecisionSection(result = uiState.lastDecision)
    HomeConfigSummarySection(
        form = uiState.configForm,
        config = uiState.lastConfig ?: DriverConfig.default()
    )
}

@Composable
private fun StatusSection(
    uiState: MainUiState,
    onRequestOverlayPermission: () -> Unit,
    onRequestScreenCapturePermission: () -> Unit
) {
    val homeStatus = uiState.toHomeStatus()
    val containerColor = when (homeStatus) {
        HomeStatus.READY -> AppPrimaryDim
        HomeStatus.REQUIRES_PERMISSIONS -> AppErrorDim
        HomeStatus.MONITORING -> Color(0xFF172B4D)
        HomeStatus.STOPPED -> AppSurfaceRaised
    }
    val borderColor = when (homeStatus) {
        HomeStatus.READY -> AppPrimary.copy(alpha = 0.42f)
        HomeStatus.REQUIRES_PERMISSIONS -> AppError.copy(alpha = 0.48f)
        HomeStatus.MONITORING -> Color(0xFF8DB7FF).copy(alpha = 0.45f)
        HomeStatus.STOPPED -> AppBorder
    }

    AppCard(
        containerColor = containerColor,
        borderColor = borderColor,
        elevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "حالة المساعد",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = homeStatus.toTitle(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = homeStatus.toDescription(),
                style = MaterialTheme.typography.bodyMedium
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                InfoMetric(
                    modifier = Modifier.weight(1f),
                    label = "النافذة العائمة",
                    value = uiState.overlayPermissionStatus.toOverlayPermissionSummary()
                )
                InfoMetric(
                    modifier = Modifier.weight(1f),
                    label = "التقاط الشاشة",
                    value = uiState.screenCapturePermissionStatus.toScreenCaptureSummary()
                )
            }
            if (homeStatus == HomeStatus.REQUIRES_PERMISSIONS) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onRequestOverlayPermission,
                        colors = darkOutlinedButtonColors()
                    ) {
                        Text("Permitir ventana flotante")
                    }
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onRequestScreenCapturePermission,
                        colors = darkOutlinedButtonColors()
                    ) {
                        Text("السماح بالتقاط الشاشة")
                    }
                }
            }
        }
    }
}

@Composable
private fun PrimaryMonitorSection(
    uiState: MainUiState,
    onStartMonitoring: () -> Unit,
    onStopMonitoring: () -> Unit
) {
    val isMonitoring = uiState.isMonitoringActive()

    AppCard(containerColor = AppSurface) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "المراقبة",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = if (isMonitoring) {
                    "المراقبة مفعلة حاليًا."
                } else {
                    "شغّل المراقبة عندما تكون مستعدًا لاستقبال العروض."
                },
                style = MaterialTheme.typography.bodyMedium
            )
            if (isMonitoring) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onStopMonitoring,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppError,
                        contentColor = Color(0xFF220006)
                    )
                ) {
                    Text("إيقاف المراقبة")
                }
            } else {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onStartMonitoring,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppPrimary,
                        contentColor = Color(0xFF06251F)
                    )
                ) {
                    Text("Iniciar monitoreo")
                }
            }
            uiState.monitorErrorMessage?.let { errorMessage ->
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun LastDecisionSection(result: TripDecisionResult?) {
    val containerColor = result?.decision.toDecisionContainerColor()
    val borderColor = result?.decision?.toDecisionAccentColor() ?: AppBorder

    AppCard(
        containerColor = containerColor,
        borderColor = borderColor.copy(alpha = 0.55f),
        elevation = if (result == null) 2.dp else 4.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "آخر قرار",
                style = MaterialTheme.typography.titleLarge
            )

            if (result == null) {
                Text(
                    text = "عند اكتشاف عرض، ستظهر هنا التوصية وأهم المقاييس.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Text(
                    text = result.decision.toArabicLabel(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = result.decision.toDecisionAccentColor()
                )
                Text(
                    text = "السبب: ${result.toMainReason()}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    InfoMetric(
                        modifier = Modifier.weight(1f),
                        label = "الأجرة",
                        value = result.fareAmount.toDisplayMoney()
                    )
                    InfoMetric(
                        modifier = Modifier.weight(1f),
                        label = "جنيه/كم",
                        value = result.egpPerKm.toDisplayMoney()
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    InfoMetric(
                        modifier = Modifier.weight(1f),
                        label = "جنيه/ساعة",
                        value = result.egpPerHour.toDisplayMoney()
                    )
                    InfoMetric(
                        modifier = Modifier.weight(1f),
                        label = "الإجمالي",
                        value = "${result.totalKm.toDisplayNumber()} km / ${result.totalMinutes.toDisplayNumber()} min"
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoMetric(
    modifier: Modifier,
    label: String,
    value: String
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = AppSurfaceSubtle,
        border = BorderStroke(1.dp, AppBorder.copy(alpha = 0.7f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun HomeConfigSummarySection(
    form: DriverConfigFormState,
    config: DriverConfig
) {
    AppCard(containerColor = AppSurface) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "ملخص الإعدادات",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "الحدود المحلية المستخدمة لحساب التوصية.",
                style = MaterialTheme.typography.bodyMedium
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                InfoMetric(
                    modifier = Modifier.weight(1f),
                    label = "الحد الأدنى جنيه/كم",
                    value = form.minEgpPerKm.toMoneyInputSummary()
                )
                InfoMetric(
                    modifier = Modifier.weight(1f),
                    label = "الحد الأدنى جنيه/ساعة",
                    value = form.minEgpPerHour.toMoneyInputSummary()
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                InfoMetric(
                    modifier = Modifier.weight(1f),
                    label = "التكلفة/كم",
                    value = form.costPerKm.toMoneyInputSummary()
                )
                InfoMetric(
                    modifier = Modifier.weight(1f),
                    label = "التكلفة/دقيقة",
                    value = form.costPerMinute.toMoneyInputSummary()
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                InfoMetric(
                    modifier = Modifier.weight(1f),
                    label = "أقل ربح",
                    value = form.minNetProfit.toMoneyInputSummary()
                )
                InfoMetric(
                    modifier = Modifier.weight(1f),
                    label = "Revisión",
                    value = form.reviewTolerancePercent.toPercentInputSummary()
                )
            }
            HorizontalDivider(color = AppBorder)
            ReadOnlySettingRow(
                label = "Reglas activas",
                value = config.toRulesSummary()
            )
            ReadOnlySettingRow(
                label = "مناطق للتجنب",
                value = config.avoidZones.count { it.enabled }.toZonesCountText()
            )
        }
    }
}

@Composable
private fun SettingsSection(
    uiState: MainUiState,
    onConfigInputChange: (DriverConfigFormField, String) -> Unit,
    onSaveConfig: () -> Unit,
    onResetConfig: () -> Unit
) {
    val form = uiState.configForm
    val config = uiState.lastConfig ?: DriverConfig.default()

    Text(
        text = "الإعدادات",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold
    )
    Text(
        text = "اضبط المعايير المحلية التي تستخدمها للتوصية بالقبول أو المراجعة أو الرفض.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    ConfigGroupCard(
        title = "الحد الأدنى للربحية",
        subtitle = "الشروط التي يجب أن يحققها العرض ليُعتبر مربحًا."
    ) {
        ConfigNumberField(
            label = "الحد الأدنى جنيه/كم",
            value = form.minEgpPerKm,
            field = DriverConfigFormField.MIN_EGP_PER_KM,
            onConfigInputChange = onConfigInputChange
        )
        ConfigNumberField(
            label = "الحد الأدنى جنيه/ساعة",
            value = form.minEgpPerHour,
            field = DriverConfigFormField.MIN_EGP_PER_HOUR,
            onConfigInputChange = onConfigInputChange
        )
        ConfigNumberField(
            label = "الحد الأدنى للربح",
            value = form.minNetProfit,
            field = DriverConfigFormField.MIN_NET_PROFIT,
            onConfigInputChange = onConfigInputChange
        )
    }

    ConfigGroupCard(
        title = "التكاليف التقديرية",
        subtitle = "Valores usados para estimar costo y ganancia neta."
    ) {
        ConfigNumberField(
            label = "التكلفة لكل كم",
            value = form.costPerKm,
            field = DriverConfigFormField.COST_PER_KM,
            onConfigInputChange = onConfigInputChange
        )
        ConfigNumberField(
            label = "التكلفة لكل دقيقة",
            value = form.costPerMinute,
            field = DriverConfigFormField.COST_PER_MINUTE,
            onConfigInputChange = onConfigInputChange
        )
    }

    ConfigGroupCard(
        title = "حدود البحث",
        subtitle = "الإعدادات الحالية لا تحتوي على حدود قصوى قابلة للتعديل لوصول السائق إلى الراكب."
    ) {
        ReadOnlySettingRow(
            label = "أقصى مسافة للوصول للراكب",
            value = "Sin límite editable"
        )
        ReadOnlySettingRow(
            label = "أقصى دقائق للوصول للراكب",
            value = "Sin límite editable"
        )
    }

    ConfigGroupCard(
        title = "قواعد المراجعة والرفض",
        subtitle = "قواعد احتياطية عند نقص البيانات أو اكتشاف منطقة محددة."
    ) {
        ConfigNumberField(
            label = "هامش المراجعة %",
            value = form.reviewTolerancePercent,
            field = DriverConfigFormField.REVIEW_TOLERANCE_PERCENT,
            onConfigInputChange = onConfigInputChange
        )
        HorizontalDivider(color = AppBorder)
        ReadOnlySettingRow(
            label = "الأجرة غير مكتشفة",
            value = if (config.rejectIfUnknownFare) "رفض" else "مراجعة"
        )
        ReadOnlySettingRow(
            label = "المسافة غير مكتملة",
            value = if (config.rejectIfUnknownDistance) "رفض" else "مراجعة"
        )
        ReadOnlySettingRow(
            label = "تم اكتشاف منطقة محظورة",
            value = if (config.rejectIfAvoidZoneDetected) "رفض" else "مراجعة"
        )
    }

    ConfigGroupCard(
        title = "التطبيق والمناطق",
        subtitle = "Filtros disponibles en la configuración local actual."
    ) {
        ReadOnlySettingRow(
            label = "Plataformas habilitadas",
            value = "Sin filtro por plataforma"
        )
        if (config.avoidZones.isEmpty()) {
            ReadOnlySettingRow(
                label = "مناطق للتجنب",
                value = "لا توجد مناطق مهيأة"
            )
        } else {
            config.avoidZones.forEach { zone ->
                ReadOnlySettingRow(
                    label = zone.name,
                    value = if (zone.enabled) zone.policy.name.toZonePolicyLabel() else "Inactiva"
                )
            }
        }
    }

    uiState.configStatusMessage?.let { message ->
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = AppPrimary
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            modifier = Modifier.weight(1f),
            onClick = onSaveConfig,
            colors = ButtonDefaults.buttonColors(
                containerColor = AppPrimary,
                contentColor = Color(0xFF06251F)
            )
        ) {
            Text("حفظ")
        }
        OutlinedButton(
            modifier = Modifier.weight(1f),
            onClick = onResetConfig,
            colors = darkOutlinedButtonColors()
        ) {
            Text("Restablecer")
        }
    }
}

@Composable
private fun ConfigGroupCard(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    AppCard(containerColor = AppSurface) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            content()
        }
    }
}

@Composable
private fun ReadOnlySettingRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            modifier = Modifier.weight(1f),
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun ConfigNumberField(
    label: String,
    value: String,
    field: DriverConfigFormField,
    onConfigInputChange: (DriverConfigFormField, String) -> Unit
) {
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = value,
        onValueChange = { onConfigInputChange(field, it) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = AppText,
            unfocusedTextColor = AppText,
            focusedLabelColor = AppPrimary,
            unfocusedLabelColor = AppMutedText,
            cursorColor = AppPrimary,
            focusedBorderColor = AppPrimary,
            unfocusedBorderColor = AppBorder,
            focusedContainerColor = AppSurfaceRaised,
            unfocusedContainerColor = AppSurfaceRaised
        )
    )
}

@Composable
private fun DiagnosticToolsSection(
    uiState: MainUiState,
    overlayActionsEnabled: Boolean,
    onCaptureFrame: () -> Unit,
    onAnalyzeOcr: () -> Unit,
    onStopScreenCapture: () -> Unit,
    onStopService: () -> Unit,
    onRunSimulatedDecision: () -> Unit,
    onShowLastRealDecisionOverlay: () -> Unit
) {
    Text(
        text = "Diagnóstico",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold
    )
    Text(
        text = "أدوات فحص التقاط الشاشة وOCR والنافذة العائمة.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    ConfigGroupCard(
        title = "إجراءات تقنية",
        subtitle = "استخدمها فقط للتحقق من التدفق يدويًا."
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = onCaptureFrame,
                colors = darkOutlinedButtonColors()
            ) {
                Text("التقاط إطار")
            }
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = onAnalyzeOcr,
                colors = darkOutlinedButtonColors()
            ) {
                Text("تحليل OCR")
            }
        }
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = onStopScreenCapture,
            colors = darkOutlinedButtonColors()
        ) {
            Text("إيقاف الالتقاط")
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                modifier = Modifier.weight(1f),
                enabled = overlayActionsEnabled,
                onClick = onRunSimulatedDecision,
                colors = darkOutlinedButtonColors()
            ) {
                Text("نافذة عائمة تجريبية")
            }
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = onStopService,
                colors = darkOutlinedButtonColors()
            ) {
                Text("إيقاف النافذة العائمة")
            }
        }
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            enabled = overlayActionsEnabled,
            onClick = onShowLastRealDecisionOverlay,
            colors = darkOutlinedButtonColors()
        ) {
            Text("عرض آخر قرار فعلي")
        }
    }

    ConfigGroupCard(
        title = "الحالة التقنية",
        subtitle = "الحالة الحالية للأذونات والخدمات والتحليل."
    ) {
        ReadOnlySettingRow(
            label = "النافذة العائمة",
            value = uiState.overlayPermissionStatus
        )
        ReadOnlySettingRow(
            label = "التقاط الشاشة",
            value = uiState.screenCapturePermissionStatus
        )
        ReadOnlySettingRow(
            label = "المراقبة",
            value = uiState.monitorStatus
        )
        ReadOnlySettingRow(
            label = "Servicio overlay",
            value = uiState.serviceStatus
        )
        ReadOnlySettingRow(
            label = "التحليل",
            value = uiState.ocrStatus
        )
        uiState.lastCapturedFrameTimestamp?.let { timestamp ->
            ReadOnlySettingRow(
                label = "آخر لقطة",
                value = timestamp.toDisplayTime()
            )
        }
        if (uiState.lastCapturedFrameWidth != null && uiState.lastCapturedFrameHeight != null) {
            ReadOnlySettingRow(
                label = "حجم الإطار",
                value = "${uiState.lastCapturedFrameWidth} x ${uiState.lastCapturedFrameHeight}"
            )
        }
        uiState.monitorOverlayStatus?.let { status ->
            ReadOnlySettingRow(
                label = "آخر نافذة عائمة",
                value = status
            )
        }
        uiState.decisionStatusMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        uiState.screenCaptureErrorMessage?.let { errorMessage ->
            Text(
                text = "التقاط الشاشة: $errorMessage",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        uiState.ocrErrorMessage?.let { errorMessage ->
            Text(
                text = "OCR: $errorMessage",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }

    ConfigGroupCard(
        title = "آخر نص OCR",
        subtitle = "آخر نص تم التعرف عليه، مع حد للحجم حتى لا يشغل الشاشة بالكامل."
    ) {
        val text = uiState.lastRecognizedText?.takeIf { it.isNotBlank() }
        if (text == null) {
            Text(
                text = "لا يوجد نص مقروء حتى الآن.",
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 180.dp),
                shape = MaterialTheme.shapes.small,
                color = AppSurfaceSubtle,
                border = BorderStroke(1.dp, AppBorder)
            ) {
                Text(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                    text = text,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private enum class HomeStatus {
    READY,
    REQUIRES_PERMISSIONS,
    MONITORING,
    STOPPED
}

private fun MainUiState.toHomeStatus(): HomeStatus {
    val needsPermission = overlayPermissionStatus != "ممنوح" ||
        monitorStatus == "في انتظار الإذن" ||
        monitorErrorMessage?.contains("إذن", ignoreCase = true) == true

    return when {
        isMonitoringActive() -> HomeStatus.MONITORING
        needsPermission -> HomeStatus.REQUIRES_PERMISSIONS
        hasMonitoringSessionStarted && monitorStatus == "متوقف" -> HomeStatus.STOPPED
        else -> HomeStatus.READY
    }
}

private fun MainUiState.isMonitoringActive(): Boolean {
    return when (monitorStatus) {
        "جارٍ المراقبة",
        "جارٍ التحليل",
        "تم اكتشاف عرض",
        "بيانات غير مكتملة",
        "لم يتم اكتشاف عرض" -> true
        else -> false
    }
}

private fun HomeStatus.toTitle(): String {
    return when (this) {
        HomeStatus.READY -> "جاهز للمراقبة"
        HomeStatus.REQUIRES_PERMISSIONS -> "تحتاج إلى أذونات"
        HomeStatus.MONITORING -> "جارٍ المراقبة"
        HomeStatus.STOPPED -> "متوقف"
    }
}

private fun HomeStatus.toDescription(): String {
    return when (this) {
        HomeStatus.READY -> "الإعدادات محملة ويمكنك بدء المراقبة."
        HomeStatus.REQUIRES_PERMISSIONS -> "فعّل الأذونات اللازمة حتى يعرض التطبيق التوصيات."
        HomeStatus.MONITORING -> "يقرأ التطبيق الشاشة المصرح بها وسيعرض تنبيهًا عند اكتشاف عرض."
        HomeStatus.STOPPED -> "المراقبة متوقفة ويمكنك تشغيلها مرة أخرى عند الحاجة."
    }
}

private fun String.toOverlayPermissionSummary(): String {
    return if (this == "ممنوح") "مسموح" else "قيد الانتظار"
}

private fun String.toScreenCaptureSummary(): String {
    return when (this) {
        "التقاط الشاشة مصرح",
        "التقاط الشاشة متاح" -> "مسموح"
        "التقاط الشاشة متوقفة" -> "متوقف"
        else -> "قيد الانتظار"
    }
}

private fun DriverDecision.toArabicLabel(): String {
    return when (this) {
        DriverDecision.ACCEPT -> "قبول"
        DriverDecision.REJECT -> "رفض"
        DriverDecision.REVIEW -> "مراجعة"
    }
}

@Composable
private fun DriverDecision?.toDecisionContainerColor(): Color {
    return when (this) {
        DriverDecision.ACCEPT -> Color(0xFF102A24)
        DriverDecision.REJECT -> AppErrorDim
        DriverDecision.REVIEW -> AppWarningDim
        null -> AppSurface
    }
}

private fun DriverDecision.toDecisionAccentColor(): Color {
    return when (this) {
        DriverDecision.ACCEPT -> AppPrimary
        DriverDecision.REJECT -> AppError
        DriverDecision.REVIEW -> AppWarning
    }
}

private fun TripDecisionResult.toMainReason(): String {
    return rejectionReasons.firstOrNull()
        ?: reviewReasons.firstOrNull()
        ?: when (decision) {
            DriverDecision.ACCEPT -> "العرض يحقق الشروط المحددة."
            DriverDecision.REJECT -> "العرض لا يحقق الشروط المحددة."
            DriverDecision.REVIEW -> "يفضل مراجعة العرض قبل القبول."
        }
}

private fun DriverConfig.toRulesSummary(): String {
    val activeRules = listOf(
        rejectIfUnknownFare,
        rejectIfUnknownDistance,
        rejectIfAvoidZoneDetected
    ).count { it }

    return "$activeRules من 3 قواعد للرفض"
}

private fun Int.toZonesCountText(): String {
    return when (this) {
        0 -> "لا توجد"
        1 -> "منطقة واحدة"
        else -> "$this مناطق نشطة"
    }
}

private fun String.toZonePolicyLabel(): String {
    return when (this) {
        "REJECT" -> "رفض"
        "REVIEW" -> "مراجعة"
        else -> "نشطة"
    }
}

private fun Double?.toDisplayMoney(): String {
    return this?.roundToInt()?.let { "$it جنيه" } ?: "-"
}

private fun Double?.toDisplayNumber(): String {
    return this?.let { "%.1f".format(it) } ?: "-"
}

private fun String.toMoneyInputSummary(): String {
    return takeIf { it.isNotBlank() }?.let { "$it جنيه" } ?: "-"
}

private fun String.toPercentInputSummary(): String {
    return takeIf { it.isNotBlank() }?.let { "$it%" } ?: "-"
}

private fun Long.toDisplayTime(): String {
    return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(this))
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    val sampleUiState = MainUiState(
        overlayPermissionStatus = "ممنوح",
        screenCapturePermissionStatus = "التقاط الشاشة متاح",
        lastCapturedFrameWidth = 1080,
        lastCapturedFrameHeight = 2400,
        lastCapturedFrameTimestamp = 1_700_000_000_000L,
        ocrStatus = "تم اكتشاف النص",
        lastRecognizedText = "230.84 EGP\n7 min / 2.5 km\n37 min / 29.0 km",
        monitorStatus = "تم اكتشاف عرض",
        hasMonitoringSessionStarted = true,
        monitorLastRecognizedText = "Uber 230.84 EGP\n7 min / 2.5 km\n37 min / 29.0 km",
        monitorOverlayStatus = "تم تحديث النافذة بالعرض المكتشف",
        serviceStatus = "يعمل",
        decisionStatusMessage = "Decisión OCR calculada con datos completos",
        lastConfig = DriverConfig.default(),
        configForm = DriverConfigFormState.fromConfig(DriverConfig.default()),
        lastDecision = TripDecisionResult(
            decision = DriverDecision.ACCEPT,
            fareAmount = 230.84,
            egpPerKm = 7.33,
            egpPerHour = 314.78,
            estimatedCost = 144.5,
            estimatedNetProfit = 86.34,
            totalKm = 31.5,
            totalMinutes = 44.0,
            rejectionReasons = emptyList(),
            reviewReasons = emptyList()
        )
    )
    MaterialTheme {
        MainScreenContent(
            uiState = sampleUiState,
            overlayActionsEnabled = true,
            onRequestOverlayPermission = {},
            onRequestScreenCapturePermission = {},
            onStopScreenCapture = {},
            onCaptureFrame = {},
            onAnalyzeOcr = {},
            onStartMonitoring = {},
            onStopMonitoring = {},
            onStopService = {},
            onRunSimulatedDecision = {},
            onShowLastRealDecisionOverlay = {},
            onConfigInputChange = { _, _ -> },
            onSaveConfig = {},
            onResetConfig = {}
        )
    }
}
