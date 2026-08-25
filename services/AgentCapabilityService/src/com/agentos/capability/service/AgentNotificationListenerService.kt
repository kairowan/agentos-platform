package com.agentos.capability.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class AgentNotificationListenerService : NotificationListenerService() {
    override fun onNotificationPosted(statusBarNotification: StatusBarNotification) {
        val notification = statusBarNotification.notification
        NotificationEventPolicy.create(
            packageName = statusBarNotification.packageName,
            sender = notification.extras.getCharSequence(Notification.EXTRA_TITLE),
            text = notification.extras.getCharSequence(Notification.EXTRA_TEXT),
            postedAtMillis = statusBarNotification.postTime,
            isMessage = notification.category == Notification.CATEGORY_MESSAGE,
            isOngoing = notification.flags and Notification.FLAG_ONGOING_EVENT != 0,
            isGroupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
        )?.let(AgentEventBus::publish)
    }
}
