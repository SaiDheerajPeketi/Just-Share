package com.blackandblue.justshare

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics

/** Operational telemetry only. Never pass file names, paths, codes, peers, or transfer content. */
object JustShareTelemetry {
    private var analytics: FirebaseAnalytics? = null
    private var crashlytics: FirebaseCrashlytics? = null

    fun initialize(context: Context) {
        if (!BuildConfig.FIREBASE_CONFIGURED) return
        analytics = FirebaseAnalytics.getInstance(context).also {
            it.setAnalyticsCollectionEnabled(true)
        }
        crashlytics = FirebaseCrashlytics.getInstance().also {
            it.setCrashlyticsCollectionEnabled(true)
        }
    }

    fun log(name: String) {
        val safeName = sanitize(name) ?: return
        analytics?.logEvent(safeName, Bundle.EMPTY)
    }

    fun recordNonFatal(code: String, throwable: Throwable) {
        val failure = sanitizedFailure(code, throwable)
        crashlytics?.apply {
            setCustomKey("failure_code", failure.code)
            setCustomKey("failure_type", failure.type)
            recordException(failure.exception)
        }
    }

    internal fun sanitizedFailure(code: String, throwable: Throwable): SanitizedFailure {
        val safeCode = sanitize(code) ?: "unknown"
        val safeType = sanitize(throwable::class.simpleName ?: "throwable") ?: "throwable"
        return SanitizedFailure(
            code = safeCode,
            type = safeType,
            exception = TelemetryFailureException("$safeCode:$safeType"),
        )
    }

    internal fun sanitize(value: String): String? {
        val normalized = value.lowercase()
            .replace(Regex("[^a-z0-9_]+"), "_")
            .trim('_')
            .take(40)
        return normalized.takeIf(String::isNotEmpty)
    }

    internal data class SanitizedFailure(
        val code: String,
        val type: String,
        val exception: Throwable,
    )

    private class TelemetryFailureException(message: String) : RuntimeException(message)
}
