package com.insighton.ai.notification.dto;

public record DashboardNotificationBroadcastEvent(
        Long groupId,
        DashboardNotificationResponse notification
) {
}