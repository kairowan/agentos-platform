package com.agentos.capability.core

import java.util.UUID

/** One outstanding, expiring approval. The trusted service owns the immutable payload. */
class PendingApproval<T>(
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
    private val lifetimeMillis: Long = 60_000,
) {
    private var pending: Triple<String, Long, T>? = null

    init { require(lifetimeMillis > 0) }

    @Synchronized
    fun issue(value: T): String = UUID.randomUUID().toString().also {
        pending = Triple(it, clock(), value)
    }

    @Synchronized
    fun take(token: String): T? {
        val current = pending ?: return null
        val elapsed = clock() - current.second
        if (elapsed < 0 || elapsed >= lifetimeMillis) {
            pending = null
            return null
        }
        if (token != current.first) return null
        pending = null // Consume before invoking any side effect, including one that throws.
        return current.third
    }

    @Synchronized
    fun clear() { pending = null }
}
