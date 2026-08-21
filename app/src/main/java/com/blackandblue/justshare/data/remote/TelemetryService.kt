package com.blackandblue.justshare.data.remote

import android.util.Log
import com.blackandblue.justshare.data.UserPreferencesDataStore
import com.blackandblue.justshare.domain.altersend.ConnectionMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fire-and-forget telemetry sink for AlterSend Remote.
 *
 * Emits **non-identifying, aggregated** metrics to the backend dashboard so relay
 * spend can be tracked against revenue and anomalies (e.g. unnecessary relay
 * fallback, unusual per-device byte totals) can be caught early.
 *
 * Constraints:
 * - Never emits file names, device model, IP addresses, or any personal data
 * - Never throws — telemetry must never affect the user experience
 * - All calls are fully asynchronous (fire-and-forget)
 */
@Singleton
class TelemetryService @Inject constructor(
    private val apiService: QuotaApiService,
    private val dataStore: UserPreferencesDataStore
) {
    companion object {
        private const val TAG = "TelemetryService"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Call after a successful AlterSend Remote transfer completes. */
    fun onTransferCompleted(connectionMode: ConnectionMode, bytesTransferred: Long) {
        emit(
            eventName = when (connectionMode) {
                ConnectionMode.RELAY -> TelemetryEvent.RELAY_SESSION_COMPLETED
                ConnectionMode.CLOUDFLARE_RELAY -> TelemetryEvent.RELAY_SESSION_COMPLETED
                ConnectionMode.DIRECT -> TelemetryEvent.DIRECT_SESSION_COMPLETED
                ConnectionMode.UNKNOWN -> TelemetryEvent.DIRECT_SESSION_COMPLETED
            },
            bytes = bytesTransferred,
            connectionMode = connectionMode.name
        )
    }

    /** Call when a remote transfer session starts (before bytes flow). */
    fun onTransferStarted(connectionMode: ConnectionMode) {
        if (connectionMode == ConnectionMode.RELAY || connectionMode == ConnectionMode.CLOUDFLARE_RELAY) {
            emit(eventName = TelemetryEvent.RELAY_SESSION_STARTED, connectionMode = connectionMode.name)
        }
    }

    /** Call when a relay request is rejected due to quota exhaustion. */
    fun onQuotaExhausted() {
        emit(eventName = TelemetryEvent.QUOTA_EXHAUSTED)
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun emit(
        eventName: String,
        bytes: Long? = null,
        connectionMode: String? = null
    ) {
        scope.launch {
            try {
                val deviceId = dataStore.ensureDeviceId()
                apiService.reportTelemetry(
                    TelemetryEvent(
                        deviceId = deviceId,
                        name = eventName,
                        bytes = bytes,
                        connectionMode = connectionMode
                    )
                )
            } catch (e: Exception) {
                // Swallow all errors — telemetry must never surface to the user
                Log.w(TAG, "Telemetry emit swallowed: $eventName", e)
            }
        }
    }
}
