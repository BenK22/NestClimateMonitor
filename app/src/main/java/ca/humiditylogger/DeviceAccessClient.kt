package ca.humiditylogger

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Read-only Nest SDM client with user-supplied Web OAuth credentials stored on the phone.
 *
 * Suspending network entry points dispatch blocking HTTP to IO and use finite socket timeouts.
 * Access tokens refresh with a one-minute safety margin; rejected/expired refresh grants require
 * user reconnection. Responses are snapshots, not a Pub/Sub stream. Do not log OAuth URLs,
 * request bodies or bearer tokens.
 */
class DeviceAccessClient(context: Context) {
    private val store = DeviceAccessStore(context.applicationContext)

    /**
     * Starts a new consent attempt, replacing the stored pending state, and returns its browser URL.
     *
     * @throws IllegalArgumentException if the required configuration has not been saved.
     */
    fun authorizationUrl(): String {
        val config = store.configuration()
        require(store.isConfigured()) { "Save the Device Access credentials first." }
        val state = newOAuthState()
        store.savePendingOAuthState(state)
        return "https://nestservices.google.com/partnerconnections/${encode(config.projectId)}/auth" +
            "?redirect_uri=${encode(REDIRECT_URI)}" +
            "&access_type=offline&prompt=consent" +
            "&client_id=${encode(config.clientId)}" +
            "&response_type=code&scope=${encode(SDM_SCOPE)}" +
            "&state=${encode(state)}"
    }

    /**
     * Validates a full Google redirect URL, exchanges its code, saves tokens and discovers devices.
     *
     * Despite the legacy parameter name, bare codes are rejected: the URL must match pending
     * OAuth state. State is consumed before the exchange, so a failed exchange needs new consent.
     * Discovery does not persist climate readings or reset the sampling schedule.
     *
     * @throws IllegalArgumentException if the redirect host/path or OAuth state is invalid.
     * @throws IOException if Google returns a non-success HTTP/token response.
     */
    suspend fun link(authorizationCodeOrUrl: String): List<Reading> = withContext(Dispatchers.IO) {
        val expectedState = store.pendingOAuthState()
            ?: error("Authorization request expired. Open Google authorization again.")
        val code = extractAuthorizationResponse(authorizationCodeOrUrl, expectedState)
        store.clearPendingOAuthState()
        val tokens = tokenRequest(
            mapOf(
                "code" to code,
                "grant_type" to "authorization_code",
                "redirect_uri" to REDIRECT_URI,
            )
        )
        val refreshToken = tokens.optString("refresh_token").takeIf { it.isNotBlank() }
            ?: error("Google did not return a refresh token. Re-authorize with consent enabled.")
        saveTokenResponse(tokens, refreshToken)
        thermostats()
    }

    /**
     * Returns supported thermostat snapshots without saving them; refreshes the access token if needed.
     *
     * @throws IllegalArgumentException if no locally usable refresh token is present.
     * @throws IOException if an OAuth or SDM HTTP request fails.
     */
    suspend fun thermostats(): List<Reading> = withContext(Dispatchers.IO) {
        val config = store.configuration()
        require(store.isConnected()) { "Nest Device Access is not connected." }
        val accessToken = validAccessToken()
        val response = request(
            method = "GET",
            url = "$SDM_API/enterprises/${encodePath(config.projectId)}/devices",
            bearerToken = accessToken,
        )
        DeviceAccessParser.thermostats(response)
    }

    private fun validAccessToken(): String {
        val current = store.accessToken()
        if (current != null && current.expiresAtMs > System.currentTimeMillis() + TOKEN_SAFETY_MS) {
            return current.value
        }
        val refreshToken = store.refreshToken() ?: error("Nest authorization has expired. Connect again.")
        val response = tokenRequest(
            mapOf(
                "refresh_token" to refreshToken,
                "grant_type" to "refresh_token",
            )
        )
        // Refresh responses need not include a replacement refresh token; retain the saved grant.
        saveTokenResponse(response, null)
        return store.accessToken()?.value ?: error("Google did not return an access token.")
    }

    private fun tokenRequest(values: Map<String, String>): JSONObject {
        val config = store.configuration()
        val form = values + mapOf(
            "client_id" to config.clientId,
            "client_secret" to store.clientSecret(),
        )
        return JSONObject(
            request(
                method = "POST",
                url = TOKEN_ENDPOINT,
                body = form.entries.joinToString("&") { (key, value) -> "${encode(key)}=${encode(value)}" },
                contentType = "application/x-www-form-urlencoded",
            )
        )
    }

    private fun saveTokenResponse(response: JSONObject, refreshToken: String?) {
        val accessToken = response.optString("access_token").takeIf { it.isNotBlank() }
            ?: throw IOException(oauthError(response))
        val expiresInSeconds = response.optLong("expires_in", 3600L).coerceAtLeast(60L)
        store.saveTokens(
            accessToken,
            System.currentTimeMillis() + expiresInSeconds * 1000L,
            refreshToken,
        )
    }

    private fun request(
        method: String,
        url: String,
        body: String? = null,
        contentType: String? = null,
        bearerToken: String? = null,
    ): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = method
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")
            contentType?.let { connection.setRequestProperty("Content-Type", it) }
            bearerToken?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val response = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                throw IOException(apiError(status, response))
            }
            response
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        /** Creates 256 bits of random, URL-safe state to bind the redirect to this consent attempt. */
        internal fun newOAuthState(): String {
            val bytes = ByteArray(32).also(SecureRandom()::nextBytes)
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }

        /** Checks the HTTPS Google redirect host/path and code presence, but not request state. */
        internal fun isExpectedRedirectUrl(value: String): Boolean {
            val uri = runCatching { URI(value.trim()) }.getOrNull() ?: return false
            if (!uri.scheme.equals("https", ignoreCase = true)) return false
            if (!uri.host.equals("www.google.com", ignoreCase = true)) return false
            if (uri.path !in listOf(null, "", "/")) return false
            return runCatching { extractAuthorizationCode(value) }.isSuccess
        }

        /** Extracts a code only; never use this parser alone to authorize a connection. */
        internal fun extractAuthorizationCode(value: String): String {
            val trimmed = value.trim()
            require(trimmed.isNotBlank()) { "Paste the authorization code or redirected URL." }
            if (!trimmed.contains("://") && !trimmed.contains("?")) return trimmed
            val query = runCatching { URI(trimmed).rawQuery }.getOrNull()
                ?: trimmed.substringAfter('?', "")
            val code = query.split('&').mapNotNull { part ->
                val pieces = part.split('=', limit = 2)
                if (pieces.firstOrNull() == "code") pieces.getOrNull(1) else null
            }.firstOrNull()
            return code?.let { URLDecoder.decode(it, Charsets.UTF_8.name()) }
                ?.takeIf { it.isNotBlank() }
                ?: error("The pasted value does not contain an authorization code.")
        }

        /** Validates redirect host/path and state before returning a nonblank decoded authorization code. */
        internal fun extractAuthorizationResponse(value: String, expectedState: String): String {
            require(expectedState.isNotBlank()) { "Authorization request expired. Open Google authorization again." }
            require(isExpectedRedirectUrl(value)) {
                "Paste the complete https://www.google.com/?code=… address from this authorization attempt."
            }
            val query = URI(value.trim()).rawQuery.orEmpty()
            val parameters = query.split('&').mapNotNull { part ->
                val pieces = part.split('=', limit = 2)
                pieces.firstOrNull()?.let { key ->
                    URLDecoder.decode(key, Charsets.UTF_8.name()) to
                        URLDecoder.decode(pieces.getOrElse(1) { "" }, Charsets.UTF_8.name())
                }
            }.toMap()
            val returnedState = parameters["state"].orEmpty()
            require(
                MessageDigest.isEqual(
                    expectedState.toByteArray(Charsets.UTF_8),
                    returnedState.toByteArray(Charsets.UTF_8),
                )
            ) { "This Google URL belongs to a different or expired authorization attempt." }
            return parameters["code"]?.takeIf { it.isNotBlank() }
                ?: error("The pasted Google URL does not contain an authorization code.")
        }

        private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
        private fun encodePath(value: String): String = encode(value).replace("+", "%20")
        private fun oauthError(json: JSONObject): String = json.optString("error_description")
            .takeIf { it.isNotBlank() } ?: json.optString("error", "OAuth token request failed.")
        /** Reports HTTP status and a structured Google error instead of echoing the response body. */
        internal fun apiError(status: Int, body: String): String {
            val message = runCatching {
                val json = JSONObject(body)
                json.optString("error_description").takeIf { it.isNotBlank() }
                    ?: json.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
                    ?: json.optString("error").takeIf { it.isNotBlank() }
            }.getOrNull()
            return "Google request failed ($status)${message?.let { ": $it" }.orEmpty()}"
        }

        /** Browser landing URI registered on the user's Web OAuth client; copied back manually. */
        const val REDIRECT_URI = "https://www.google.com"
        private const val SDM_SCOPE = "https://www.googleapis.com/auth/sdm.service"
        private const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
        private const val SDM_API = "https://smartdevicemanagement.googleapis.com/v1"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val TOKEN_SAFETY_MS = 60_000L
    }
}
