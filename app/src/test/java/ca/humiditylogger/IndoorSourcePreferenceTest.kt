package ca.humiditylogger

import org.junit.Assert.assertEquals
import org.junit.Test

class IndoorSourcePreferenceTest {
    @Test
    fun defaultsToDeviceAccessWhenConnected() {
        assertEquals(
            IndoorSource.DEVICE_ACCESS,
            IndoorSourcePreference.resolve(null, deviceAccessConnected = true),
        )
    }

    @Test
    fun defaultsToGoogleHomeWithoutDeviceAccess() {
        assertEquals(
            IndoorSource.GOOGLE_HOME,
            IndoorSourcePreference.resolve(null, deviceAccessConnected = false),
        )
    }

    @Test
    fun explicitChoiceOverridesConnectionState() {
        assertEquals(
            IndoorSource.GOOGLE_HOME,
            IndoorSourcePreference.resolve("GOOGLE_HOME", deviceAccessConnected = true),
        )
        assertEquals(
            IndoorSource.DEVICE_ACCESS,
            IndoorSourcePreference.resolve("DEVICE_ACCESS", deviceAccessConnected = false),
        )
    }
}
