package ca.humiditylogger

import android.content.Context
import java.io.IOException

/** Permanent refresh-grant failure, distinct from an ordinary short-lived access-token expiry. */
class NestAuthorizationRequiredException : IOException("Nest authorization expired. Renew Nest access in the app.")

/** Shared, credential-free decisions for app routing and cached-reading widget warnings. */
object NestAuthorizationRecovery {
    /** Only a rejected refresh grant needs consent; network errors and invalid clients are not expiry. */
    fun isRejectedRefresh(status: Int, errorCode: String?): Boolean =
        status in setOf(400, 401) && errorCode == "invalid_grant"

    /** Recognizes the narrow grant-expiry errors recorded by older versions, before typed state existed. */
    fun isLegacyGrantFailure(message: String?): Boolean {
        val value = message.orEmpty()
        return value.contains("Nest authorization has expired", ignoreCase = true) ||
            value.contains("Nest authorization expired", ignoreCase = true) ||
            (value.contains("Google request failed (400)") &&
                (value.contains("invalid_grant") || value.contains("Token has been expired or revoked", ignoreCase = true)))
    }

    /** Previously successful, configured Device Access connections only; Home is never redirected. */
    fun shouldRenew(configured: Boolean, previouslyConnected: Boolean, required: Boolean,
        source: IndoorSource): Boolean = configured && previouslyConnected && required && source == IndoorSource.DEVICE_ACCESS

    /** Local preferences only: no network, token decryption, refresh request or sampler changes. */
    fun isRequired(context: Context): Boolean {
        val store = DeviceAccessStore(context)
        return shouldRenew(store.isConfigured(), store.hasCompletedConnection(), store.needsReauthorization(),
            IndoorSourcePreference.selected(context))
    }

    /** Compact status for all tile-widget sizes; cached values remain visible but explicitly stale. */
    const val WIDGET_MESSAGE = "Token expired · Tap to renew"
}
