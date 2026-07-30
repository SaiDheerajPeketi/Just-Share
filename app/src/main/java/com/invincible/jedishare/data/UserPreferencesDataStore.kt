package com.invincible.jedishare.data

import timber.log.Timber

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
