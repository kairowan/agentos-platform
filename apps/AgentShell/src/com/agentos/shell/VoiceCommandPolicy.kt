package com.agentos.shell

import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

internal object VoiceCommandPolicy {
    fun sanitize(raw: String?): String? = raw
        ?.trim()
        ?.take(MAX_COMMAND_LENGTH)
        ?.takeIf(String::isNotEmpty)

    private const val MAX_COMMAND_LENGTH = 8_000
}

internal object VoiceCommandInbox {
    private val pending = AtomicReference<Pair<String, String>?>(null)

    // ponytail: only the newest voice command is retained; use encrypted queued
    // storage if concurrent voice turns are introduced later.
    fun offer(command: String): String = UUID.randomUUID().toString().also { token ->
        pending.set(token to command)
    }

    fun take(token: String?): String? {
        val current = pending.get() ?: return null
        if (token != current.first || !pending.compareAndSet(current, null)) return null
        return current.second
    }
}

internal object VoiceInterruptInbox {
    private val pending = AtomicReference<String?>(null)

    fun offer(): String = UUID.randomUUID().toString().also(pending::set)

    fun take(token: String?): Boolean {
        val current = pending.get() ?: return false
        return token == current && pending.compareAndSet(current, null)
    }
}
