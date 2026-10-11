package com.blackandblue.justshare.data.billing

internal class CheckoutGuard {
    private var sequence = 0L
    private var active: Long? = null
    val busy: Boolean get() = synchronized(this) { active != null }
    @Synchronized fun begin(): Long? {
        if (active != null) return null
        return (++sequence).also { active = it }
    }
    @Synchronized fun owns(ticket: Long): Boolean = active == ticket
    @Synchronized fun finish(ticket: Long) { if (active == ticket) active = null }
}

/** Shared process gate backed by a durable unresolved support attempt. */
internal open class SupportCheckoutState {
    private var ledger: SupportAttemptLedger? = null
    val gate = CheckoutGuard()
    @Synchronized fun bind(storage: SupportAttemptStorage): Boolean {
        if (ledger != null) return true
        ledger = runCatching { SupportAttemptLedger(storage) }.getOrNull()
        return ledger != null
    }
    val pending: SupportAttempt? get() = synchronized(this) { ledger?.pending }
    val ready: Boolean get() = synchronized(this) { ledger != null }
    @Synchronized fun begin(): Long? = if (ledger == null || ledger?.pending != null) null else gate.begin()
    @Synchronized fun remember(ticket: Long, attempt: SupportAttempt): Boolean =
        gate.owns(ticket) && runCatching { ledger?.begin(attempt) == true }.getOrDefault(false)
    @Synchronized fun known(user: String): Set<String> = ledger?.baselineFor(user).orEmpty()
    @Synchronized fun clear(confirmed: Set<String> = emptySet(), identifier: String? = pending?.identifier): Boolean =
        runCatching { ledger?.clear(confirmed, identifier) == true }.getOrDefault(false)
    @Synchronized fun bindReceipt(identifier: String, user: String, product: String, receipt: String, protectedPurchase: String,
        purchasedAt: Long, account: String?): Boolean =
        runCatching { ledger?.bindReceipt(identifier, user, product, receipt, protectedPurchase, purchasedAt, account) == true }.getOrDefault(false)
    @Synchronized fun finalized(identifier: String, user: String, product: String, receipt: String): Boolean {
        val attempt = pending ?: return false
        if (attempt.identifier != identifier || attempt.user != user || attempt.product != product || attempt.receipt != receipt || receipt in attempt.baseline) return false
        return clear(setOf(receipt), identifier)
    }
    fun onMain(action: () -> Unit) {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) action()
        else android.os.Handler(android.os.Looper.getMainLooper()).post { action() }
    }
}

internal object SupportCheckout : SupportCheckoutState() {
    fun attach(context: android.content.Context): Boolean = ready || runCatching {
        bind(PreferencesSupportAttemptStorage(context))
    }.getOrDefault(false)
}
