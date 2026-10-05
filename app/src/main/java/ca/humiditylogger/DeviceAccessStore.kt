package ca.humiditylogger

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Persists Device Access configuration and encrypts secret/token values with Android Keystore.
 *
 * Project/client identifiers, selection and pending state are ordinary private preferences.
 * Secrets use AES-GCM with a fresh IV per encryption; the key is not stored in preferences.
 * Backup/transfer exclusions are enforced separately by the manifest and XML rules. Local
 * connectivity means a decryptable refresh token exists, not that Google still accepts it.
 */
class DeviceAccessStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Saved non-secret identifiers and secret-presence flag; does not expose decrypted credentials. */
    data class Configuration(
        val projectId: String,
        val clientId: String,
        val hasClientSecret: Boolean,
    )

    /** Decrypted bearer token and expiration in Unix epoch milliseconds; never log this object. */
    data class AccessToken(val value: String, val expiresAtMs: Long)

    /** Returns saved identifiers; presence of ciphertext does not prove it can be decrypted. */
    fun configuration(): Configuration = Configuration(
        projectId = prefs.getString(KEY_PROJECT_ID, "").orEmpty(),
        clientId = prefs.getString(KEY_CLIENT_ID, "").orEmpty(),
        hasClientSecret = prefs.contains(KEY_CLIENT_SECRET),
    )

    /**
     * Saves combined configuration, clearing tokens/selection on identity or secret changes.
     * A blank secret preserves the existing secret only if the project/client identity is unchanged.
     * Prefer [saveProjectId] and [saveOAuthCredentials] for the separate UI setup steps.
     */
    fun saveConfiguration(projectId: String, clientId: String, clientSecret: String?) {
        require(projectId.isNotBlank()) { "Device Access Project ID is required." }
        require(clientId.endsWith(".apps.googleusercontent.com")) { "Enter a valid OAuth Client ID." }
        val normalizedProjectId = projectId.trim()
        val normalizedClientId = clientId.trim()
        val previous = configuration()
        val identityChanged = previous.projectId != normalizedProjectId ||
            previous.clientId != normalizedClientId
        prefs.edit()
            .putString(KEY_PROJECT_ID, normalizedProjectId)
            .putString(KEY_CLIENT_ID, normalizedClientId)
            .apply {
                if (identityChanged || !clientSecret.isNullOrBlank()) {
                    remove(KEY_ACCESS_TOKEN)
                    remove(KEY_ACCESS_TOKEN_EXPIRY)
                    remove(KEY_REFRESH_TOKEN)
                    remove(KEY_DEVICE_ID)
                }
                if (identityChanged && clientSecret.isNullOrBlank()) {
                    remove(KEY_CLIENT_SECRET)
                }
                if (!clientSecret.isNullOrBlank()) {
                    putString(KEY_CLIENT_SECRET, encrypt(clientSecret.trim()))
                }
            }
            .apply()
    }

    /** Saves a nonblank Device Access project ID; changes invalidate tokens, selection and state. */
    fun saveProjectId(projectId: String) {
        val normalized = projectId.trim()
        require(normalized.isNotBlank()) { "Device Access Project ID is required." }
        val changed = configuration().projectId != normalized
        prefs.edit()
            .putString(KEY_PROJECT_ID, normalized)
            .apply {
                if (changed) clearConnectionValues()
            }
            .apply()
    }

    /**
     * Saves Web OAuth credentials and invalidates the connection when either changes.
     * A blank secret keeps a previously saved one only for the same client ID.
     *
     * @throws IllegalArgumentException if the client ID or required secret is invalid/missing.
     */
    fun saveOAuthCredentials(clientId: String, clientSecret: String?) {
        val normalized = clientId.trim()
        require(normalized.endsWith(".apps.googleusercontent.com")) {
            "Enter a valid OAuth Client ID."
        }
        val previous = configuration()
        val clientChanged = previous.clientId != normalized
        require(!clientSecret.isNullOrBlank() || previous.hasClientSecret && !clientChanged) {
            "OAuth Client Secret is required."
        }
        prefs.edit()
            .putString(KEY_CLIENT_ID, normalized)
            .apply {
                if (clientChanged || !clientSecret.isNullOrBlank()) clearConnectionValues()
                if (clientChanged && clientSecret.isNullOrBlank()) remove(KEY_CLIENT_SECRET)
                if (!clientSecret.isNullOrBlank()) {
                    putString(KEY_CLIENT_SECRET, encrypt(clientSecret.trim()))
                }
            }
            .apply()
    }

    /** Decrypts the required client secret; absence or an unavailable key is an error, not blank text. */
    fun clientSecret(): String = decryptRequired(KEY_CLIENT_SECRET, "OAuth Client Secret")

    /** Encrypts tokens; a null/blank refresh token preserves the current refresh grant. */
    fun saveTokens(accessToken: String, expiresAtMs: Long, refreshToken: String? = null) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, encrypt(accessToken))
            .putLong(KEY_ACCESS_TOKEN_EXPIRY, expiresAtMs)
            .apply {
                if (!refreshToken.isNullOrBlank()) {
                    putString(KEY_REFRESH_TOKEN, encrypt(refreshToken))
                }
            }
            .apply()
    }

    /** Replaces the pending consent nonce and records its creation time in epoch milliseconds. */
    fun savePendingOAuthState(state: String, createdAtMs: Long = System.currentTimeMillis()) {
        require(state.isNotBlank()) { "OAuth state cannot be blank." }
        prefs.edit()
            .putString(KEY_PENDING_OAUTH_STATE, state)
            .putLong(KEY_PENDING_OAUTH_STATE_CREATED, createdAtMs)
            .apply()
    }

    /** Returns the nonce for up to ten minutes; expired state or clock rollback clears it. */
    fun pendingOAuthState(nowMs: Long = System.currentTimeMillis()): String? {
        val state = prefs.getString(KEY_PENDING_OAUTH_STATE, null)?.takeIf { it.isNotBlank() }
            ?: return null
        val createdAt = prefs.getLong(KEY_PENDING_OAUTH_STATE_CREATED, 0L)
        if (createdAt <= 0L || nowMs < createdAt || nowMs - createdAt > OAUTH_STATE_LIFETIME_MS) {
            clearPendingOAuthState()
            return null
        }
        return state
    }

    /** Consumes the local consent attempt without modifying saved tokens. */
    fun clearPendingOAuthState() {
        prefs.edit()
            .remove(KEY_PENDING_OAUTH_STATE)
            .remove(KEY_PENDING_OAUTH_STATE_CREATED)
            .apply()
    }

    /** Returns a decryptable token, possibly expired; null signals missing/unreadable ciphertext. */
    fun accessToken(): AccessToken? {
        val encrypted = prefs.getString(KEY_ACCESS_TOKEN, null) ?: return null
        return runCatching {
            AccessToken(decrypt(encrypted), prefs.getLong(KEY_ACCESS_TOKEN_EXPIRY, 0L))
        }.getOrNull()
    }

    /** Returns the decryptable refresh grant, or null if storage/key access fails. */
    fun refreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)?.let {
        runCatching { decrypt(it) }.getOrNull()
    }

    /** Checks saved identifiers and secret presence, not validity against Google's services. */
    fun isConfigured(): Boolean {
        val config = configuration()
        return config.projectId.isNotBlank() && config.clientId.isNotBlank() && config.hasClientSecret
    }

    /** Local grant presence only; Google can still reject a revoked or expired authorization. */
    fun isConnected(): Boolean = !refreshToken().isNullOrBlank()

    /** Selected full SDM resource name, not its display name or trailing identifier alone. */
    fun selectedDeviceId(): String? = prefs.getString(KEY_DEVICE_ID, null)?.takeIf { it.isNotBlank() }

    /** Saves the chosen resource name; null/blank clears selection without deleting history. */
    fun setSelectedDeviceId(deviceId: String?) {
        prefs.edit().apply {
            if (deviceId.isNullOrBlank()) remove(KEY_DEVICE_ID) else putString(KEY_DEVICE_ID, deviceId)
        }.apply()
    }

    /** Erases local grants, selection and pending state, retaining setup credentials and history. */
    fun clearConnection() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_ACCESS_TOKEN_EXPIRY)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_DEVICE_ID)
            .remove(KEY_PENDING_OAUTH_STATE)
            .remove(KEY_PENDING_OAUTH_STATE_CREATED)
            .apply()
    }

    /** Erases all Device Access preferences and attempts key deletion; leaves climate history intact. */
    fun clearAll() {
        prefs.edit().clear().apply()
        runCatching { keyStore().deleteEntry(KEY_ALIAS) }
    }

    private fun decryptRequired(key: String, label: String): String {
        val encrypted = prefs.getString(key, null) ?: error("$label is not configured.")
        return decrypt(encrypted)
    }

    private fun android.content.SharedPreferences.Editor.clearConnectionValues() {
        remove(KEY_ACCESS_TOKEN)
        remove(KEY_ACCESS_TOKEN_EXPIRY)
        remove(KEY_REFRESH_TOKEN)
        remove(KEY_DEVICE_ID)
        remove(KEY_PENDING_OAUTH_STATE)
        remove(KEY_PENDING_OAUTH_STATE_CREATED)
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        // Persist IV + authenticated ciphertext; decrypt must use this exact byte layout.
        return Base64.encodeToString(cipher.iv + ciphertext, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val combined = Base64.decode(encoded, Base64.NO_WRAP)
        require(combined.size > IV_LENGTH_BYTES) { "Encrypted credential is invalid." }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, combined.copyOfRange(0, IV_LENGTH_BYTES)),
        )
        return String(cipher.doFinal(combined.copyOfRange(IV_LENGTH_BYTES, combined.size)), Charsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        val store = keyStore()
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
            generateKey()
        }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private companion object {
        const val PREFS = "device_access"
        const val KEY_PROJECT_ID = "project_id"
        const val KEY_CLIENT_ID = "client_id"
        const val KEY_CLIENT_SECRET = "client_secret_encrypted"
        const val KEY_ACCESS_TOKEN = "access_token_encrypted"
        const val KEY_ACCESS_TOKEN_EXPIRY = "access_token_expiry"
        const val KEY_REFRESH_TOKEN = "refresh_token_encrypted"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_PENDING_OAUTH_STATE = "pending_oauth_state"
        const val KEY_PENDING_OAUTH_STATE_CREATED = "pending_oauth_state_created"
        const val KEY_ALIAS = "home_climate_device_access"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH_BYTES = 12
        const val GCM_TAG_LENGTH_BITS = 128
        const val OAUTH_STATE_LIFETIME_MS = 10L * 60L * 1000L
    }
}
