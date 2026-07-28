package com.insighton.ai.enginealert.dto;

import com.insighton.ai.enginealert.domain.Severity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import org.hibernate.validator.constraints.Length;

@Schema(description = "엔진 알람 생성 요청 (내부용 — Rule Engine ALERT_ACTION 수신, AI_SUGGESTION_ACTION 처리 중 자체 판단, 또는 매시간 배치 스윕 시 호출)")
public record EngineAlertCreateRequest(

        @Schema(description = "그룹 ID", example = "1")
        @NotNull
        Long groupId,

        @Schema(description = "위치 ID", example = "1")
        @NotNull
        Long locationId,

        @Schema(description = "발생 플로우 ID ", example = "1")
        @NotNull
        Long flowId,

        @Schema(description = "제목")
        @NotBlank
        @Length(max = 200)
        String title,

        @Schema(description = "상세 메시지")
        @NotBlank
        String message,

        @Schema(description = "심각도")
        @NotNull
        Severity severity,

        @Schema(description = "알람을 유발한 실제 센서값")
        BigDecimal triggerValue
) {
}
