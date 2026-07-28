package com.insighton.ai.enginealert.dto;

import com.insighton.ai.enginealert.domain.EngineAlert;
import com.insighton.ai.enginealert.domain.Severity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Schema(description = "엔진 알람")
public record EngineAlertResponse(

        @Schema(description = "알람 ID", example = "1")
        Long engineAlertId,
        
        @Schema(description = "그룹 ID", example = "5")
        Long groupId,

        @Schema(description = "위치 ID", example = "42")
        Long locationId,

        @Schema(description = "발생 플로우 ID (플로우 관여 없으면 null)", example = "7")
        Long flowId,

        @Schema(description = "제목")
        String title,

        @Schema(description = "상세 메시지")
        String message,

        @Schema(description = "심각도")
        Severity severity,

        @Schema(description = "알람을 유발한 실제 센서값")
        BigDecimal triggerValue,

        @Schema(description = "생성일시")
        OffsetDateTime createdAt
) {
    public static EngineAlertResponse from(EngineAlert alert) {
        return new EngineAlertResponse(
                alert.getEngineAlertId(),
                alert.getGroupId(),
                alert.getLocationId(),
                alert.getFlowId(),
                alert.getTitle(),
                alert.getMessage(),
                alert.getSeverity(),
                alert.getTriggerValue(),
                alert.getCreatedAt()
        );
    }
}