package com.blackandblue.justshare.domain.transfer

/** Keeps queued progress from a stopped connection out of its replacement. */
internal class TransferProgressSession {
    private var nextId = 0L
    private var activeId: Long? = null

    @Synchronized
    fun begin(clearReplay: () -> Unit): Long {
        nextId++
        activeId = nextId
        clearReplay()
        return nextId
    }

    @Synchronized
    fun clear(clearReplay: () -> Unit) {
        activeId = null
        clearReplay()
    }

    @Synchronized
    fun withCurrent(id: Long, action: () -> Unit): Boolean {
        if (activeId != id) return false
        action()
        return true
    }

    @Synchronized
    fun isCurrent(id: Long): Boolean = activeId == id
}
