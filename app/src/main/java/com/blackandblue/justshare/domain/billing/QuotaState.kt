package com.blackandblue.justshare.domain.billing

/**
 * Snapshot of a device's AlterSend Remote relay quota state, fetched from the backend.
 *
 * All GB values are expressed as [Float] for display convenience.
 * The [remainingGb] field is the single authoritative value for pre-transfer UX checks —
 * but the server is always the source of truth for actual relay authorization.
 */
data class QuotaState(
    val plan: Plan,
    /** Monthly allowance granted by the plan (Free = 2 GB, Pro = 5 GB, server-configurable). */
    val monthlyAllowanceGb: Float,
    /** Purchased data-pack credits that never expire, additive on top of monthly allowance. */
    val packBalanceGb: Float,
    /** GB consumed via relay in the current rolling 30-day window. */
    val usedThisPeriodGb: Float,
    /** Effective remaining balance = (monthlyAllowance - used) + packBalance, floored at 0. */
    val remainingGb: Float,
    /** ISO-8601 string indicating when the monthly window resets. */
    val periodResetAt: String
) {
    val isExhausted: Boolean get() = remainingGb <= 0f

    /** Human-readable quota summary for display in UI, e.g. "2.3 GB of 5 GB remaining". */
    val displaySummary: String
        get() = "%.1f GB of %.1f GB remote data remaining this month"
            .format(remainingGb, monthlyAllowanceGb + packBalanceGb)

    companion object {
        /** Optimistic placeholder used while the real quota is being fetched. */
        fun loading(): QuotaState = QuotaState(
            plan = Plan.FREE,
            monthlyAllowanceGb = 2f,
            packBalanceGb = 0f,
            usedThisPeriodGb = 0f,
            remainingGb = 2f,
            periodResetAt = ""
        )
    }
}

enum class Plan {
    FREE,
    PRO;

    val displayName: String get() = when (this) {
        FREE -> "Free"
        PRO -> "Pro"
    }
}
