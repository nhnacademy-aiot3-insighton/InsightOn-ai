package com.insighton.ai.domain.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "dashboard_notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DashboardNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "dashboard_notification_id")
    private Long dashboardNotificationId;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "location_id", nullable = false)
    private Long locationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 30)
    private NotificationType notificationType;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "is_read", nullable = false)
    private boolean isRead;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Builder
    public DashboardNotification(Long groupId, Long locationId, NotificationType notificationType,
                                 Long sourceId, String title) {
        this.groupId = groupId;
        this.locationId = locationId;
        this.notificationType = notificationType;
        this.sourceId = sourceId;
        this.title = title;
        this.isRead = false;
    }

    public void markAsRead() {
        this.isRead = true;
    }
}
