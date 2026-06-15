package com.sameerasw.airsync.health

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleHealthScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val healthManager = remember { SimpleHealthConnectManager(context) }

    var isAvailable by remember { mutableStateOf(false) }
    var hasPermissions by remember { mutableStateOf(false) }
    var healthStats by remember { mutableStateOf(HealthStats()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var showDatePicker by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { _: Set<String> ->
        scope.launch {
            hasPermissions = healthManager.hasPermissions()
            if (hasPermissions) {
                isLoading = true
                healthStats = healthManager.getStatsForDate(selectedDate)
                isLoading = false
            }
        }
    }

    LaunchedEffect(selectedDate) {
        if (isAvailable && hasPermissions) {
            isLoading = true
            healthStats = healthManager.getStatsForDate(selectedDate)
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        isAvailable = healthManager.isAvailable()
        if (isAvailable) {
            hasPermissions = healthManager.hasPermissions()
            if (hasPermissions) {
                healthStats = healthManager.getStatsForDate(selectedDate)
            }
        }
        isLoading = false
        HealthDataCache.cleanOldCache(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Compact top bar
        TopAppBar(
            title = {
                Text(
                    "Health",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            actions = {
                if (hasPermissions) {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Rounded.CalendarToday, "Select Date",
                            tint = MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = {
                        scope.launch {
                            isLoading = true
                            healthStats = healthManager.getStatsForDate(selectedDate, forceRefresh = true)
                            isLoading = false
                        }
                    }) {
                        Icon(Icons.Rounded.Refresh, "Refresh",
                            tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent
            )
        )

        when {
            !isAvailable -> HealthConnectNotAvailableCard()
            !hasPermissions -> PermissionsNeededCard { permissionLauncher.launch(SimpleHealthConnectManager.PERMISSIONS) }
            else -> {
                var hasAllPerms by remember { mutableStateOf(true) }
                LaunchedEffect(Unit) { hasAllPerms = healthManager.hasAllPermissions() }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                ) {
                    // Date navigation
                    DateChipRow(
                        selectedDate = selectedDate,
                        onPrevious = { selectedDate = selectedDate.minusDays(1) },
                        onNext = { if (selectedDate < LocalDate.now()) selectedDate = selectedDate.plusDays(1) },
                        onToday = { selectedDate = LocalDate.now() }
                    )

                    Spacer(Modifier.height(16.dp))

                    if (isLoading) {
                        Box(
                            Modifier.fillMaxWidth().padding(48.dp),
                            contentAlignment = Alignment.Center
                        ) { CircularProgressIndicator() }
                    } else {
                        // Additional permissions banner
                        if (!hasAllPerms) {
                            PermissionsBanner { permissionLauncher.launch(SimpleHealthConnectManager.PERMISSIONS) }
                            Spacer(Modifier.height(12.dp))
                        }

                        // Activity Rings Summary Card
                        ActivityRingsCard(healthStats)
                        Spacer(Modifier.height(16.dp))

                        // Metric cards in 2-column grid
                        MetricCardsGrid(healthStats)

                        Spacer(Modifier.height(100.dp))
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            selectedDate = selectedDate,
            onDateSelected = { selectedDate = it; showDatePicker = false },
            onDismiss = { showDatePicker = false }
        )
    }
}

// ──── Activity Rings Card ────

@Composable
private fun ActivityRingsCard(stats: HealthStats) {
    val stepsProgress = (stats.steps / 10000f).coerceIn(0f, 1f)
    val caloriesProgress = if (stats.calories > 0) (stats.calories / 500.0).coerceIn(0.0, 1.0).toFloat() else 0f
    val activeProgress = if (stats.activeMinutes > 0) (stats.activeMinutes / 60f).coerceIn(0f, 1f) else 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Row(
            modifier = Modifier.padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Activity Rings
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(140.dp)) {
                ActivityRing(progress = stepsProgress, color = Color(0xFF4FC3F7), strokeWidth = 14.dp, size = 140.dp)
                ActivityRing(progress = caloriesProgress, color = Color(0xFFFF7043), strokeWidth = 14.dp, size = 105.dp)
                ActivityRing(progress = activeProgress, color = Color(0xFF66BB6A), strokeWidth = 14.dp, size = 70.dp)
            }

            Spacer(Modifier.width(24.dp))

            // Ring legends
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RingLegend(
                    color = Color(0xFF4FC3F7),
                    label = "Steps",
                    value = if (stats.steps > 0) "${stats.steps}" else "--",
                    target = "10,000"
                )
                RingLegend(
                    color = Color(0xFFFF7043),
                    label = "Calories",
                    value = if (stats.calories > 0) String.format("%.0f", stats.calories) else "--",
                    target = "500 kcal"
                )
                RingLegend(
                    color = Color(0xFF66BB6A),
                    label = "Active",
                    value = if (stats.activeMinutes > 0) "${stats.activeMinutes}" else "--",
                    target = "60 min"
                )
            }
        }
    }
}

@Composable
private fun ActivityRing(
    progress: Float,
    color: Color,
    strokeWidth: Dp,
    size: Dp
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(1200, easing = FastOutSlowInEasing),
        label = "ring"
    )

    Canvas(modifier = Modifier.size(size)) {
        val stroke = strokeWidth.toPx()
        val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
        val topLeft = Offset(stroke / 2, stroke / 2)

        // Background track
        drawArc(
            color = color.copy(alpha = 0.15f),
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )

        // Progress arc
        drawArc(
            brush = Brush.sweepGradient(
                colors = listOf(color, color.copy(alpha = 0.6f), color),
                center = this.center
            ),
            startAngle = -90f,
            sweepAngle = 360f * animatedProgress,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
    }
}

@Composable
private fun RingLegend(color: Color, label: String, value: String, target: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "$label / $target",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ──── Metric Cards Grid ────

@Composable
private fun MetricCardsGrid(stats: HealthStats) {
    // Row 1: Heart Rate + Sleep
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        GradientMetricCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Rounded.Favorite,
            title = "Heart Rate",
            value = if (stats.heartRate > 0) "${stats.heartRate}" else "--",
            unit = "bpm",
            detail = if (stats.heartRateMin > 0 && stats.heartRateMax > 0) "${stats.heartRateMin}–${stats.heartRateMax}" else null,
            gradient = Brush.linearGradient(listOf(Color(0xFFE53935), Color(0xFFFF7043)))
        )
        GradientMetricCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Rounded.Bedtime,
            title = "Sleep",
            value = if (stats.sleepHours > 0) String.format("%.1f", stats.sleepHours) else "--",
            unit = "hours",
            detail = null,
            gradient = Brush.linearGradient(listOf(Color(0xFF7E57C2), Color(0xFFB388FF)))
        )
    }

    Spacer(Modifier.height(12.dp))

    // Row 2: Distance + Floors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        GradientMetricCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Rounded.DirectionsRun,
            title = "Distance",
            value = if (stats.distance > 0) String.format("%.2f", stats.distance) else "--",
            unit = "km",
            detail = null,
            gradient = Brush.linearGradient(listOf(Color(0xFF43A047), Color(0xFF66BB6A)))
        )
        GradientMetricCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Rounded.Stairs,
            title = "Floors",
            value = if (stats.floorsClimbed > 0) "${stats.floorsClimbed}" else "--",
            unit = "climbed",
            detail = null,
            gradient = Brush.linearGradient(listOf(Color(0xFF8D6E63), Color(0xFFBCAAA4)))
        )
    }

    Spacer(Modifier.height(12.dp))

    // Row 3: Hydration + Weight
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        GradientMetricCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Rounded.WaterDrop,
            title = "Hydration",
            value = if (stats.hydration > 0) String.format("%.1f", stats.hydration) else "--",
            unit = "L",
            detail = null,
            gradient = Brush.linearGradient(listOf(Color(0xFF039BE5), Color(0xFF4FC3F7)))
        )
        GradientMetricCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Rounded.Scale,
            title = "Weight",
            value = if (stats.weight > 0) String.format("%.1f", stats.weight) else "--",
            unit = "kg",
            detail = null,
            gradient = Brush.linearGradient(listOf(Color(0xFF5C6BC0), Color(0xFF9FA8DA)))
        )
    }

    // Vitals section header
    if (hasVitals(stats)) {
        Spacer(Modifier.height(20.dp))
        Text(
            "Vitals",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Vitals cards
        if (stats.restingHeartRate > 0) {
            CompactVitalRow(Icons.Rounded.MonitorHeart, "Resting HR", "${stats.restingHeartRate} bpm", Color(0xFFE91E63))
        }
        if (stats.bloodPressureSystolic > 0 && stats.bloodPressureDiastolic > 0) {
            CompactVitalRow(Icons.Rounded.Bloodtype, "Blood Pressure", "${stats.bloodPressureSystolic}/${stats.bloodPressureDiastolic} mmHg", Color(0xFFF44336))
        }
        if (stats.oxygenSaturation > 0) {
            CompactVitalRow(Icons.Rounded.Air, "Blood Oxygen", String.format("%.1f%%", stats.oxygenSaturation), Color(0xFF00BCD4))
        }
        if (stats.bodyTemperature > 0) {
            CompactVitalRow(Icons.Rounded.Thermostat, "Temperature", String.format("%.1f°C", stats.bodyTemperature), Color(0xFFFF9800))
        }
        if (stats.bloodGlucose > 0) {
            CompactVitalRow(Icons.Rounded.Bloodtype, "Glucose", String.format("%.1f mmol/L", stats.bloodGlucose), Color(0xFF9C27B0))
        }
        if (stats.vo2Max > 0) {
            CompactVitalRow(Icons.Rounded.FitnessCenter, "VO2 Max", String.format("%.1f", stats.vo2Max), Color(0xFF009688))
        }
    }
}

private fun hasVitals(stats: HealthStats): Boolean {
    return stats.restingHeartRate > 0 || stats.bloodPressureSystolic > 0 ||
            stats.oxygenSaturation > 0 || stats.bodyTemperature > 0 ||
            stats.bloodGlucose > 0 || stats.vo2Max > 0
}

@Composable
private fun GradientMetricCard(
    modifier: Modifier,
    icon: ImageVector,
    title: String,
    value: String,
    unit: String,
    detail: String?,
    gradient: Brush
) {
    Card(
        modifier = modifier.height(140.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(gradient)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Icon row
                Icon(
                    icon,
                    contentDescription = title,
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(24.dp)
                )

                // Value
                Column {
                    Text(
                        value,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        unit,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    if (detail != null) {
                        Text(
                            detail,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            // Title at bottom right
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }
    }
}

@Composable
private fun CompactVitalRow(icon: ImageVector, label: String, value: String, color: Color) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
                }
                Text(label, style = MaterialTheme.typography.bodyLarge)
            }
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

// ──── Date Navigation ────

@Composable
private fun DateChipRow(
    selectedDate: LocalDate,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.Rounded.ChevronLeft, "Previous Day")
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = when (selectedDate) {
                    LocalDate.now() -> "Today"
                    LocalDate.now().minusDays(1) -> "Yesterday"
                    else -> selectedDate.format(DateTimeFormatter.ofPattern("EEE, MMM dd"))
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (selectedDate != LocalDate.now()) {
                TextButton(onClick = onToday, contentPadding = PaddingValues(0.dp)) {
                    Text("Go to Today", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        IconButton(
            onClick = onNext,
            enabled = selectedDate < LocalDate.now()
        ) {
            Icon(Icons.Rounded.ChevronRight, "Next Day")
        }
    }
}

// ──── Supporting Dialogs and Cards ────

@Composable
private fun HealthConnectNotAvailableCard() {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Text("Health Connect Not Available", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Text("Install Health Connect to access data from Google Fit, Samsung Health, and other apps.", style = MaterialTheme.typography.bodyMedium)
            Button(
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata")))
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Rounded.Download, null)
                Spacer(Modifier.width(8.dp))
                Text("Install Health Connect")
            }
        }
    }
}

@Composable
private fun PermissionsNeededCard(onRequest: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Security, contentDescription = null)
                Text("Permissions Needed", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Text("Grant permissions to access your health data from Health Connect.", style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onRequest, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Rounded.Security, null)
                Spacer(Modifier.width(8.dp))
                Text("Grant Permissions")
            }
        }
    }
}

@Composable
private fun PermissionsBanner(onRequest: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text("More metrics available", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text("Grant extra permissions for vitals data", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onRequest) { Text("Grant") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerDialog(
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = selectedDate.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    )

    androidx.compose.material3.DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                datePickerState.selectedDateMillis?.let { millis ->
                    val date = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                    onDateSelected(date)
                }
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) {
        DatePicker(state = datePickerState)
    }
}
