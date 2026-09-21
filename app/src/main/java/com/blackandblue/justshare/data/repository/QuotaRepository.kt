package com.blackandblue.justshare.data.repository

import android.util.Log
import com.blackandblue.justshare.data.UserPreferencesDataStore
import com.blackandblue.justshare.data.remote.QuotaApiService
import com.blackandblue.justshare.data.remote.RelayCredentials
import com.blackandblue.justshare.domain.billing.QuotaState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository that owns the authoritative [QuotaState] observable for the app.
 *
 * Consumers should observe [quotaState] and call [refresh] to trigger a
 * network round-trip. Refresh is automatically triggered on:
 * - App foreground (triggered from [MainActivity])
 * - After any confirmed in-app purchase (triggered by [PurchaseRepository])
 * - Before any remote AlterSend transfer begins
 *
 * Client-side quota checks (via [quotaState]) are for UX responsiveness only.
 * The relay server is always the authoritative gate — never trust the client alone.
 */
@Singleton
class QuotaRepository @Inject constructor(
    private val apiService: QuotaApiService,
    private val dataStore: UserPreferencesDataStore
) {
    companion object {
        private const val TAG = "QuotaRepository"
    }

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _quotaState = MutableStateFlow(QuotaState.loading())
    val quotaState: StateFlow<QuotaState> = _quotaState.asStateFlow()

    /**
     * Refresh quota from the backend. Safe to call on any thread — dispatches to IO internally.
     * Silently degrades on network errors (keeps the last known state).
     */
    fun refresh() {
        repositoryScope.launch {
            try {
                val deviceId = dataStore.ensureDeviceId()
                val fresh = apiService.fetchQuota(deviceId)
                if (fresh != null) {
                    _quotaState.value = fresh
                    Log.d(TAG, "Quota refreshed: remaining=${fresh.remainingGb}GB plan=${fresh.plan}")
                } else {
                    Log.w(TAG, "Quota refresh returned null — keeping previous state")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Quota refresh failed", e)
            }
        }
    }

    /**
     * Check with the server whether a relay session is permitted for [estimatedBytes].
     *
     * Returns:
     * - [RelayCheckResult.Allowed] — server gave a green light
     * - [RelayCheckResult.Exhausted] — quota is used up; show the upsell dialog
     * - [RelayCheckResult.NetworkError] — couldn't reach the server; block until quota is verified
     */
    suspend fun checkRelayAllowed(estimatedBytes: Long): RelayCheckResult {
        return try {
            val deviceId = dataStore.ensureDeviceId()
            when (apiService.checkRelayAllowed(deviceId, estimatedBytes)) {
                true -> RelayCheckResult.Allowed
                false -> {
                    // Refresh so the UI reflects exhausted state immediately
                    refresh()
                    RelayCheckResult.Exhausted
                }
                null -> RelayCheckResult.NetworkError
            }
        } catch (e: Exception) {
            Log.e(TAG, "checkRelayAllowed exception", e)
            RelayCheckResult.NetworkError
        }
    }

    suspend fun createRelayCredentials(sessionId: String, estimatedBytes: Long): RelayCredentials? = try {
        val deviceId = dataStore.ensureDeviceId()
        apiService.createRelayCredentials(deviceId, sessionId, estimatedBytes)
    } catch (e: Exception) {
        Log.e(TAG, "createRelayCredentials exception", e)
        null
    }
}

sealed class RelayCheckResult {
    object Allowed : RelayCheckResult()
    object Exhausted : RelayCheckResult()
    object NetworkError : RelayCheckResult()
}
