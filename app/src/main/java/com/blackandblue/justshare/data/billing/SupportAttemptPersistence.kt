package com.blackandblue.justshare.data.billing

import android.content.Context

internal data class SupportAttempt(val user: String, val product: String, val baseline: Set<String>, val receipt: String? = null, val protectedPurchase: String? = null,
    val identifier: String = java.util.UUID.randomUUID().toString(), val startedAt: Long = 0L, val live: Boolean = false)
internal interface SupportAttemptStorage {
    fun read(): SupportAttempt?
    fun readKnown(): Map<String, Set<String>>
    fun write(attempt: SupportAttempt?, known: Map<String, Set<String>>): Boolean
}
/** Attempts and confirmed receipt history are committed together before allowing another checkout. */
internal class SupportAttemptLedger(private val storage: SupportAttemptStorage) {
    var pending: SupportAttempt? = storage.read()
        private set
    private var known = storage.readKnown()
    @Synchronized fun baselineFor(user: String): Set<String> = known[user].orEmpty()
    @Synchronized fun begin(attempt: SupportAttempt): Boolean {
        val prepared = attempt.copy(baseline = attempt.baseline + baselineFor(attempt.user))
        if (pending != null || !storage.write(prepared, known)) return false
        pending = prepared
        return true
    }
    @Synchronized fun bindReceipt(identifier: String, user: String, product: String, receipt: String, protectedPurchase: String,
        purchasedAt: Long, account: String?): Boolean {
        val attempt = pending ?: return false
        if (attempt.identifier != identifier || !attempt.acceptsReceipt(purchasedAt, account) || attempt.user != user || attempt.product != product || receipt.isBlank() || receipt in attempt.baseline ||
            (attempt.receipt != null && attempt.receipt != receipt)) return false
        val updated = attempt.copy(receipt = receipt, protectedPurchase = protectedPurchase)
        if (!storage.write(updated, known)) return false
        pending = updated
        return true
    }
    @Synchronized fun clear(confirmed: Set<String> = emptySet(), identifier: String? = pending?.identifier): Boolean {
        val attempt = pending ?: return identifier == null
        if (attempt.identifier != identifier) return false
        val updated = known + (attempt.user to (baselineFor(attempt.user) + attempt.baseline + confirmed))
        if (!storage.write(null, updated)) return false
        pending = null
        known = updated
        return true
    }
}
internal class PreferencesSupportAttemptStorage(context: Context) : SupportAttemptStorage {
    private val preferences = context.applicationContext.getSharedPreferences("student_support_attempt", Context.MODE_PRIVATE)
    override fun read(): SupportAttempt? {
        check(preferences.all.keys.all { it in setOf("user", "product", "baseline", "receipt", "protectedPurchase", "id", "started", "live") || it.startsWith("history:") })
        if (!preferences.contains("product")) {
            check(preferences.all.keys.all { it.startsWith("history:") }) { "Incomplete checkout record" }
            return null
        }
        val user = checkNotNull(preferences.getString("user", null))
        val product = checkNotNull(preferences.getString("product", null))
        check(user.isNotBlank() && product in checkoutProductIds)
        val baseline = checkNotNull(preferences.getStringSet("baseline", null)).toSet()
        check(baseline.all { it.isNotBlank() })
        val receipt = preferences.getString("receipt", null)
        val protectedPurchase = preferences.getString("protectedPurchase", null)
        check((receipt == null && protectedPurchase == null) || (!receipt.isNullOrBlank() && !protectedPurchase.isNullOrBlank()))
        val identifier = preferences.getString("id", "legacy-checkout")!!
        val startedAt = preferences.getLong("started", Long.MAX_VALUE)
        val live = preferences.getBoolean("live", !preferences.contains("receipt"))
        check(identifier.isNotBlank() && startedAt >= 0)
        if (preferences.contains("id")) check(preferences.contains("started") && preferences.contains("live"))
        return SupportAttempt(user, product, baseline, receipt, protectedPurchase, identifier, startedAt, live)
    }
    override fun readKnown(): Map<String, Set<String>> = preferences.all.keys.filter { it.startsWith("history:") }
        .associate { key ->
            val user = key.removePrefix("history:")
            check(user.isNotBlank())
            val receipts = checkNotNull(preferences.getStringSet(key, null)).toSet()
            check(receipts.all { it.isNotBlank() })
            user to receipts
        }
    override fun write(attempt: SupportAttempt?, known: Map<String, Set<String>>): Boolean {
        val edit = preferences.edit().clear()
        known.forEach { (user, receipts) -> edit.putStringSet("history:$user", receipts) }
        if (attempt != null) edit.putString("user", attempt.user).putString("product", attempt.product)
            .putStringSet("baseline", attempt.baseline)
            .putString("receipt", attempt.receipt).putString("protectedPurchase", attempt.protectedPurchase)
            .putString("id", attempt.identifier).putLong("started", attempt.startedAt).putBoolean("live", attempt.live)
        return edit.commit()
    }
}

internal val checkoutProductIds = StudentSupportProducts.consumableIds + setOf("pro_unlock", "data_pack_10gb")

internal fun SupportAttempt.acceptsReceipt(purchasedAt: Long, account: String?): Boolean =
    if (live) purchasedAt >= startedAt && account == user else account == null || account == user
