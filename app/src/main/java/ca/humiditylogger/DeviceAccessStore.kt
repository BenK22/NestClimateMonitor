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

class DeviceAccessStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    data class Configuration(
        val projectId: String,
        val clientId: String,
        val hasClientSecret: Boolean,
    )

    data class AccessToken(val value: String, val expiresAtMs: Long)

    fun configuration(): Configuration = Configuration(
        projectId = prefs.getString(KEY_PROJECT_ID, "").orEmpty(),
        clientId = prefs.getString(KEY_CLIENT_ID, "").orEmpty(),
        hasClientSecret = prefs.contains(KEY_CLIENT_SECRET),
    )

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

    fun saveOAuthCredentials(clientId: String, clientSecret: String?) {
        val normalized = clientId.trim()
        require(normalized.endsWith(".apps.googleusercontent.com")) {
            "Enter a valid OAuth Client ID."
        }
        val clientChanged = configuration().clientId != normalized
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

    fun clientSecret(): String = decryptRequired(KEY_CLIENT_SECRET, "OAuth Client Secret")

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

    fun accessToken(): AccessToken? {
        val encrypted = prefs.getString(KEY_ACCESS_TOKEN, null) ?: return null
        return runCatching {
            AccessToken(decrypt(encrypted), prefs.getLong(KEY_ACCESS_TOKEN_EXPIRY, 0L))
        }.getOrNull()
    }

    fun refreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)?.let {
        runCatching { decrypt(it) }.getOrNull()
    }

    fun isConfigured(): Boolean {
        val config = configuration()
        return config.projectId.isNotBlank() && config.clientId.isNotBlank() && config.hasClientSecret
    }

    fun isConnected(): Boolean = !refreshToken().isNullOrBlank()

    fun selectedDeviceId(): String? = prefs.getString(KEY_DEVICE_ID, null)?.takeIf { it.isNotBlank() }

    fun setSelectedDeviceId(deviceId: String?) {
        prefs.edit().apply {
            if (deviceId.isNullOrBlank()) remove(KEY_DEVICE_ID) else putString(KEY_DEVICE_ID, deviceId)
        }.apply()
    }

    fun clearConnection() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_ACCESS_TOKEN_EXPIRY)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_DEVICE_ID)
            .apply()
    }

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
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
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
        const val KEY_ALIAS = "home_climate_device_access"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH_BYTES = 12
        const val GCM_TAG_LENGTH_BITS = 128
    }
}
