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

    fun log(name: String, parameters: Map<String, String> = emptyMap()) {
        val safeName = sanitize(name) ?: return
        val bundle = Bundle().apply {
            parameters.entries.take(10).forEach { (key, value) ->
                putString(sanitize(key) ?: return@forEach, sanitize(value) ?: "other")
            }
        }
        analytics?.logEvent(safeName, bundle)
    }

    fun recordNonFatal(code: String, throwable: Throwable) {
        crashlytics?.apply {
            setCustomKey("failure_code", sanitize(code) ?: "unknown")
            recordException(throwable)
        }
    }

    internal fun sanitize(value: String): String? {
        val normalized = value.lowercase()
            .replace(Regex("[^a-z0-9_]+"), "_")
            .trim('_')
            .take(40)
        return normalized.takeIf(String::isNotEmpty)
    }
}
