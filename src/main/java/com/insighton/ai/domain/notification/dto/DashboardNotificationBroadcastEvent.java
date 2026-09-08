package com.insighton.ai.domain.notification.dto;

public record DashboardNotificationBroadcastEvent(
        Long groupId,
        DashboardNotificationResponse notification
) {
}