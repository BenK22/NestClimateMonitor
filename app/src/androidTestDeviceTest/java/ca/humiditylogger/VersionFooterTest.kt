package ca.humiditylogger

import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises real dashboard layout only in the isolated device-test package. */
@RunWith(AndroidJUnit4::class)
class VersionFooterTest {
    @Test
    fun installedVariantVersionIsLastDashboardItemAfterRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            fun assertFooter() {
                scenario.onActivity { activity ->
                    val content = activity.findViewById<ViewGroup>(android.R.id.content)
                    val footer = content.findViewWithTag<TextView>("app_version_footer")
                    assertNotNull(footer)
                    val resource = if (BuildConfig.DEBUG) {
                        R.string.app_version_development
                    } else {
                        R.string.app_version
                    }
                    assertEquals(activity.getString(resource, BuildConfig.VERSION_NAME), footer.text.toString())
                    val dashboard = footer.parent as ViewGroup
                    assertSame(footer, dashboard.getChildAt(dashboard.childCount - 1))
                }
            }
            assertFooter()
            scenario.recreate()
            assertFooter()
        }
    }
}
