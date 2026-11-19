package ca.humiditylogger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reconnection requires a rejected saved grant, not every OAuth, HTTP or connectivity failure. */
class NestAuthorizationRecoveryTest {
    @Test fun onlyRejectedRefreshGrantsRequireNewConsent() {
        assertTrue(NestAuthorizationRecovery.isRejectedRefresh(400, "invalid_grant"))
        assertTrue(NestAuthorizationRecovery.isRejectedRefresh(401, "invalid_grant"))
        for (status in listOf(200, 403, 429, 500, 503)) {
            assertFalse(NestAuthorizationRecovery.isRejectedRefresh(status, "invalid_grant"))
        }
        for (code in listOf(null, "invalid_client", "temporarily_unavailable", "access_denied")) {
            assertFalse(NestAuthorizationRecovery.isRejectedRefresh(400, code))
        }
    }

    @Test fun readsStructuredOAuthErrorWithoutConfusingAnApiErrorObject() {
        assertEquals("invalid_grant", DeviceAccessClient.oauthErrorCode("""{"error":"invalid_grant"}"""))
        assertNull(DeviceAccessClient.oauthErrorCode("""{"error":{"message":"permission denied"}}"""))
        assertNull(DeviceAccessClient.oauthErrorCode("unavailable"))
    }

    @Test fun doesNotInterruptInitialSetupOrGoogleHomeOrHealthyConnections() {
        assertTrue(NestAuthorizationRecovery.shouldRenew(true, true, true, IndoorSource.DEVICE_ACCESS))
        assertFalse(NestAuthorizationRecovery.shouldRenew(false, true, true, IndoorSource.DEVICE_ACCESS))
        assertFalse(NestAuthorizationRecovery.shouldRenew(true, false, true, IndoorSource.DEVICE_ACCESS))
        assertFalse(NestAuthorizationRecovery.shouldRenew(true, true, false, IndoorSource.DEVICE_ACCESS))
        assertFalse(NestAuthorizationRecovery.shouldRenew(true, true, true, IndoorSource.GOOGLE_HOME))
    }

    @Test fun legacyMigrationRecognizesOnlyKnownSavedGrantErrors() {
        assertTrue(NestAuthorizationRecovery.isLegacyGrantFailure(
            "Nest Device Access failed: Google request failed (400): Token has been expired or revoked."))
        assertTrue(NestAuthorizationRecovery.isLegacyGrantFailure("Google request failed (400): invalid_grant"))
        for (message in listOf(null, "Read timed out", "Google request failed (401): Unauthorized",
            "Google request failed (400): invalid_client", "Google request failed (503): unavailable")) {
            assertFalse(NestAuthorizationRecovery.isLegacyGrantFailure(message))
        }
    }
}
