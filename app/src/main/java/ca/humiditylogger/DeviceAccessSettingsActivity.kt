package ca.humiditylogger

import android.app.AlertDialog
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class DeviceAccessSettingsActivity : ComponentActivity() {
    private lateinit var store: DeviceAccessStore
    private lateinit var projectId: EditText
    private lateinit var clientId: EditText
    private lateinit var clientSecret: EditText
    private lateinit var authorizationCode: EditText
    private lateinit var status: TextView
    private lateinit var authorizeButton: Button
    private lateinit var completeButton: Button
    private lateinit var testButton: Button
    private lateinit var disconnectButton: Button
    private lateinit var oauthIndicator: TextView
    private lateinit var projectIndicator: TextView
    private lateinit var connectionIndicator: TextView
    private var awaitingAuthorization = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        awaitingAuthorization = savedInstanceState?.getBoolean(KEY_AWAITING_AUTHORIZATION) ?: false
        store = DeviceAccessStore(applicationContext)
        setContentView(buildContent())
        renderStatus()
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) {
            renderStatus()
            if (awaitingAuthorization) importAuthorizationFromClipboard()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_AWAITING_AUTHORIZATION, awaitingAuthorization)
        super.onSaveInstanceState(outState)
    }

    private fun buildContent(): View {
        val config = store.configuration()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(32))
            setBackgroundColor(BACKGROUND)
        }
        content.addView(TextView(this).apply {
            text = "Device Access"
            textSize = 28f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(TEXT_PRIMARY)
        })
        content.addView(TextView(this).apply {
            text = "Direct Nest access for reliable screen-off readings"
            textSize = 14f
            setTextColor(TEXT_SECONDARY)
            setPadding(0, dp(4), 0, dp(16))
        })

        status = TextView(this).apply {
            textSize = 14f
            setTextColor(TEXT_PRIMARY)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(CARD, 14)
        }
        content.addView(status, matchWrap(bottom = 16))

        sectionHeader("1. OAuth credentials").also { (view, indicator) ->
            oauthIndicator = indicator
            content.addView(view)
        }
        content.addView(helpText("Create a Web application client in Google Auth Platform, then enable the Smart Device Management API in the same Google Cloud project. The client secret is encrypted with Android Keystore and never exported."))
        clientId = field("Web OAuth Client ID", config.clientId)
        content.addView(clientId, matchWrap(bottom = 10))
        clientSecret = field(
            if (config.hasClientSecret) "Client Secret (saved — leave blank to keep)" else "Client Secret",
            "",
            secret = true,
        )
        content.addView(clientSecret, matchWrap(bottom = 10))
        val saveOAuthButton = actionButton("Save OAuth credentials", secondary = true)
        content.addView(saveOAuthButton, matchWrap(bottom = 10))
        val oauthClientsLink = externalLink("Open Google Auth Platform ↗")
        content.addView(oauthClientsLink, matchWrap(bottom = 8))
        val sdmApiLink = externalLink("Enable Smart Device Management API ↗")
        content.addView(sdmApiLink, matchWrap(bottom = 20))

        sectionHeader("2. Device Access project").also { (view, indicator) ->
            projectIndicator = indicator
            content.addView(view)
        }
        content.addView(helpText("Create or edit the Nest Device Access project using the OAuth Client ID above. Its Project ID is different from the Google Cloud project ID."))
        projectId = field("Device Access Project ID", config.projectId)
        content.addView(projectId, matchWrap(bottom = 10))
        val saveProjectButton = actionButton("Save project ID", secondary = true)
        content.addView(saveProjectButton, matchWrap(bottom = 10))
        val deviceAccessConsoleLink = externalLink("Open Nest Device Access Console ↗")
        content.addView(deviceAccessConsoleLink, matchWrap(bottom = 20))

        sectionHeader("3. Connect Nest").also { (view, indicator) ->
            connectionIndicator = indicator
            content.addView(view)
        }
        content.addView(helpText("1. Open Google authorization.\n2. Allow access to your home and thermostat.\n3. At the Google page, tap the address bar and copy the complete URL.\n4. Return to this app.\n5. Paste the copied Google URL below."))
        authorizeButton = actionButton("Open Google authorization")
        content.addView(authorizeButton, matchWrap(bottom = 10))
        authorizationCode = field("Authorization code or redirected URL", "", multiline = true)
        content.addView(authorizationCode, matchWrap(bottom = 10))
        completeButton = actionButton("Complete connection")
        content.addView(completeButton, matchWrap(bottom = 10))
        testButton = actionButton("Test and choose thermostat", secondary = true)
        content.addView(testButton, matchWrap(bottom = 20))

        content.addView(sectionTitle("Connection data"))
        disconnectButton = actionButton("Disconnect Nest", secondary = true)
        content.addView(disconnectButton, matchWrap(bottom = 10))
        val eraseButton = actionButton("Erase all Device Access data", destructive = true)
        content.addView(eraseButton, matchWrap(bottom = 18))
        val doneButton = actionButton("Done")
        content.addView(doneButton)

        saveProjectButton.setOnClickListener {
            runCatching(::saveProjectId).onFailure(::showError)
        }
        deviceAccessConsoleLink.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(DEVICE_ACCESS_CONSOLE_URL)))
        }
        saveOAuthButton.setOnClickListener {
            runCatching(::saveOAuthCredentials).onFailure(::showError)
        }
        oauthClientsLink.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(OAUTH_CLIENTS_URL)))
        }
        sdmApiLink.setOnClickListener { openSdmApiConsole() }
        authorizeButton.setOnClickListener {
            openGoogleAuthorization()
        }
        completeButton.setOnClickListener { completeConnection() }
        testButton.setOnClickListener { testConnection() }
        disconnectButton.setOnClickListener { confirmDisconnect() }
        eraseButton.setOnClickListener { confirmErase() }
        doneButton.setOnClickListener { finish() }

        return ScrollView(this).apply {
            isFillViewport = true
            addView(content)
        }
    }

    private fun saveProjectId() {
        store.saveProjectId(projectId.text.toString())
        status.text = "Device Access Project ID saved locally."
        Toast.makeText(this, "Project ID saved", Toast.LENGTH_SHORT).show()
        renderIndicators()
        renderButtons()
    }

    private fun saveOAuthCredentials() {
        store.saveOAuthCredentials(
            clientId.text.toString(),
            clientSecret.text.toString().takeIf { it.isNotBlank() },
        )
        clientSecret.setText("")
        clientSecret.hint = "Client Secret (saved — leave blank to keep)"
        status.text = "OAuth credentials saved securely."
        Toast.makeText(this, "OAuth credentials saved", Toast.LENGTH_SHORT).show()
        renderIndicators()
        renderButtons()
    }

    private fun completeConnection() {
        if (!store.isConfigured()) {
            showError(IllegalStateException("Save the project ID and OAuth credentials first."))
            return
        }
        setBusy(true, "Exchanging the authorization code with Google…")
        lifecycleScope.launch {
            val result = runCatching {
                DeviceAccessClient(this@DeviceAccessSettingsActivity).link(authorizationCode.text.toString())
            }
            setBusy(false)
            result.onSuccess { readings ->
                    authorizationCode.setText("")
                    chooseThermostat(readings)
                    renderIndicators()
                    LoggerScheduler.refreshNow(this@DeviceAccessSettingsActivity)
                }
                .onFailure(::showError)
        }
    }

    private fun openGoogleAuthorization() {
        val url = runCatching { DeviceAccessClient(this).authorizationUrl() }
            .getOrElse {
                showError(it)
                return
            }
        AlertDialog.Builder(this)
            .setTitle("Copy the final Google URL")
            .setMessage(
                "After you approve access, Google opens google.com. Chrome may show only “google.com”. " +
                    "Tap the address bar to reveal the complete address, then copy it and return here. " +
                    "The app will import the copied authorization code automatically."
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Open Google") { _, _ ->
                awaitingAuthorization = true
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
            .show()
    }

    private fun openSdmApiConsole() {
        val enteredClientId = clientId.text.toString().trim()
        val projectNumber = enteredClientId.substringBefore('-').takeIf {
            it.isNotBlank() && it.all(Char::isDigit)
        }
        if (projectNumber == null) {
            showError(IllegalArgumentException("Enter your Web OAuth Client ID first."))
            return
        }
        val url = "$SDM_API_CONSOLE_URL?project=${Uri.encode(projectNumber)}"
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private fun importAuthorizationFromClipboard() {
        val clipboard = getSystemService(ClipboardManager::class.java)
        val copied = clipboard?.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
        // Automatic clipboard import must only accept the redirect URL. The manual field still
        // accepts a bare code, but treating arbitrary clipboard text as a code is too surprising.
        val valid = copied.contains("code=") &&
            runCatching { DeviceAccessClient.extractAuthorizationCode(copied) }.isSuccess
        if (valid) {
            authorizationCode.setText(copied)
            awaitingAuthorization = false
            status.text = "Authorization URL copied from Chrome. Tap Complete connection."
            Toast.makeText(this, "Google authorization URL imported", Toast.LENGTH_SHORT).show()
        } else {
            status.text = "Copy the complete google.com/?code=… address from Chrome, then return here."
        }
    }

    private fun testConnection() {
        setBusy(true, "Reading thermostats from Google SDM…")
        lifecycleScope.launch {
            val result = runCatching { DeviceAccessClient(this@DeviceAccessSettingsActivity).thermostats() }
            setBusy(false)
            result
                .onSuccess(::chooseThermostat)
                .onFailure(::showError)
        }
    }

    private fun chooseThermostat(readings: List<Reading>) {
        if (readings.isEmpty()) {
            status.text = "Connected, but Google returned no supported Nest thermostats."
            return
        }
        if (readings.size == 1) {
            store.setSelectedDeviceId(readings.single().deviceId)
            status.text = "Connected to ${readings.single().source}."
            return
        }
        val selectedId = store.selectedDeviceId()
        AlertDialog.Builder(this)
            .setTitle("Choose thermostat")
            .setSingleChoiceItems(
                readings.map { it.source }.toTypedArray(),
                readings.indexOfFirst { it.deviceId == selectedId }.coerceAtLeast(0),
            ) { dialog, which ->
                store.setSelectedDeviceId(readings[which].deviceId)
                status.text = "Connected to ${readings[which].source}."
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDisconnect() {
        AlertDialog.Builder(this)
            .setTitle("Disconnect Nest?")
            .setMessage("Access and refresh tokens will be erased. Your project credentials and saved readings remain.")
            .setPositiveButton("Disconnect") { _, _ ->
                store.clearConnection()
                renderStatus()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmErase() {
        AlertDialog.Builder(this)
            .setTitle("Erase Device Access data?")
            .setMessage("Project credentials, tokens, and thermostat selection will be permanently removed from this phone. Climate readings remain.")
            .setPositiveButton("Erase") { _, _ ->
                store.clearAll()
                projectId.setText("")
                clientId.setText("")
                clientSecret.setText("")
                clientSecret.hint = "Client Secret"
                renderStatus()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun renderStatus() {
        val config = store.configuration()
        status.text = when {
            store.isConnected() -> "Connected to Nest Device Access. Background samples will use Google SDM."
            store.isConfigured() -> "Credentials saved. Complete Google authorization to connect Nest."
            else -> "Not configured"
        }
        if (::projectId.isInitialized && !projectId.hasFocus() && projectId.text.isBlank()) {
            projectId.setText(config.projectId)
        }
        if (::clientId.isInitialized && !clientId.hasFocus() && clientId.text.isBlank()) {
            clientId.setText(config.clientId)
        }
        renderIndicators()
        renderButtons()
    }

    private fun renderIndicators() {
        if (!::oauthIndicator.isInitialized) return
        val config = store.configuration()
        setIndicator(oauthIndicator, config.clientId.isNotBlank() && config.hasClientSecret)
        setIndicator(projectIndicator, config.projectId.isNotBlank())
        setIndicator(connectionIndicator, store.isConnected())
    }

    private fun setIndicator(view: TextView, complete: Boolean) {
        view.text = if (complete) "✓" else "✕"
        view.setTextColor(if (complete) COMPLETE else INCOMPLETE)
        view.contentDescription = if (complete) "Complete" else "Not complete"
    }

    private fun renderButtons() {
        if (!::authorizeButton.isInitialized) return
        authorizeButton.isEnabled = store.isConfigured()
        completeButton.isEnabled = store.isConfigured()
        testButton.isEnabled = store.isConnected()
        disconnectButton.isEnabled = store.isConnected()
    }

    private fun setBusy(busy: Boolean, message: String? = null) {
        authorizeButton.isEnabled = !busy && store.isConfigured()
        completeButton.isEnabled = !busy && store.isConfigured()
        testButton.isEnabled = !busy && store.isConnected()
        disconnectButton.isEnabled = !busy && store.isConnected()
        if (message != null) status.text = message
    }

    private fun showError(error: Throwable) {
        val message = error.message ?: "Device Access request failed."
        status.text = message
        renderIndicators()
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun field(hint: String, value: String, secret: Boolean = false, multiline: Boolean = false) =
        EditText(this).apply {
            this.hint = hint
            setText(value)
            setHintTextColor(TEXT_MUTED)
            setTextColor(TEXT_PRIMARY)
            textSize = 15f
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(CARD, 12, BORDER)
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            inputType = when {
                secret -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                multiline -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                else -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            }
            if (multiline) {
                minLines = 2
                gravity = Gravity.TOP
            } else isSingleLine = true
        }

    private fun actionButton(label: String, secondary: Boolean = false, destructive: Boolean = false) =
        Button(this).apply {
            text = label
            isAllCaps = false
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(if (secondary) ACCENT else if (destructive) Color.WHITE else Color.BLACK)
            background = rounded(
                when {
                    destructive -> DESTRUCTIVE
                    secondary -> CARD
                    else -> ACCENT
                },
                12,
                if (secondary) BORDER else null,
            )
        }

    private fun externalLink(label: String) = TextView(this).apply {
        text = label
        textSize = 15f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(ACCENT)
        paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
        setPadding(dp(4), dp(8), dp(4), dp(8))
        isClickable = true
        isFocusable = true
        contentDescription = "$label. Opens an external website."
    }

    private fun sectionHeader(label: String): Pair<LinearLayout, TextView> {
        val indicator = TextView(this).apply {
            textSize = 21f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(this@DeviceAccessSettingsActivity).apply {
                text = label
                textSize = 18f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(TEXT_PRIMARY)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(indicator, LinearLayout.LayoutParams(dp(32), ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        return row to indicator
    }

    private fun sectionTitle(label: String) = TextView(this).apply {
        text = label
        textSize = 18f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(TEXT_PRIMARY)
        setPadding(0, 0, 0, dp(6))
    }

    private fun helpText(value: String) = TextView(this).apply {
        text = value
        textSize = 13f
        setTextColor(TEXT_SECONDARY)
        setPadding(0, 0, 0, dp(10))
    }

    private fun rounded(color: Int, radius: Int, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        stroke?.let { setStroke(dp(1), it) }
    }

    private fun matchWrap(bottom: Int = 0) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply { bottomMargin = dp(bottom) }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private companion object {
        val BACKGROUND = Color.rgb(9, 16, 14)
        val CARD = Color.rgb(20, 32, 28)
        val BORDER = Color.rgb(48, 70, 63)
        val TEXT_PRIMARY = Color.rgb(236, 244, 241)
        val TEXT_SECONDARY = Color.rgb(168, 187, 180)
        val TEXT_MUTED = Color.rgb(113, 137, 128)
        val ACCENT = Color.rgb(112, 219, 181)
        val DESTRUCTIVE = Color.rgb(166, 55, 55)
        val COMPLETE = Color.rgb(112, 219, 181)
        val INCOMPLETE = Color.rgb(255, 111, 97)
        const val DEVICE_ACCESS_CONSOLE_URL = "https://console.nest.google.com/device-access"
        const val OAUTH_CLIENTS_URL = "https://console.cloud.google.com/auth/clients/"
        const val SDM_API_CONSOLE_URL =
            "https://console.developers.google.com/apis/api/smartdevicemanagement.googleapis.com/overview"
        const val KEY_AWAITING_AUTHORIZATION = "awaiting_authorization"
    }
}
