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
    @Synchronized fun remember(ticket: Long, user: String, product: String, current: Set<String>): Boolean =
        gate.owns(ticket) && runCatching { ledger?.begin(SupportAttempt(user, product, current)) == true }.getOrDefault(false)
    @Synchronized fun known(user: String): Set<String> = ledger?.baselineFor(user).orEmpty()
    @Synchronized fun clear(confirmed: Set<String> = emptySet()): Boolean =
        runCatching { ledger?.clear(confirmed) == true }.getOrDefault(false)
    @Synchronized fun bindReceipt(user: String, product: String, receipt: String, protectedPurchase: String): Boolean =
        runCatching { ledger?.bindReceipt(user, product, receipt, protectedPurchase) == true }.getOrDefault(false)
    @Synchronized fun finalized(user: String, product: String, receipt: String): Boolean {
        val attempt = pending ?: return false
        if (attempt.user != user || attempt.product != product || attempt.receipt != receipt || receipt in attempt.baseline) return false
        return clear(setOf(receipt))
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
