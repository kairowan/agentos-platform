package com.agentos.shell

internal class UtteranceGate {
    private var sequence = 0L
    private var current: String? = null

    @Synchronized fun start(): String = "agentos-${++sequence}".also { current = it }
    @Synchronized fun finish(id: String?): Boolean {
        if (id == null || id != current) return false
        current = null
        return true
    }
    @Synchronized fun cancel(): Boolean = (current != null).also { current = null }
}
