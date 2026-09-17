package ca.humiditylogger

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceAccessStoreTest {
    private lateinit var store: DeviceAccessStore

    @Before
    fun setUp() {
        store = DeviceAccessStore(ApplicationProvider.getApplicationContext())
        store.clearAll()
    }

    @After
    fun tearDown() {
        store.clearAll()
    }

    @Test
    fun encryptsAndRestoresSecretAndTokens() {
        store.saveConfiguration(PROJECT_ID, CLIENT_ID, CLIENT_SECRET)
        store.saveTokens("access-token", 123456L, "refresh-token")

        assertTrue(store.isConfigured())
        assertTrue(store.isConnected())
        assertEquals(CLIENT_SECRET, store.clientSecret())
        assertEquals("access-token", store.accessToken()?.value)
        assertEquals("refresh-token", store.refreshToken())

        store.clearConnection()
        assertTrue(store.isConfigured())
        assertFalse(store.isConnected())
        assertNull(store.refreshToken())
    }

    @Test
    fun changingClientIdentityRequiresANewSecret() {
        store.saveConfiguration(PROJECT_ID, CLIENT_ID, CLIENT_SECRET)
        store.saveConfiguration("different-project", CLIENT_ID, null)

        assertFalse(store.isConfigured())
        assertFalse(store.configuration().hasClientSecret)
    }

    @Test
    fun projectAndOAuthSectionsSaveIndependently() {
        store.saveProjectId(PROJECT_ID)
        assertFalse(store.isConfigured())

        store.saveOAuthCredentials(CLIENT_ID, CLIENT_SECRET)
        assertTrue(store.isConfigured())

        store.saveProjectId("different-project")
        assertTrue(store.isConfigured())
        assertEquals(CLIENT_SECRET, store.clientSecret())
        assertFalse(store.isConnected())
    }

    @Test
    fun oauthSectionRequiresASecretOnFirstSave() {
        try {
            store.saveOAuthCredentials(CLIENT_ID, null)
            fail("Expected the missing secret to be rejected")
        } catch (expected: IllegalArgumentException) {
            assertEquals("OAuth Client Secret is required.", expected.message)
        }
    }

    @Test
    fun pendingOAuthStateExpiresAndCanOnlyBeConsumedOnce() {
        store.savePendingOAuthState("one-time", createdAtMs = 1_000L)
        assertEquals("one-time", store.pendingOAuthState(nowMs = 2_000L))

        store.clearPendingOAuthState()
        assertNull(store.pendingOAuthState(nowMs = 2_000L))

        store.savePendingOAuthState("expired", createdAtMs = 1_000L)
        assertNull(store.pendingOAuthState(nowMs = 11L * 60L * 1000L + 1_000L))
    }

    private companion object {
        const val PROJECT_ID = "00000000-0000-0000-0000-000000000000"
        const val CLIENT_ID = "123-example.apps.googleusercontent.com"
        const val CLIENT_SECRET = "test-client-secret"
    }
}
