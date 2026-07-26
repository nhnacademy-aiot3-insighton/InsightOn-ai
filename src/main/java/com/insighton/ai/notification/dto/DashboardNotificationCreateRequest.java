package com.insighton.ai.notification.dto;

import com.insighton.ai.notification.domain.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "대시보드 알림 생성 요청 (내부용 — engine_alerts/suggestion_logs/reports 생성과 같은 트랜잭션에서 호출)")
public record DashboardNotificationCreateRequest(

        @Schema(description = "그룹 ID", example = "5")
        @NotNull
        Long groupId,

        @Schema(description = "위치 ID", example = "10")
        @NotNull
        Long locationId,

        @Schema(description = "알림 종류")
        @NotNull
        NotificationType notificationType,

        @Schema(description = "원본 레코드 ID")
        @NotNull
        Long sourceId,

        @Schema(description = "제목")
        @NotBlank
        String title
) {
}
