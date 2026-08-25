package com.agentos.capability.service

import com.agentos.capability.api.AgentNotificationEvent

internal object NotificationEventPolicy {
    private const val MAX_PACKAGE_LENGTH = 255
    private const val MAX_SENDER_LENGTH = 200
    private const val MAX_TEXT_LENGTH = 1_000

    fun create(
        packageName: String,
        sender: CharSequence?,
        text: CharSequence?,
        postedAtMillis: Long,
        isMessage: Boolean,
        isOngoing: Boolean,
        isGroupSummary: Boolean,
    ): AgentNotificationEvent? {
        if (!isMessage || isOngoing || isGroupSummary) return null
        val safePackage = packageName.trim().take(MAX_PACKAGE_LENGTH)
        val safeSender = sender.normalized(MAX_SENDER_LENGTH)
        val safeText = text.normalized(MAX_TEXT_LENGTH)
        if (safePackage.isEmpty() || safeSender.isEmpty() || safeText.isEmpty()) return null
        return AgentNotificationEvent(safePackage, safeSender, safeText, postedAtMillis)
    }

    private fun CharSequence?.normalized(maxLength: Int): String = this
        ?.toString()
        ?.take(maxLength)
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        .orEmpty()
}
