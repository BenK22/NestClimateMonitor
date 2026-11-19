package ca.humiditylogger

import android.content.Context
import android.content.Intent
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Fake expired grants and UI-only recovery in the isolated package; no Google requests or real credentials. */
@RunWith(AndroidJUnit4::class)
class NestRenewalUiTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var store: DeviceAccessStore

    @Before fun setUp() {
        store = DeviceAccessStore(context)
        store.clearAll()
        context.getSharedPreferences("setup", 0).edit().putBoolean("complete", true).commit()
        LoggerScheduler.setEnabled(context, false)
        IndoorSourcePreference.set(context, IndoorSource.DEVICE_ACCESS)
        store.saveConfiguration("synthetic-project", "123-example.apps.googleusercontent.com", "test-secret")
    }

    @After fun tearDown() {
        store.clearAll()
        context.getSharedPreferences("indoor_source", 0).edit().clear().commit()
        context.getSharedPreferences("logger_state", 0).edit().clear().commit()
        context.getSharedPreferences("setup", 0).edit().clear().commit()
    }

    private fun expireGrant() {
        store.saveTokens("test-access", 1, "test-refresh")
        store.markReauthorizationRequired()
    }

    private fun visibleTexts(root: android.view.View): List<String> = when {
        !root.isShown -> emptyList()
        root is TextView -> listOf(root.text.toString())
        root is ViewGroup -> (0 until root.childCount).flatMap { visibleTexts(root.getChildAt(it)) }
        else -> emptyList()
    }

    @Test fun renewalScreenFocusesConsentAndDoesNotShowInitialSetupFields() {
        expireGrant()
        val intent = Intent(context, DeviceAccessSettingsActivity::class.java)
            .putExtra(DeviceAccessSettingsActivity.EXTRA_RENEW_AUTHORIZATION, true)
        ActivityScenario.launch<DeviceAccessSettingsActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                val texts = visibleTexts(activity.findViewById(android.R.id.content))
                assertTrue(texts.contains("Renew Nest access"))
                assertTrue(texts.contains("Complete token refresh"))
                assertFalse(texts.contains("1. OAuth credentials"))
                assertFalse(texts.contains("2. Device Access project"))
                assertTrue(texts.contains("View history"))
                val close = activity.findViewById<ViewGroup>(android.R.id.content)
                    .findViewWithTag<TextView>("device_access_close")
                assertNotNull(close)
                assertEquals("Close Nest connection screen", close.contentDescription.toString())
                assertTrue(close.isClickable && close.isShown)
            }
        }
    }

    @Test fun initialSetupCannotBeBypassedByARenewalIntent() {
        val intent = Intent(context, DeviceAccessSettingsActivity::class.java)
            .putExtra(DeviceAccessSettingsActivity.EXTRA_RENEW_AUTHORIZATION, true)
        ActivityScenario.launch<DeviceAccessSettingsActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                val texts = visibleTexts(activity.findViewById(android.R.id.content))
                assertTrue(texts.contains("1. OAuth credentials"))
                assertFalse(texts.contains("Renew Nest access"))
            }
        }
    }

    @Test fun dashboardRoutesToRenewalAndAllowsHistoryWithoutAResumeLoop() {
        expireGrant()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = instrumentation.addMonitor(DeviceAccessSettingsActivity::class.java.name, null, false)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                val renewal = instrumentation.waitForMonitorWithTimeout(monitor, 5000)
                assertNotNull("Opening the app must show renewal", renewal)
                instrumentation.runOnMainSync {
                    val close = renewal.findViewById<ViewGroup>(android.R.id.content)
                        .findViewWithTag<TextView>("device_access_close")
                    assertNotNull(close)
                    renewal.findViewById<ViewGroup>(android.R.id.content)
                        .findViewWithTag<android.widget.ScrollView>("device_access_form")
                        .fullScroll(android.view.View.FOCUS_DOWN)
                    assertTrue("Close button must stay visible after scrolling the form",
                        close.getGlobalVisibleRect(android.graphics.Rect()))
                    assertTrue(close.performClick())
                }
                instrumentation.waitForIdleSync()
                scenario.onActivity { assertTrue(store.needsReauthorization()) }
                scenario.recreate()
                instrumentation.waitForIdleSync()
                assertEquals("Must not immediately reopen renewal after Back", 1, monitor.hits)
            }
        } finally { instrumentation.removeMonitor(monitor) }
    }

    @Test fun cachedClimateWidgetShowsExpiryForEveryTileSizeAndClearsAfterRenewal() {
        expireGrant()
        instrumentationOnMain {
            for ((width, height) in listOf(150 to 55, 340 to 55, 340 to 140)) {
                val root = ClimateWidgetProvider.buildViews(context, -42, width, height).apply(context, FrameLayout(context))
                assertEquals(NestAuthorizationRecovery.WIDGET_MESSAGE,
                    root.findViewById<TextView>(R.id.widget_updated).text.toString())
                assertTrue(root.contentDescription.toString().contains("cached"))
            }
            IndoorSourcePreference.set(context, IndoorSource.GOOGLE_HOME)
            val home = ClimateWidgetProvider.buildViews(context, -42, 340, 55).apply(context, FrameLayout(context))
            assertFalse(home.findViewById<TextView>(R.id.widget_updated).text.toString().contains("Token expired"))
            IndoorSourcePreference.set(context, IndoorSource.DEVICE_ACCESS)
            store.saveTokens("new-access", Long.MAX_VALUE, "new-refresh")
            val renewed = ClimateWidgetProvider.buildViews(context, -42, 340, 55).apply(context, FrameLayout(context))
            assertFalse(renewed.findViewById<TextView>(R.id.widget_updated).text.toString().contains("Token expired"))
            assertTrue(renewed.contentDescription == null)
        }
    }

    private fun instrumentationOnMain(block: () -> Unit) =
        InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
}
