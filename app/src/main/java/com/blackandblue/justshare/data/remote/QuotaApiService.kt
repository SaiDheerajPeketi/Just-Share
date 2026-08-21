package com.blackandblue.justshare.data.remote

import android.util.Log
import com.blackandblue.justshare.domain.billing.Plan
import com.blackandblue.justshare.domain.billing.QuotaState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Thin HTTP client for the AlterSend Remote quota + telemetry API.
 *
 * Uses [HttpURLConnection] to avoid pulling in a large HTTP library dependency.
 * All calls are dispatched on [Dispatchers.IO].
 *
 * The base URL is injected via Hilt so it can be swapped in tests.
 */
@Singleton
class QuotaApiService @Inject constructor(
    @Named("quotaApiBaseUrl") private val baseUrl: String
) {
    companion object {
        private const val TAG = "QuotaApiService"
        private const val TIMEOUT_MS = 10_000
    }

    // ── Quota ───────────────────────────────────────────────────────────────

    /**
     * Fetch the current quota state for [deviceId].
     * Returns null on any network or parse error (caller should degrade gracefully).
     */
    suspend fun fetchQuota(deviceId: String): QuotaState? = withContext(Dispatchers.IO) {
        try {
            val json = get("$baseUrl/quota/$deviceId", deviceId) ?: return@withContext null
            parseQuotaState(json)
        } catch (e: Exception) {
            Log.e(TAG, "fetchQuota failed", e)
            null
        }
    }

    /**
     * Ask the server whether a relay session is permitted before starting a transfer.
     * Returns true if allowed, false if quota is exhausted.
     * Null means a network error occurred — treat as allowed for UX but log.
     */
    suspend fun checkRelayAllowed(deviceId: String, estimatedBytes: Long): Boolean? =
        withContext(Dispatchers.IO) {
            try {
                val body = JSONObject().apply {
                    put("deviceId", deviceId)
                    put("estimatedBytes", estimatedBytes)
                }.toString()
                val code = post("$baseUrl/quota/relay-check", deviceId, body)
                when (code) {
                    HttpURLConnection.HTTP_OK -> true
                    402 -> false
                    else -> null
                }
            } catch (e: Exception) {
                Log.e(TAG, "checkRelayAllowed failed", e)
                null
            }
        }

    // ── Telemetry ───────────────────────────────────────────────────────────

    /**
     * Fire-and-forget telemetry event. Never throws — swallows all errors silently
     * since telemetry must never affect the user experience.
     */
    suspend fun reportTelemetry(event: TelemetryEvent) = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("deviceId", event.deviceId)
                put("event", event.name)
                event.bytes?.let { put("bytes", it) }
                event.connectionMode?.let { put("connectionMode", it) }
            }.toString()
            post("$baseUrl/telemetry", event.deviceId, body)
        } catch (e: Exception) {
            Log.w(TAG, "reportTelemetry swallowed error", e)
        }
    }

    // ── Internal HTTP helpers ───────────────────────────────────────────────

    private fun get(url: String, deviceId: String): JSONObject? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("X-Device-Id", deviceId)
            setRequestProperty("Accept", "application/json")
        }
        return try {
            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                JSONObject(conn.inputStream.bufferedReader().readText())
            } else null
        } finally {
            conn.disconnect()
        }
    }

    /** Returns the HTTP response code. */
    private fun post(url: String, deviceId: String, body: String): Int {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            doOutput = true
            setRequestProperty("X-Device-Id", deviceId)
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }
        return try {
            OutputStreamWriter(conn.outputStream).use { it.write(body) }
            conn.responseCode
        } finally {
            conn.disconnect()
        }
    }

    /** Returns the HTTP response code AND body JSON. */
    fun postForJson(url: String, deviceId: String, body: String): Pair<Int, JSONObject?> {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            doOutput = true
            setRequestProperty("X-Device-Id", deviceId)
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }
        return try {
            OutputStreamWriter(conn.outputStream).use { it.write(body) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val json = runCatching { JSONObject(stream?.bufferedReader()?.readText() ?: "{}") }.getOrNull()
            Pair(code, json)
        } finally {
            conn.disconnect()
        }
    }

    // ── Parsing ─────────────────────────────────────────────────────────────

    private fun parseQuotaState(json: JSONObject): QuotaState = QuotaState(
        plan = if (json.optString("plan") == "PRO") Plan.PRO else Plan.FREE,
        monthlyAllowanceGb = json.optDouble("monthlyAllowanceGb", 2.0).toFloat(),
        packBalanceGb = json.optDouble("packBalanceGb", 0.0).toFloat(),
        usedThisPeriodGb = json.optDouble("usedThisPeriodGb", 0.0).toFloat(),
        remainingGb = json.optDouble("remainingGb", 0.0).toFloat(),
        periodResetAt = json.optString("periodResetAt", "")
    )
}

/** Telemetry event payload — all fields are non-PII aggregates. */
data class TelemetryEvent(
    val deviceId: String,
    val name: String,
    val bytes: Long? = null,
    val connectionMode: String? = null
) {
    companion object {
        const val RELAY_SESSION_STARTED = "relay_session_started"
        const val RELAY_SESSION_COMPLETED = "relay_session_completed"
        const val DIRECT_SESSION_COMPLETED = "direct_session_completed"
        const val QUOTA_EXHAUSTED = "quota_exhausted"
        const val PACK_PURCHASED = "pack_purchased"
        const val PRO_PURCHASED = "pro_purchased"
    }
}
