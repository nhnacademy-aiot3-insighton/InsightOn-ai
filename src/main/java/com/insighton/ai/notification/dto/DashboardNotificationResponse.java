package com.insighton.ai.notification.dto;

import com.insighton.ai.notification.domain.DashboardNotification;
import com.insighton.ai.notification.domain.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(description = "대시보드 알림")
public record DashboardNotificationResponse(

        @Schema(description = "알림 ID", example = "1")
        Long dashboardNotificationId,

        @Schema(description = "위치 ID", example = "10")
        Long locationId,

        @Schema(description = "알림 종류")
        NotificationType notificationType,

        @Schema(description = "원본 레코드 ID (notificationType에 따라 대상 테이블 결정")
        Long sourceId,

        @Schema(description = "알림 제목")
        String title,

        @Schema(description = "읽음 여부")
        boolean isRead,

        @Schema(description = "생성일시")
        OffsetDateTime createdAt
) {

    public static DashboardNotificationResponse from(DashboardNotification notification) {

        return new DashboardNotificationResponse(
                notification.getDashboardNotificationId(),
                notification.getLocationId(),
                notification.getNotificationType(),
                notification.getSourceId(),
                notification.getTitle(),
                notification.isRead(),
                notification.getCreatedAt()
        );
    }
}
