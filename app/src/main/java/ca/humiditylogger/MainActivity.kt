package ca.humiditylogger

import android.Manifest
import android.content.Intent
import android.graphics.DashPathEffect
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.app.AlertDialog
import android.content.res.Configuration
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.PowerManager
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.Switch
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.content.FileProvider
import com.google.home.ForcePermissionFlow
import com.google.home.PermissionsResultStatus
import com.google.home.PermissionsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val importCsv = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(::previewCsvImport)
    }
    private lateinit var reader: HomeReader
    private lateinit var store: ReadingStore
    private lateinit var status: TextView
    private lateinit var temperatureValue: TextView
    private lateinit var humidityValue: TextView
    private lateinit var outdoorTemperatureValue: TextView
    private lateinit var outdoorHumidityValue: TextView
    private lateinit var targetValue: TextView
    private lateinit var modeValue: TextView
    private lateinit var runningValue: TextView
    private lateinit var detailValue: TextView
    private lateinit var readingMeta: TextView
    private lateinit var comfortValue: TextView
    private lateinit var outdoorReadingMeta: TextView
    private lateinit var diagnostics: TextView
    private lateinit var chart: ReadingChartView
    private lateinit var previousDayButton: Button
    private lateinit var nextDayButton: Button
    private lateinit var chartDate: TextView
    private lateinit var indoorTemperatureSummary: SummaryViews
    private lateinit var indoorHumiditySummary: SummaryViews
    private lateinit var outdoorTemperatureSummary: SummaryViews
    private lateinit var outdoorHumiditySummary: SummaryViews
    private lateinit var settingsButton: Button
    private val zoneId = ZoneId.systemDefault()
    private var selectedDay: LocalDate = LocalDate.now(zoneId)
    private var useFahrenheit = true
    private var homePermissionGranted = false
    private var periodicWorkState = "Unknown"
    private var manualWorkState = "Not requested"
    private var nextScheduledMs: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedDay = restoreSelectedHistoryDay(
            savedInstanceState?.getString(KEY_SELECTED_DAY),
            LocalDate.now(zoneId),
        )
        reader = HomeReader.getInstance(applicationContext)
        store = ReadingStore(applicationContext)
        useFahrenheit = getSharedPreferences(DISPLAY_PREFS, MODE_PRIVATE)
            .getBoolean(KEY_USE_FAHRENHEIT, true)
        reader.client.registerActivityResultCallerForPermissions(this)
        setContentView(buildUi())
        refreshUiFromStorage()
        ClimateWidgetProvider.updateAll(applicationContext)
        LoggerScheduler.periodicWork(this).observe(this) { workInfos ->
            val work = workInfos.lastOrNull()
            periodicWorkState = work?.state?.name ?: "Not scheduled"
            nextScheduledMs = work?.nextScheduleTimeMillis?.takeIf { it > System.currentTimeMillis() }
            refreshUiFromStorage()
        }
        LoggerScheduler.manualWork(this).observe(this) { workInfos ->
            manualWorkState = workInfos.lastOrNull()?.state?.name ?: "Not requested"
            refreshUiFromStorage()
        }

        settingsButton.setOnClickListener { showSettingsMenu() }
        previousDayButton.setOnClickListener {
            selectedDay = selectedDay.minusDays(1)
            refreshUiFromStorage()
        }
        nextDayButton.setOnClickListener {
            val today = LocalDate.now(zoneId)
            if (selectedDay < today) {
                selectedDay = selectedDay.plusDays(1)
                refreshUiFromStorage()
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                updatePermissionAndSchedule()
                refreshUiFromStorage()
            }
        }
        if (!getSharedPreferences(SETUP_PREFS, MODE_PRIVATE).getBoolean(KEY_SETUP_COMPLETE, false)) {
            window.decorView.post { showSetupGuide() }
        }
    }

    private fun requestHomePermission() {
        lifecycleScope.launch {
            status.text = "Opening Google Home permission screen…"
            runCatchingCancellable {
                reader.client.requestPermissions(ForcePermissionFlow.FORCE_LAUNCH)
            }.onSuccess { result ->
                if (result.status == PermissionsResultStatus.SUCCESS) {
                    if (LoggerScheduler.isEnabled(this@MainActivity)) {
                        LoggerScheduler.start(this@MainActivity)
                        status.text = "15-minute background logging is active"
                    } else {
                        status.text = "15-minute background logging is disabled"
                    }
                } else {
                    status.text = "Permission result: ${result.status} ${result.errorMessage.orEmpty()}"
                }
                updatePermissionAndSchedule()
            }.onFailure { error ->
                status.text = "Permission error: ${error.message}"
            }
        }
    }

    private suspend fun updatePermissionAndSchedule() {
        val source = IndoorSourcePreference.selected(this)
        if (source == IndoorSource.DEVICE_ACCESS) {
            if (!DeviceAccessStore(this).isConnected()) {
                status.text = "Nest Device Access is selected. Open Settings (⚙) to connect."
                return
            }
            if (LoggerScheduler.isEnabled(this)) {
                LoggerScheduler.start(this)
                status.text = LoggerScheduler.status(this)
                    ?: "Nest Device Access background logging is active"
            } else {
                status.text = "15-minute background logging is disabled"
            }
            return
        }
        val homePermissionResult = runCatchingCancellable { reader.permissionState() }
        homePermissionGranted = homePermissionResult.getOrNull() == PermissionsState.GRANTED
        homePermissionResult.onSuccess { state ->
            val granted = state == PermissionsState.GRANTED
            homePermissionGranted = granted
            if (granted) {
                if (LoggerScheduler.isEnabled(this)) {
                    LoggerScheduler.start(this)
                    status.text = LoggerScheduler.status(this)
                        ?: "15-minute background logging is active"
                } else {
                    status.text = "15-minute background logging is disabled"
                }
            } else {
                status.text = "Google Home isn't connected. Open Settings (⚙) to connect."
            }
        }.onFailure {
            status.text = "Home API initialization failed: ${it.message}"
        }
    }

    private fun refreshUiFromStorage() {
        val recentReadings = store.recent()
        val weatherLocation = WeatherLocationStore.get(this)
        val last = WidgetReadingSelection.latestIndoorClimate(recentReadings) {
            ThermostatSelection.matches(this, it)
        }
        val lastSuccessMs = last?.timestampMs
        val lastError = LoggerScheduler.lastError(this)
        val sampleAge = lastSuccessMs?.let { System.currentTimeMillis() - it }
        status.text = when {
            !LoggerScheduler.isEnabled(this) -> "15-minute logging disabled"
            lastSuccessMs != null -> {
                val prefix = when {
                    sampleAge!! < 30L * 60L * 1000L -> "Last successful sample"
                    sampleAge < 60L * 60L * 1000L -> "Sample delayed"
                    else -> "Data stale"
                }
                "$prefix  •  ${
                    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(lastSuccessMs))
                }${lastError?.let { "  •  $it" }.orEmpty()}"
            }
            else -> when {
                IndoorSourcePreference.selected(this) == IndoorSource.DEVICE_ACCESS &&
                    DeviceAccessStore(this).isConnected() &&
                    DeviceAccessStore(this).selectedDeviceId() == null ->
                    "Choose a Nest thermostat in Settings"
                lastError != null -> lastError
                else -> "Waiting for the first selected indoor sample"
            }
        }
        status.setTextColor(
            when {
                !LoggerScheduler.isEnabled(this) -> TEXT_SECONDARY
                sampleAge == null || sampleAge >= 60L * 60L * 1000L -> Color.rgb(255, 112, 96)
                sampleAge >= 30L * 60L * 1000L -> OUTDOOR_TEMPERATURE
                else -> ACCENT
            }
        )
        LoggerScheduler.diagnostics(this)?.takeIf { it.isNotBlank() }?.let {
            diagnostics.text = climateDiagnosticsOnly(it)
        }

        val outdoorLast = recentReadings.lastOrNull { it.source == weatherLocation.readingSource }
        val dayStart = selectedDay.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val dayEnd = selectedDay.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        chart.dayStartMs = dayStart
        chart.dayEndMs = dayEnd
        chart.useFahrenheit = useFahrenheit
        chart.outdoorSource = weatherLocation.readingSource
        val daySeries = WidgetReadingSelection.select(
            store.between(dayStart, dayEnd),
            weatherLocation.readingSource,
        ) { ThermostatSelection.matches(this, it) }
        val dayReadings = daySeries.displayed.sortedBy(Reading::timestampMs)
        chart.readings = dayReadings
        updateDailySummaries(dayReadings)
        chartDate.text = if (selectedDay == LocalDate.now(zoneId)) {
            "Today"
        } else {
            selectedDay.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
        }
        nextDayButton.visibility = if (selectedDay < LocalDate.now(zoneId)) {
            View.VISIBLE
        } else {
            View.INVISIBLE
        }
        if (last == null) {
            temperatureValue.text = "—"
            humidityValue.text = "—"
            targetValue.text = "—"
            modeValue.text = "—"
            runningValue.text = "—"
            detailValue.text = "Waiting for thermostat state"
            readingMeta.text = "Waiting for the first reading"
            comfortValue.text = "Dew point unavailable"
        } else {
            temperatureValue.text = last.temperatureC?.let(::formatTemperature) ?: "—"
            humidityValue.text = last.humidityPercent?.let { "%.1f%%".format(it) } ?: "—"
            targetValue.text = formatTargets(last)
            modeValue.text = last.systemMode ?: "—"
            runningValue.text = last.runningState ?: "—"
            detailValue.text = buildList {
                last.ecoState?.let { add("Eco: $it") }
                last.holdState?.let { add("Hold: $it") }
                last.changeSource?.let { add("Changed by: $it") }
            }.joinToString("  •  ").ifEmpty { "No hold, Eco, or change-source data exposed" }
            readingMeta.text = buildString {
                append(last.source)
                append("  •  ")
                append(
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                        .format(Date(last.timestampMs))
                )
                val indoorCount = recentReadings.count { ThermostatSelection.matches(this@MainActivity, it) }
                append("  •  $indoorCount readings")
            }
            comfortValue.text = if (last.temperatureC != null && last.humidityPercent != null) {
                val dewPoint = ClimateMetrics.dewPointC(last.temperatureC, last.humidityPercent)
                val formatted = dewPoint?.let(::formatTemperature) ?: "—"
                "Dew point $formatted  •  ${ClimateMetrics.comfortStatus(last.temperatureC, last.humidityPercent)}"
            } else {
                "Dew point unavailable"
            }
        }

        if (outdoorLast == null) {
            outdoorTemperatureValue.text = "—"
            outdoorHumidityValue.text = "—"
            outdoorReadingMeta.text = "${weatherLocation.label}  •  Waiting for outdoor weather"
        } else {
            outdoorTemperatureValue.text = outdoorLast.temperatureC
                ?.let(::formatTemperature) ?: "—"
            outdoorHumidityValue.text = outdoorLast.humidityPercent
                ?.let { "%.1f%%".format(it) } ?: "—"
            outdoorReadingMeta.text = buildString {
                append(weatherLocation.label)
                append("  •  ")
                append(
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                        .format(Date(outdoorLast.timestampMs))
                )
            }
        }
    }

    private fun formatTargets(reading: Reading): String {
        val heat = reading.heatingSetpointC?.let(::formatTargetTemperature)
        val cool = reading.coolingSetpointC?.let(::formatTargetTemperature)
        val mode = reading.systemMode.orEmpty()
        return when {
            mode.contains("heat", ignoreCase = true) && heat != null -> heat
            mode.contains("cool", ignoreCase = true) && cool != null -> cool
            heat != null && cool != null -> "H $heat / C $cool"
            heat != null -> heat
            cool != null -> cool
            else -> "—"
        }
    }

    private fun updateDailySummaries(readings: List<Reading>) {
        val indoor = readings.filterNot { WeatherClient.isOutdoor(it.source) }
        val outdoor = readings.filter { WeatherClient.isOutdoor(it.source) }
        updateSummary(
            indoorTemperatureSummary,
            indoor.mapNotNull { it.temperatureC },
            ::formatTemperature,
        )
        updateSummary(
            indoorHumiditySummary,
            indoor.mapNotNull { it.humidityPercent },
        ) { "%.0f%%".format(it) }
        updateSummary(
            outdoorTemperatureSummary,
            outdoor.mapNotNull { it.temperatureC },
            ::formatTemperature,
        )
        updateSummary(
            outdoorHumiditySummary,
            outdoor.mapNotNull { it.humidityPercent },
        ) { "%.0f%%".format(it) }
    }

    private fun updateSummary(
        views: SummaryViews,
        values: List<Double>,
        formatter: (Double) -> String,
    ) {
        views.minimum.text = values.minOrNull()?.let(formatter) ?: "—"
        views.maximum.text = values.maxOrNull()?.let(formatter) ?: "—"
        views.average.text = values.takeIf { it.isNotEmpty() }?.average()?.let(formatter) ?: "—"
    }

    private fun celsiusToFahrenheit(celsius: Double): Double = celsius * 9.0 / 5.0 + 32.0

    private fun formatTemperature(celsius: Double): String = if (useFahrenheit) {
        "%.1f°".format(celsiusToFahrenheit(celsius))
    } else {
        "%.1f°".format(celsius)
    }

    private fun formatTargetTemperature(celsius: Double): String = if (useFahrenheit) {
        "%.0f°".format(celsiusToFahrenheit(celsius))
    } else {
        "%.1f°".format(celsius)
    }

    private fun climateDiagnosticsOnly(raw: String): String = raw.split("\n\n")
        .filter { block ->
            block.contains("Thermostat", ignoreCase = true) ||
                block.contains("Temperature", ignoreCase = true) ||
                block.contains("Humidity", ignoreCase = true)
        }
        .joinToString("\n\n")
        .ifEmpty { "No climate-device diagnostics available yet." }

    private fun showSettingsMenu() {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        fun rounded(color: Int, radius: Int = 18) = GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }
        fun settingsText(title: String, value: String): Pair<LinearLayout, TextView> {
            val valueView = TextView(this).apply {
                text = value
                textSize = 13f
                setTextColor(TEXT_SECONDARY)
            }
            val textColumn = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(this@MainActivity).apply {
                    text = title
                    textSize = 16f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(TEXT_PRIMARY)
                })
                addView(valueView)
            }
            return textColumn to valueView
        }
        fun clickableRow(title: String, value: String): Pair<LinearLayout, TextView> {
            val (textColumn, valueView) = settingsText(title, value)
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(13), dp(12), dp(13))
                background = rounded(BACKGROUND, 14)
                isClickable = true
                isFocusable = true
                addView(
                    textColumn,
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
                )
                addView(TextView(this@MainActivity).apply {
                    text = "›"
                    textSize = 25f
                    setTextColor(ACCENT)
                })
            }
            return row to valueView
        }
        fun sectionHeader(title: String) = TextView(this).apply {
            text = title.uppercase()
            textSize = 12f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(TEXT_SECONDARY)
            letterSpacing = 0.08f
            setPadding(dp(4), dp(16), dp(4), dp(5))
        }
        fun addMenuRow(panel: LinearLayout, row: View) {
            panel.addView(
                row,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(8) },
            )
        }

        val weatherLocation = WeatherLocationStore.get(this)
        val loggingEnabled = LoggerScheduler.isEnabled(this)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(16))
            background = rounded(CARD, 24)
        }
        panel.addView(TextView(this).apply {
            text = "Settings"
            textSize = 24f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(TEXT_PRIMARY)
            setPadding(dp(4), 0, 0, dp(14))
        })

        val (loggingText, loggingValue) = settingsText(
            "15-minute logging",
            if (loggingEnabled) "Enabled" else "Disabled",
        )
        val loggingSwitch = Switch(this).apply {
            isChecked = loggingEnabled
            contentDescription = "Enable 15-minute logging"
        }
        val loggingRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(13), dp(8), dp(13))
            background = rounded(BACKGROUND, 14)
            addView(
                loggingText,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )
            addView(loggingSwitch)
        }
        panel.addView(loggingRow)

        val (refreshRow, _) = clickableRow("Refresh now", "Request an immediate sample")
        panel.addView(
            refreshRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(8) },
        )
        val (healthRow, _) = clickableRow("Sampling health", periodicWorkState.lowercase().replaceFirstChar { it.uppercase() })
        panel.addView(
            healthRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(8) },
        )

        val (unitsRow, unitsValue) = clickableRow(
            "Temperature units",
            if (useFahrenheit) "Fahrenheit (°F)" else "Celsius (°C)",
        )
        panel.addView(
            unitsRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(8) },
        )
        val (locationRow, _) = clickableRow("Outdoor location", weatherLocation.label)
        panel.addView(
            locationRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(8) },
        )
        val (exportRow, _) = clickableRow("Export readings", "CSV backup")
        panel.addView(
            exportRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(8) },
        )
        val (importRow, _) = clickableRow("Import readings", "Restore a CSV backup")
        panel.addView(
            importRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(8) },
        )
        val dataSummary = "${store.count()} readings • ${formatStorageSize(store.databaseSizeBytes())} • " +
            if (DataRetention.isOneYear(this)) "Keep 1 year" else "Keep unlimited"
        val (dataRow, _) = clickableRow("Stored data", dataSummary)
        panel.addView(
            dataRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(8) },
        )
        val alertSettings = HumidityAlerts.settings(this)
        val alertSummary = if (alertSettings.enabled) {
            "Below ${alertSettings.low}% or above ${alertSettings.high}% for ${alertSettings.durationMinutes} min"
        } else {
            "Disabled"
        }
        val (alertsRow, _) = clickableRow("Humidity alerts", alertSummary)
        panel.addView(
            alertsRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(8) },
        )
        val (googleHomeRow, _) = clickableRow(
            "Google Home access",
            when {
                homePermissionGranted -> "Connected — screen-on/unlocked"
                IndoorSourcePreference.selected(this) == IndoorSource.DEVICE_ACCESS ->
                    "Optional alternate source — tap to check"
                else -> "Not connected"
            },
        )
        val deviceAccessStore = DeviceAccessStore(this)
        val indoorSource = IndoorSourcePreference.selected(this)
        val (indoorSourceRow, _) = clickableRow(
            "Indoor data source",
            when (indoorSource) {
                IndoorSource.DEVICE_ACCESS -> "Nest Device Access"
                IndoorSource.GOOGLE_HOME -> "Google Home"
            },
        )
        val (deviceAccessRow, _) = clickableRow(
            "Nest Device Access",
            when {
                deviceAccessStore.isConnected() -> "Connected — used for background readings"
                deviceAccessStore.isConfigured() -> "Credentials saved — authorization required"
                else -> "Not configured"
            },
        )
        val (setupRow, _) = clickableRow("Setup guide", "Connection, logging, and widgets")
        panel.addView(
            setupRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(8) },
        )
        panel.addView(
            indoorSourceRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(8) },
        )
        panel.addView(
            deviceAccessRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(8) },
        )
        panel.addView(
            googleHomeRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(8) },
        )
        val selectedThermostat = ThermostatSelection.selectedSource(this)
        val (thermostatRow, _) = clickableRow(
            if (indoorSource == IndoorSource.DEVICE_ACCESS) "Nest thermostat" else "Google Home device",
            if (indoorSource == IndoorSource.DEVICE_ACCESS) {
                if (deviceAccessStore.selectedDeviceId() == null) {
                    "Choose one in Device Access"
                } else {
                    "Selected in Device Access"
                }
            } else {
                selectedThermostat ?: "All compatible devices"
            },
        )
        panel.addView(
            thermostatRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(8) },
        )

        val menuRows = listOf(
            setupRow,
            indoorSourceRow,
            deviceAccessRow,
            googleHomeRow,
            thermostatRow,
            loggingRow,
            refreshRow,
            healthRow,
            unitsRow,
            locationRow,
            alertsRow,
            dataRow,
            exportRow,
            importRow,
        )
        menuRows.forEach(panel::removeView)

        addMenuRow(panel, setupRow)
        panel.addView(sectionHeader("Connections"))
        listOf(indoorSourceRow, deviceAccessRow, googleHomeRow, thermostatRow)
            .forEach { addMenuRow(panel, it) }
        panel.addView(sectionHeader("Logging"))
        listOf(loggingRow, refreshRow, healthRow).forEach { addMenuRow(panel, it) }
        panel.addView(sectionHeader("Display and environment"))
        listOf(unitsRow, locationRow, alertsRow).forEach { addMenuRow(panel, it) }
        panel.addView(sectionHeader("Data"))
        listOf(dataRow, exportRow, importRow).forEach { addMenuRow(panel, it) }

        val settingsScroll = ScrollView(this).apply {
            isFillViewport = true
            addView(panel)
        }
        val dialog = AlertDialog.Builder(this).setView(settingsScroll).create()
        val doneButton = Button(this).apply {
            text = "Done"
            isAllCaps = false
            setTextColor(Color.BLACK)
            backgroundTintList = android.content.res.ColorStateList.valueOf(ACCENT)
            setOnClickListener { dialog.dismiss() }
        }
        panel.addView(
            doneButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(14) },
        )

        loggingSwitch.setOnCheckedChangeListener { _, enabled ->
            LoggerScheduler.setEnabled(this, enabled)
            loggingValue.text = if (enabled) "Enabled" else "Disabled"
            refreshUiFromStorage()
        }
        loggingRow.setOnClickListener { loggingSwitch.isChecked = !loggingSwitch.isChecked }
        refreshRow.setOnClickListener {
            LoggerScheduler.refreshNow(this)
            status.text = "Refresh requested…"
            dialog.dismiss()
        }
        healthRow.setOnClickListener {
            dialog.dismiss()
            showSamplingHealthDialog()
        }
        unitsRow.setOnClickListener {
            useFahrenheit = !useFahrenheit
            getSharedPreferences(DISPLAY_PREFS, MODE_PRIVATE).edit()
                .putBoolean(KEY_USE_FAHRENHEIT, useFahrenheit)
                .apply()
            unitsValue.text = if (useFahrenheit) "Fahrenheit (°F)" else "Celsius (°C)"
            refreshUiFromStorage()
            ClimateWidgetProvider.updateAll(applicationContext)
        }
        locationRow.setOnClickListener {
            dialog.dismiss()
            showWeatherLocationDialog()
        }
        exportRow.setOnClickListener {
            dialog.dismiss()
            showExportDialog()
        }
        importRow.setOnClickListener {
            dialog.dismiss()
            importCsv.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain"))
        }
        dataRow.setOnClickListener {
            dialog.dismiss()
            showDataManagementDialog()
        }
        alertsRow.setOnClickListener {
            dialog.dismiss()
            showHumidityAlertDialog()
        }
        googleHomeRow.setOnClickListener {
            dialog.dismiss()
            requestHomePermission()
        }
        deviceAccessRow.setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, DeviceAccessSettingsActivity::class.java))
        }
        setupRow.setOnClickListener {
            dialog.dismiss()
            showSetupGuide()
        }
        indoorSourceRow.setOnClickListener {
            dialog.dismiss()
            showIndoorSourceDialog()
        }
        thermostatRow.setOnClickListener {
            dialog.dismiss()
            if (indoorSource == IndoorSource.DEVICE_ACCESS) {
                startActivity(Intent(this, DeviceAccessSettingsActivity::class.java))
            } else {
                showThermostatSelectionDialog()
            }
        }

        dialog.setOnShowListener {
            dialog.window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setLayout(
                    (resources.displayMetrics.widthPixels * 0.92f).toInt(),
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            }
        }
        dialog.show()
    }

    private fun showStyledDialog(
        title: String,
        message: String? = null,
        body: View? = null,
        positiveLabel: String? = null,
        negativeLabel: String? = null,
        neutralLabel: String? = null,
        onPositive: ((AlertDialog) -> Boolean)? = null,
        onNegative: ((AlertDialog) -> Unit)? = null,
        onNeutral: ((AlertDialog) -> Unit)? = null,
    ): AlertDialog {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        fun rounded(color: Int, radius: Int = 18) = GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(20), dp(22), dp(18))
            background = rounded(CARD, 24)
        }
        panel.addView(TextView(this).apply {
            text = title
            textSize = 23f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(TEXT_PRIMARY)
        })
        message?.let {
            panel.addView(TextView(this).apply {
                text = it
                textSize = 14f
                setTextColor(TEXT_SECONDARY)
                setLineSpacing(0f, 1.18f)
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(10) })
        }
        body?.let {
            panel.addView(it, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(14) })
        }
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }
        panel.addView(actions, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(18) })
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(panel)
        }
        val dialog = AlertDialog.Builder(this).setView(scroll).create()
        fun actionButton(label: String, primary: Boolean, click: () -> Unit) =
            Button(this).apply {
                text = label
                isAllCaps = false
                textSize = 14f
                minHeight = dp(46)
                setTextColor(if (primary) Color.BLACK else ACCENT)
                backgroundTintList = android.content.res.ColorStateList.valueOf(
                    if (primary) ACCENT else Color.TRANSPARENT
                )
                setOnClickListener { click() }
            }
        neutralLabel?.let { label ->
            actions.addView(actionButton(label, false) {
                onNeutral?.invoke(dialog)
                dialog.dismiss()
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        if (neutralLabel == null) actions.addView(Space(this), LinearLayout.LayoutParams(0, 1, 1f))
        negativeLabel?.let { label ->
            actions.addView(actionButton(label, false) {
                onNegative?.invoke(dialog)
                dialog.dismiss()
            })
        }
        positiveLabel?.let { label ->
            actions.addView(actionButton(label, true) {
                if (onPositive?.invoke(dialog) != false) dialog.dismiss()
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { marginStart = dp(6) })
        }
        dialog.setOnShowListener {
            dialog.window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setLayout(
                    (resources.displayMetrics.widthPixels * 0.92f).toInt(),
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            }
        }
        dialog.show()
        return dialog
    }

    private fun showStyledOptionsDialog(
        title: String,
        message: String? = null,
        options: List<String>,
        selectedIndex: Int? = null,
        confirmLabel: String? = null,
        onChosen: (Int) -> Unit,
    ) {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        fun rowBackground(selected: Boolean) = GradientDrawable().apply {
            setColor(if (selected) Color.rgb(31, 52, 45) else BACKGROUND)
            cornerRadius = dp(14).toFloat()
            if (selected) setStroke(dp(1), ACCENT)
        }
        var selected = selectedIndex ?: -1
        val rows = mutableListOf<TextView>()
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        lateinit var dialog: AlertDialog
        fun redraw() {
            rows.forEachIndexed { index, row ->
                val checked = index == selected
                row.text = if (selectedIndex != null) {
                    "${if (checked) "✓" else "  "}  ${options[index]}"
                } else options[index]
                row.background = rowBackground(checked)
                row.setTextColor(if (checked) ACCENT else TEXT_PRIMARY)
            }
        }
        options.forEachIndexed { index, option ->
            val row = TextView(this).apply {
                text = option
                textSize = 16f
                gravity = Gravity.CENTER_VERTICAL
                minHeight = dp(52)
                setPadding(dp(16), dp(10), dp(16), dp(10))
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    if (confirmLabel == null) {
                        onChosen(index)
                        dialog.dismiss()
                    } else {
                        selected = index
                        redraw()
                    }
                }
            }
            rows += row
            body.addView(row, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { if (index > 0) topMargin = dp(7) })
        }
        redraw()
        dialog = showStyledDialog(
            title = title,
            message = message,
            body = body,
            positiveLabel = confirmLabel,
            negativeLabel = "Cancel",
            onPositive = {
                if (selected >= 0) onChosen(selected)
                true
            },
        )
    }

    private fun previewCsvImport(uri: Uri) {
        lifecycleScope.launch {
            val preview = withContext(Dispatchers.IO) {
                runCatchingCancellable {
                    val csv = contentResolver.openInputStream(uri)?.bufferedReader()?.use(CsvImporter::readLimited)
                        ?: error("The selected file could not be opened")
                    CsvImporter.preview(csv, store.all())
                }
            }.getOrElse {
                showStyledDialog(
                    title = "Import failed",
                    message = it.message ?: "This file could not be read.",
                    positiveLabel = "OK",
                )
                return@launch
            }
            showStyledDialog(
                title = "Import readings",
                message =
                    "${preview.readings.size} new readings\n" +
                        "${preview.duplicateCount} duplicates skipped\n" +
                        "${preview.invalidCount} invalid rows skipped",
                negativeLabel = "Cancel",
                positiveLabel = "Import",
                onPositive = {
                    lifecycleScope.launch(Dispatchers.IO) {
                        ReadingImporter.insert(this@MainActivity, store, preview.readings)
                        withContext(Dispatchers.Main) {
                            refreshUiFromStorage()
                            ClimateWidgetProvider.updateAll(applicationContext)
                            status.text = "Imported ${preview.readings.size} readings"
                        }
                    }
                    true
                },
            )
        }
    }

    private fun showSetupGuide() {
        showStyledDialog(
            title = "Set up NestClimateMonitor",
            message =
                "1. For reliable background and screen-off readings, register for Nest Device Access (a one-time, non-refundable US$5 Google fee), enter your project credentials, and connect your thermostat.\n\n" +
                    "2. Leave 15-minute logging enabled. Android may defer a sample slightly to save battery. Opening the app does not sample; use Refresh now for an extra reading without changing the periodic schedule.\n\n" +
                    "3. Choose either Nest Device Access or Google Home as the indoor source. Device Access is selected by default when connected. Google Home works only while the display is on and the phone is unlocked; this app does not need to remain visible.\n\n" +
                    "4. Optional: add a climate or graph widget from your launcher.\n\n" +
                    "Sampling health in Settings shows the latest attempt, success, timing, and battery policy.",
            neutralLabel = "Device Access",
            negativeLabel = "Later",
            positiveLabel = "Done",
            onNeutral = { startActivity(Intent(this, DeviceAccessSettingsActivity::class.java)) },
            onPositive = {
                getSharedPreferences(SETUP_PREFS, MODE_PRIVATE).edit()
                    .putBoolean(KEY_SETUP_COMPLETE, true).apply()
                true
            },
        )
    }

    private fun showIndoorSourceDialog() {
        val current = IndoorSourcePreference.selected(this)
        showStyledOptionsDialog(
            title = "Indoor data source",
            options = listOf(
                "Nest Device Access — background and screen-off",
                "Google Home — screen-on and unlocked",
            ),
            selectedIndex = if (current == IndoorSource.DEVICE_ACCESS) 0 else 1,
            confirmLabel = "Use source",
        ) { selected ->
            val source = if (selected == 0) IndoorSource.DEVICE_ACCESS else IndoorSource.GOOGLE_HOME
            IndoorSourcePreference.set(this, source)
            lifecycleScope.launch { updatePermissionAndSchedule() }
            status.text = if (source == IndoorSource.DEVICE_ACCESS) {
                "Nest Device Access selected"
            } else {
                "Google Home selected — display must be on and unlocked"
            }
        }
    }

    private fun showExportDialog() {
        val dayLabel = if (selectedDay == LocalDate.now(zoneId)) {
            "Today"
        } else {
            selectedDay.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
        }
        showStyledOptionsDialog(
            title = "Export readings",
            options = listOf("Selected day ($dayLabel)", "All readings"),
        ) { which ->
                lifecycleScope.launch {
                    val export = withContext(Dispatchers.IO) {
                        val readings = if (which == 0) {
                            val start = selectedDay.atStartOfDay(zoneId).toInstant().toEpochMilli()
                            val end = selectedDay.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
                            store.between(start, end)
                        } else {
                            store.all()
                        }
                        if (readings.isEmpty()) return@withContext null
                        val suffix = if (which == 0) selectedDay.toString() else "all"
                        CsvExporter.export(this@MainActivity, readings, "home-climate-$suffix.csv")
                    }
                    if (export == null) {
                        status.text = "No readings to export"
                        return@launch
                    }
                    val uri = FileProvider.getUriForFile(
                        this@MainActivity,
                        "$packageName.files",
                        export,
                    )
                    startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/csv"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            },
                            "Share climate readings",
                        )
                    )
                }
            }
    }

    private fun showDataManagementDialog() {
        val retention = if (DataRetention.isOneYear(this)) "one year" else "unlimited"
        showStyledOptionsDialog(
            title = "Stored data",
            message = "${store.count()} readings use ${formatStorageSize(store.databaseSizeBytes())}. Retention is $retention.",
            options = listOf("Export all readings", "Change retention", "Delete all readings"),
        ) { which ->
            when (which) {
                0 -> exportAllReadings()
                1 -> showRetentionDialog()
                2 -> confirmClearReadings()
            }
        }
    }

    private fun showRetentionDialog() {
        val current = if (DataRetention.isOneYear(this)) 1 else 0
        showStyledOptionsDialog(
            title = "Data retention",
            options = listOf("Unlimited", "One year"),
            selectedIndex = current,
            confirmLabel = "Save",
        ) { which ->
            DataRetention.setOneYear(this, which == 1)
            if (which == 1) lifecycleScope.launch(Dispatchers.IO) { DataRetention.apply(this@MainActivity, store) }
            status.text = if (which == 1) "Keeping one year of readings" else "Keeping readings indefinitely"
        }
    }

    private fun confirmClearReadings() {
        showStyledDialog(
            title = "Delete every reading?",
            message = "This cannot be undone. Export a CSV backup first if you may need this history.",
            neutralLabel = "Export first",
            negativeLabel = "Cancel",
            positiveLabel = "Delete",
            onNeutral = { exportAllReadings() },
            onPositive = {
                store.clear()
                refreshUiFromStorage()
                ClimateWidgetProvider.updateAll(applicationContext)
                status.text = "All stored readings deleted"
                true
            },
        )
    }

    private fun exportAllReadings() {
        lifecycleScope.launch {
            val file = withContext(Dispatchers.IO) {
                val readings = store.all()
                if (readings.isEmpty()) null else CsvExporter.export(
                    this@MainActivity,
                    readings,
                    "home-climate-all.csv",
                )
            }
            if (file == null) {
                status.text = "No readings to export"
                return@launch
            }
            val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.files", file)
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "Share climate readings"))
        }
    }

    private fun formatStorageSize(bytes: Long): String = when {
        bytes >= 1_048_576L -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1_024L -> "%.1f KB".format(bytes / 1_024.0)
        else -> "$bytes B"
    }

    private fun showHumidityAlertDialog() {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        fun fieldBackground() = GradientDrawable().apply {
            setColor(BACKGROUND)
            cornerRadius = dp(12).toFloat()
            setStroke(dp(1), Color.rgb(48, 70, 63))
        }
        fun fieldGroup(title: String, explanation: String, input: EditText) =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(this@MainActivity).apply {
                    text = title
                    textSize = 14f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(TEXT_PRIMARY)
                })
                addView(TextView(this@MainActivity).apply {
                    text = explanation
                    textSize = 12f
                    setTextColor(TEXT_SECONDARY)
                    setPadding(0, dp(2), 0, dp(6))
                })
                addView(
                    input,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
            }
        val current = HumidityAlerts.settings(this)
        val enabled = Switch(this).apply {
            text = "Enable humidity notifications"
            isChecked = current.enabled
            setTextColor(TEXT_PRIMARY)
            textSize = 15f
        }
        val low = EditText(this).apply {
            hint = "Percentage, for example 30"
            contentDescription = "Low indoor humidity threshold percentage"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(current.low.toString())
            setTextColor(TEXT_PRIMARY)
            setHintTextColor(TEXT_SECONDARY)
            background = fieldBackground()
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        val high = EditText(this).apply {
            hint = "Percentage, for example 60"
            contentDescription = "High indoor humidity threshold percentage"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(current.high.toString())
            setTextColor(TEXT_PRIMARY)
            setHintTextColor(TEXT_SECONDARY)
            background = fieldBackground()
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        val durations = listOf(30, 60, 120)
        var selectedDuration = current.durationMinutes
        val durationButtons = mutableListOf<Button>()
        fun redrawDurations() {
            durationButtons.forEachIndexed { index, button ->
                val selected = durations[index] == selectedDuration
                button.setTextColor(if (selected) Color.BLACK else ACCENT)
                button.backgroundTintList = android.content.res.ColorStateList.valueOf(
                    if (selected) ACCENT else BACKGROUND
                )
            }
        }
        val durationRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            durations.forEachIndexed { index, minutes ->
                val button = Button(this@MainActivity).apply {
                    text = "$minutes min"
                    isAllCaps = false
                    minHeight = dp(44)
                    setOnClickListener {
                        selectedDuration = minutes
                        redrawDurations()
                    }
                }
                durationButtons += button
                addView(button, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (index > 0) marginStart = dp(6)
                })
            }
        }
        redrawDurations()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(enabled)
            addView(TextView(this@MainActivity).apply {
                text = "A notification is sent when indoor humidity remains outside the selected range."
                textSize = 12f
                setTextColor(TEXT_SECONDARY)
                setPadding(0, dp(5), 0, 0)
            })
            addView(fieldGroup(
                "Low humidity threshold (%)",
                "Alert when indoor humidity remains below this value.",
                low,
            ), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(12)
            })
            addView(fieldGroup(
                "High humidity threshold (%)",
                "Alert when indoor humidity remains above this value.",
                high,
            ), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(12)
            })
            addView(TextView(this@MainActivity).apply {
                text = "Time outside range"
                textSize = 14f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(TEXT_PRIMARY)
                setPadding(0, dp(14), 0, 0)
            })
            addView(TextView(this@MainActivity).apply {
                text = "Humidity must stay below or above a threshold for this long before alerting."
                textSize = 12f
                setTextColor(TEXT_SECONDARY)
                setPadding(0, dp(2), 0, dp(5))
            })
            addView(durationRow)
        }
        showStyledDialog(
            title = "Humidity alerts",
            body = content,
            negativeLabel = "Cancel",
            positiveLabel = "Save",
            onPositive = {
                val lowValue = low.text.toString().toIntOrNull()
                val highValue = high.text.toString().toIntOrNull()
                if (lowValue == null || highValue == null || lowValue !in 0..99 || highValue !in 1..100 || lowValue >= highValue) {
                    low.error = "Low must be below high (0–99)"
                    high.error = "High must be above low (1–100)"
                    return@showStyledDialog false
                }
                HumidityAlerts.save(
                    this,
                    HumidityAlerts.Settings(
                        enabled.isChecked,
                        lowValue,
                        highValue,
                        selectedDuration,
                    ),
                )
                if (enabled.isChecked && Build.VERSION.SDK_INT >= 33 && !HumidityAlerts.canNotify(this)) {
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 4102)
                }
                status.text = if (enabled.isChecked) "Humidity alerts enabled" else "Humidity alerts disabled"
                true
            },
        )
    }

    private fun showSamplingHealthDialog() {
        val stats = LoggerScheduler.runtimeStats(this)
        val lastAttempt = LoggerScheduler.lastAttemptMs(this)?.let {
            DateFormat.getDateTimeInstance().format(Date(it))
        } ?: "Never"
        val lastSuccess = LoggerScheduler.lastSuccessMs(this)?.let {
            DateFormat.getDateTimeInstance().format(Date(it))
        } ?: "Never"
        val next = nextScheduledMs?.let {
            DateFormat.getDateTimeInstance().format(Date(it))
        } ?: "Determined by Android"
        val averageMs = if (stats.runCount == 0L) 0L else stats.totalDurationMs / stats.runCount
        val powerManager = getSystemService(PowerManager::class.java)
        val batteryState = if (powerManager.isIgnoringBatteryOptimizations(packageName)) {
            "Unrestricted"
        } else {
            "Android battery optimization applies"
        }
        val failure = LoggerScheduler.lastError(this) ?: "None"
        val errorHistory = LoggerScheduler.recentErrors(this)
            .takeLast(5)
            .asReversed()
            .joinToString("\n") { error ->
                "${DateFormat.getDateTimeInstance().format(Date(error.timestampMs))}: ${error.message}"
            }
            .ifEmpty { "None recorded" }
        showStyledDialog(
            title = "Sampling health",
            message =
                "Periodic work: $periodicWorkState\n" +
                    "Manual work: $manualWorkState\n" +
                    "Next eligible run: $next\n\n" +
                    "Last attempt: $lastAttempt\n" +
                    "Last success: $lastSuccess\n" +
                    "Last failure: $failure\n\n" +
                    "Recent failures (newest first):\n$errorHistory\n\n" +
                    "Recorded runs: ${stats.runCount}\n" +
                    "Last duration: ${stats.lastDurationMs} ms\n" +
                    "Average duration: $averageMs ms\n" +
                    "Battery policy: $batteryState",
            positiveLabel = "OK",
        )
    }

    private fun showThermostatSelectionDialog() {
        status.text = "Finding compatible climate devices…"
        lifecycleScope.launch {
            val devices = runCatchingCancellable {
                reader.sample().readings.distinctBy { it.deviceId ?: it.source }.sortedBy { it.source }
            }
                .getOrElse {
                    status.text = "Device scan failed: ${it.message}"
                    emptyList()
                }
            if (devices.isEmpty()) {
                showStyledDialog(
                    title = "Indoor device",
                    message = "No compatible climate devices were returned. Check Google Home access and try again.",
                    positiveLabel = "OK",
                )
                return@launch
            }
            val duplicateNames = devices.groupingBy { it.source }.eachCount()
            val labels = devices.map { reading ->
                if ((duplicateNames[reading.source] ?: 0) > 1) {
                    "${reading.source} (${reading.deviceId?.takeLast(6) ?: "unknown"})"
                } else reading.source
            }
            val choices = listOf("All compatible devices") + labels
            val current = ThermostatSelection.selectedSource(this@MainActivity)
            val currentId = ThermostatSelection.selectedDeviceId(this@MainActivity)
            var selected = if (current == null) 0 else (
                devices.indexOfFirst { it.deviceId == currentId || (currentId == null && it.source == current) } + 1
            ).coerceAtLeast(0)
            showStyledOptionsDialog(
                title = "Indoor device",
                options = choices,
                selectedIndex = selected,
                confirmLabel = "Use device",
            ) { chosen ->
                    selected = chosen
                    ThermostatSelection.setSelectedSource(
                        this@MainActivity,
                        if (selected == 0) null else devices[selected - 1].source,
                        if (selected == 0) null else devices[selected - 1].deviceId,
                    )
                    refreshUiFromStorage()
                    ClimateWidgetProvider.updateAll(applicationContext)
                    status.text = "Indoor device selection updated"
                }
        }
    }

    private fun showWeatherLocationDialog() {
        val current = WeatherLocationStore.get(this)
        val input = EditText(this).apply {
            setText(current.label)
            selectAll()
            hint = "Street, city, province, postal code"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS
            setSingleLine(false)
            setTextColor(TEXT_PRIMARY)
            setHintTextColor(TEXT_SECONDARY)
            background = GradientDrawable().apply {
                setColor(BACKGROUND)
                cornerRadius = 12f * resources.displayMetrics.density
                setStroke(resources.displayMetrics.density.toInt().coerceAtLeast(1), Color.rgb(48, 70, 63))
            }
            val padding = (14f * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        showStyledDialog(
            title = "Outdoor weather location",
            message = "Enter an address or place name. Only its coordinates are sent to the weather service.",
            body = input,
            negativeLabel = "Cancel",
            positiveLabel = "Use address",
            onPositive = { dialog ->
                val query = input.text.toString().trim()
                if (query.isEmpty()) {
                    input.error = "Enter an address or place"
                    return@showStyledDialog false
                }
                status.text = "Finding outdoor weather location…"
                lifecycleScope.launch {
                    val location = geocodeLocation(query)
                    if (location == null) {
                        input.error = "Location not found. Add the city and province."
                        status.text = "Could not find that weather location"
                    } else {
                        WeatherLocationStore.set(this@MainActivity, location)
                        status.text = "Outdoor weather set to ${location.label}"
                        refreshUiFromStorage()
                        dialog.dismiss()
                    }
                }
                false
            },
        )
    }

    @Suppress("DEPRECATION")
    private suspend fun geocodeLocation(query: String): WeatherLocation? =
        withContext(Dispatchers.IO) {
            runCatchingCancellable {
                Geocoder(this@MainActivity, Locale.CANADA)
                    .getFromLocationName(query, 1)
                    ?.firstOrNull()
                    ?.let { address ->
                        WeatherLocation(query, address.latitude, address.longitude)
                    }
            }.getOrNull()
        }

    private fun buildUi(): ScrollView {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        fun rounded(color: Int, radius: Int = 18) = GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }
        fun legendEntry(label: String, color: Int, dashed: Boolean) =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(4), 0, dp(4))
                addView(
                    object : View(this@MainActivity) {
                        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            this.color = color
                            strokeWidth = 1.5f * density
                            strokeCap = Paint.Cap.ROUND
                            if (dashed) {
                                pathEffect = DashPathEffect(
                                    floatArrayOf(dp(7).toFloat(), dp(5).toFloat()),
                                    0f,
                                )
                            }
                        }

                        override fun onDraw(canvas: android.graphics.Canvas) {
                            super.onDraw(canvas)
                            val middle = height / 2f
                            canvas.drawLine(dp(2).toFloat(), middle, width - dp(2).toFloat(), middle, linePaint)
                        }
                    },
                    LinearLayout.LayoutParams(dp(38), dp(18)),
                )
                addView(TextView(this@MainActivity).apply {
                    text = label
                    textSize = 11f
                    setTextColor(TEXT_SECONDARY)
                    setPadding(dp(7), 0, 0, 0)
                })
            }
        fun legendRow(
            scope: String,
            temperatureColor: Int,
            humidityColor: Int,
            dashed: Boolean,
        ) = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(this@MainActivity).apply {
                text = scope
                textSize = 12f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(TEXT_PRIMARY)
            }, LinearLayout.LayoutParams(dp(62), ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(
                legendEntry("Temperature", temperatureColor, dashed),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )
            addView(
                legendEntry("Humidity", humidityColor, dashed),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.82f),
            )
        }
        fun sectionTitle(textValue: String) = TextView(this).apply {
            text = textValue
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(TEXT_PRIMARY)
            setPadding(0, dp(24), 0, dp(10))
        }
        fun metricCard(label: String, color: Int): Pair<LinearLayout, TextView> {
            val value = TextView(this).apply {
                text = "—"
                textSize = 36f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(color)
            }
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(18), dp(16), dp(18), dp(16))
                background = rounded(CARD)
                addView(TextView(this@MainActivity).apply {
                    text = label
                    textSize = 13f
                    setTextColor(TEXT_SECONDARY)
                })
                addView(value)
            }
            return card to value
        }
        fun summaryCard(label: String, color: Int): Pair<LinearLayout, SummaryViews> {
            fun stat(labelText: String): Pair<LinearLayout, TextView> {
                val value = TextView(this).apply {
                    text = "—"
                    textSize = 18f
                    gravity = Gravity.CENTER
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(color)
                }
                return LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    addView(TextView(this@MainActivity).apply {
                        text = labelText
                        textSize = 9f
                        gravity = Gravity.CENTER
                        setTextColor(TEXT_SECONDARY)
                    })
                    addView(value)
                } to value
            }
            val stats = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            val (minimumColumn, minimum) = stat("MIN")
            val (maximumColumn, maximum) = stat("MAX")
            val (averageColumn, average) = stat("AVG")
            stats.addView(minimumColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            stats.addView(maximumColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            stats.addView(averageColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(12), dp(12), dp(12))
                background = rounded(CARD, 14)
                addView(TextView(this@MainActivity).apply {
                    text = label
                    textSize = 11f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(TEXT_PRIMARY)
                    setPadding(dp(3), 0, 0, dp(7))
                })
                addView(stats)
            }
            return card to SummaryViews(minimum, maximum, average)
        }
        fun stateColumn(label: String): Pair<LinearLayout, TextView> {
            val value = TextView(this).apply {
                text = "—"
                textSize = 17f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(TEXT_PRIMARY)
                gravity = Gravity.CENTER
            }
            return LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                addView(TextView(this@MainActivity).apply {
                    text = label
                    textSize = 10f
                    gravity = Gravity.CENTER
                    setTextColor(TEXT_SECONDARY)
                })
                addView(value)
            } to value
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(32))
            setBackgroundColor(BACKGROUND)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(
            TextView(this).apply {
                text = "NestClimateMonitor"
                textSize = 24f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(TEXT_PRIMARY)
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        settingsButton = Button(this).apply {
            text = "⚙"
            contentDescription = "Settings"
            textSize = 24f
            setTextColor(ACCENT)
            minWidth = dp(48)
            minimumWidth = dp(48)
            minHeight = dp(48)
            minimumHeight = dp(48)
            setPadding(0, 0, 0, 0)
            backgroundTintList = android.content.res.ColorStateList.valueOf(CARD)
        }
        header.addView(
            settingsButton,
            LinearLayout.LayoutParams(dp(52), dp(48)),
        )
        root.addView(header)
        if (!isLandscape) {
            root.addView(TextView(this).apply {
                text = "Nest  •  every 15 minutes"
                textSize = 14f
                setTextColor(TEXT_SECONDARY)
                setPadding(0, dp(2), 0, dp(16))
            })
        }

        status = TextView(this).apply {
            text = "Initializing Google Home…"
            textSize = 14f
            setTextColor(ACCENT)
            setPadding(dp(16), dp(13), dp(16), dp(13))
            background = rounded(CARD)
        }
        if (!isLandscape) {
            root.addView(status, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        if (!isLandscape) root.addView(sectionTitle("Now"))
        val metrics = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val (temperatureCard, tempText) = metricCard("INSIDE TEMPERATURE", TEMPERATURE)
        temperatureValue = tempText
        metrics.addView(
            temperatureCard,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        metrics.addView(Space(this), LinearLayout.LayoutParams(dp(10), 1))
        val (humidityCard, humidityText) = metricCard("INSIDE HUMIDITY", HUMIDITY)
        humidityValue = humidityText
        metrics.addView(
            humidityCard,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        if (!isLandscape) root.addView(metrics)

        readingMeta = TextView(this).apply {
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(TEXT_SECONDARY)
            setPadding(0, dp(10), 0, 0)
        }
        if (!isLandscape) root.addView(readingMeta)
        comfortValue = TextView(this).apply {
            textSize = 13f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ACCENT)
            setPadding(0, dp(5), 0, 0)
        }
        if (!isLandscape) root.addView(comfortValue)

        val outdoorMetrics = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val (outdoorTemperatureCard, outdoorTempText) = metricCard(
            "OUTSIDE TEMPERATURE",
            OUTDOOR_TEMPERATURE,
        )
        outdoorTemperatureValue = outdoorTempText
        outdoorMetrics.addView(
            outdoorTemperatureCard,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        outdoorMetrics.addView(Space(this), LinearLayout.LayoutParams(dp(10), 1))
        val (outdoorHumidityCard, outdoorHumidityText) = metricCard(
            "OUTSIDE HUMIDITY",
            OUTDOOR,
        )
        outdoorHumidityValue = outdoorHumidityText
        outdoorMetrics.addView(
            outdoorHumidityCard,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        if (!isLandscape) {
            root.addView(
                outdoorMetrics,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(10) },
            )
        }
        outdoorReadingMeta = TextView(this).apply {
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(TEXT_SECONDARY)
            setPadding(0, dp(10), 0, 0)
        }
        if (!isLandscape) root.addView(outdoorReadingMeta)

        val stateCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(14), dp(12), dp(12))
            background = rounded(CARD)
        }
        val stateRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val (targetColumn, targetText) = stateColumn("TARGET")
        targetValue = targetText
        stateRow.addView(targetColumn, LinearLayout.LayoutParams(0, dp(48), 1f))
        val (modeColumn, modeText) = stateColumn("MODE")
        modeValue = modeText
        stateRow.addView(modeColumn, LinearLayout.LayoutParams(0, dp(48), 1f))
        val (runningColumn, runningText) = stateColumn("RUNNING")
        runningValue = runningText
        stateRow.addView(runningColumn, LinearLayout.LayoutParams(0, dp(48), 1f))
        stateCard.addView(stateRow)
        detailValue = TextView(this).apply {
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(TEXT_SECONDARY)
            setPadding(dp(4), dp(10), dp(4), 0)
        }
        stateCard.addView(detailValue)

        if (!isLandscape) root.addView(sectionTitle("Daily history"))
        val dayNavigation = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        previousDayButton = Button(this).apply {
            text = "Previous"
            textSize = 12f
            setTextColor(ACCENT)
            backgroundTintList = android.content.res.ColorStateList.valueOf(CARD)
        }
        dayNavigation.addView(
            previousDayButton,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        chartDate = TextView(this).apply {
            text = "Today"
            textSize = 15f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(TEXT_PRIMARY)
        }
        dayNavigation.addView(
            chartDate,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.15f),
        )
        nextDayButton = Button(this).apply {
            text = "Next"
            textSize = 12f
            setTextColor(ACCENT)
            backgroundTintList = android.content.res.ColorStateList.valueOf(CARD)
        }
        dayNavigation.addView(
            nextDayButton,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        root.addView(dayNavigation)
        chart = ReadingChartView(this)
        val chartCard = FrameLayout(this).apply {
            setPadding(dp(6), dp(6), dp(6), dp(6))
            background = rounded(CARD)
            addView(
                chart,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    if (isLandscape) {
                        dp((resources.configuration.screenHeightDp - 135).coerceAtLeast(220))
                    } else {
                        dp(286)
                    },
                ),
            )
        }
        root.addView(chartCard, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        val chartKey = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = rounded(CARD, 12)
            addView(TextView(this@MainActivity).apply {
                text = "Chart key"
                textSize = 12f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(TEXT_PRIMARY)
                setPadding(0, 0, 0, dp(3))
            })
            addView(legendRow("Inside", TEMPERATURE, HUMIDITY, dashed = false))
            addView(legendRow("Outside", OUTDOOR_TEMPERATURE, OUTDOOR, dashed = true))
        }
        val dailySummaryCards = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val indoorSummaryRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val (indoorTemperatureCard, indoorTemperatureViews) = summaryCard(
            "INDOOR TEMPERATURE",
            TEMPERATURE,
        )
        indoorTemperatureSummary = indoorTemperatureViews
        indoorSummaryRow.addView(
            indoorTemperatureCard,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        indoorSummaryRow.addView(Space(this), LinearLayout.LayoutParams(dp(8), 1))
        val (indoorHumidityCard, indoorHumidityViews) = summaryCard("INDOOR HUMIDITY", HUMIDITY)
        indoorHumiditySummary = indoorHumidityViews
        indoorSummaryRow.addView(
            indoorHumidityCard,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        dailySummaryCards.addView(indoorSummaryRow)

        val outdoorSummaryRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val (outdoorTemperatureSummaryCard, outdoorTemperatureViews) = summaryCard(
            "OUTSIDE TEMPERATURE",
            OUTDOOR_TEMPERATURE,
        )
        outdoorTemperatureSummary = outdoorTemperatureViews
        outdoorSummaryRow.addView(
            outdoorTemperatureSummaryCard,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        outdoorSummaryRow.addView(Space(this), LinearLayout.LayoutParams(dp(8), 1))
        val (outdoorHumiditySummaryCard, outdoorHumidityViews) = summaryCard("OUTSIDE HUMIDITY", OUTDOOR)
        outdoorHumiditySummary = outdoorHumidityViews
        outdoorSummaryRow.addView(
            outdoorHumiditySummaryCard,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        dailySummaryCards.addView(
            outdoorSummaryRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(8) },
        )
        if (!isLandscape) {
            root.addView(
                chartKey,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(8) },
            )
            root.addView(
                dailySummaryCards,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(8) },
            )
            root.addView(
                stateCard,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(16) },
            )
        }

        if (!isLandscape) root.addView(sectionTitle("Device diagnostics"))
        diagnostics = TextView(this).apply {
            text = "The most recent scan will appear here."
            textSize = 12f
            setTextColor(TEXT_SECONDARY)
            setTextIsSelectable(true)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = rounded(CARD)
        }
        if (!isLandscape) {
            root.addView(diagnostics, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            root.addView(TextView(this).apply {
                text = "Outdoor weather data: Open-Meteo"
                textSize = 11f
                gravity = Gravity.CENTER
                setTextColor(OUTDOOR)
                setPadding(0, dp(14), 0, 0)
                setOnClickListener {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://open-meteo.com/")))
                }
            })
        }

        val scrollView = ScrollView(this).apply {
            setBackgroundColor(BACKGROUND)
            addView(root)
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            if (isLandscape) {
                view.setPadding(
                    bars.left + dp(14),
                    bars.top + dp(8),
                    bars.right + dp(14),
                    bars.bottom + dp(8),
                )
            } else {
                view.setPadding(dp(20), bars.top + dp(14), dp(20), bars.bottom + dp(24))
            }
            insets
        }
        ViewCompat.requestApplyInsets(root)
        return scrollView
    }

    override fun onDestroy() {
        store.close()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(KEY_SELECTED_DAY, selectedDay.toString())
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        if (::store.isInitialized && ::status.isInitialized) refreshUiFromStorage()
    }

    private data class SummaryViews(
        val minimum: TextView,
        val maximum: TextView,
        val average: TextView,
    )

    private companion object {
        const val DISPLAY_PREFS = "display_preferences"
        const val KEY_USE_FAHRENHEIT = "use_fahrenheit"
        const val SETUP_PREFS = "setup"
        const val KEY_SETUP_COMPLETE = "complete"
        const val KEY_SELECTED_DAY = "selected_day"
        val BACKGROUND = Color.rgb(9, 16, 14)
        val CARD = Color.rgb(22, 32, 29)
        val TEXT_PRIMARY = Color.rgb(238, 246, 243)
        val TEXT_SECONDARY = Color.rgb(158, 177, 170)
        val ACCENT = Color.rgb(112, 219, 181)
        val TEMPERATURE = Color.rgb(255, 133, 112)
        val HUMIDITY = Color.rgb(86, 190, 255)
        val OUTDOOR = Color.rgb(116, 222, 211)
        val OUTDOOR_TEMPERATURE = Color.rgb(255, 190, 92)
    }
}

internal fun restoreSelectedHistoryDay(saved: String?, today: LocalDate): LocalDate =
    runCatching { saved?.let(LocalDate::parse) }.getOrNull() ?: today
