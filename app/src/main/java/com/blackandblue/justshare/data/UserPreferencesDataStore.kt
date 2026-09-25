package com.blackandblue.justshare.data

import timber.log.Timber

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "jedi_share_prefs")

/**
 * Manages user preferences via Jetpack DataStore.
 *
 * Replaces the previous empty SettingsActivity with persisted key-value storage.
 */
@Singleton
class UserPreferencesDataStore @Inject constructor(
    private val context: Context
) {
    companion object {
        val KEY_DARK_MODE = booleanPreferencesKey("dark_mode")
        val KEY_DEFAULT_TRANSFER_METHOD = stringPreferencesKey("default_transfer_method")
        val KEY_CHUNK_SIZE_KB = intPreferencesKey("chunk_size_kb")
        val KEY_FIRST_LAUNCH = booleanPreferencesKey("first_launch")
        val KEY_ALWAYS_REQUIRE_ENCRYPTION_VERIFICATION =
            booleanPreferencesKey("always_require_encryption_verification")
        /** Persistent anonymous device identity used for relay quota metering. Never changes after first write. */
        val KEY_DEVICE_ID = stringPreferencesKey("device_id")
        private val KEY_VERIFIED_SUPPORT_PRODUCTS = stringSetPreferencesKey("verified_support_products")
    }

    val isFirstLaunch: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_FIRST_LAUNCH] ?: true
    }

    val isDarkModeEnabled: Flow<Boolean?> = context.dataStore.data.map { prefs ->
        prefs[KEY_DARK_MODE]
    }

    val defaultTransferMethod: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_DEFAULT_TRANSFER_METHOD] ?: "wifi"
    }

    val chunkSizeKb: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_CHUNK_SIZE_KB] ?: 8
    }

    val alwaysRequireEncryptionVerification: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_ALWAYS_REQUIRE_ENCRYPTION_VERIFICATION] ?: false
    }

    /** Product IDs only; purchase tokens are never kept on the device for this acknowledgement. */
    val verifiedSupportProducts: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[KEY_VERIFIED_SUPPORT_PRODUCTS] ?: emptySet()
    }

    suspend fun markSupportPurchaseVerified(productId: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_VERIFIED_SUPPORT_PRODUCTS] =
                (prefs[KEY_VERIFIED_SUPPORT_PRODUCTS] ?: emptySet()) + productId
        }
    }

    /**
     * Persistent anonymous device ID used as the billing/quota identity for AlterSend Remote.
     * Generated once on first access via [UUID.randomUUID] and stored permanently.
     * No login or personal information is required or stored.
     */
    val deviceId: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_DEVICE_ID] ?: generateAndPersistDeviceId()
    }

    /**
     * One-shot suspend read of the device ID — guaranteed to return a non-blank UUID.
     * Prefer this over [deviceId].first() in call sites that need the ID before a network call.
     */
    suspend fun ensureDeviceId(): String {
        val existing = context.dataStore.data.first()[KEY_DEVICE_ID]
        if (!existing.isNullOrBlank()) return existing
        return generateAndPersistDeviceId()
    }

    private suspend fun generateAndPersistDeviceId(): String {
        val newId = UUID.randomUUID().toString()
        Timber.d("UserPreferencesDataStore - generated new device ID")
        context.dataStore.edit { prefs -> prefs[KEY_DEVICE_ID] = newId }
        return newId
    }

    suspend fun setDarkMode(enabled: Boolean) {
        Timber.d("UserPreferencesDataStore - setDarkMode called")
        context.dataStore.edit { prefs -> prefs[KEY_DARK_MODE] = enabled }
    }

    suspend fun setDefaultTransferMethod(method: String) {
        Timber.d("UserPreferencesDataStore - setDefaultTransferMethod called")
        context.dataStore.edit { prefs -> prefs[KEY_DEFAULT_TRANSFER_METHOD] = method }
    }

    suspend fun setChunkSizeKb(sizeKb: Int) {
        Timber.d("UserPreferencesDataStore - setChunkSizeKb called")
        context.dataStore.edit { prefs -> prefs[KEY_CHUNK_SIZE_KB] = sizeKb }
    }

    suspend fun setAlwaysRequireEncryptionVerification(enabled: Boolean) {
        Timber.d("UserPreferencesDataStore - setAlwaysRequireEncryptionVerification called")
        context.dataStore.edit { prefs ->
            prefs[KEY_ALWAYS_REQUIRE_ENCRYPTION_VERIFICATION] = enabled
        }
    }

    suspend fun markFirstLaunchComplete() {
        context.dataStore.edit { prefs -> prefs[KEY_FIRST_LAUNCH] = false }
    }
}
