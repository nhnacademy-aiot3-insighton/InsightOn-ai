package com.insighton.ai.notification.exception;

public class DashboardNotificationNotFoundException extends RuntimeException {

    private final Long dashboardNotificationId;

    public DashboardNotificationNotFoundException(Long dashboardNotificationId) {
        super("DashboardNotification not found: " + dashboardNotificationId);
        this.dashboardNotificationId = dashboardNotificationId;
    }

    public Long getDashboardNotificationId() {
        return dashboardNotificationId;
    }
}