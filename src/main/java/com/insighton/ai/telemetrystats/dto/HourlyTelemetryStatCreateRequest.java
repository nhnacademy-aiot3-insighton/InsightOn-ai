package com.insighton.ai.telemetrystats.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

@Schema(description = "시간별 텔레메트리 통계 생성 요청 (내부용, 정각 집계 배치 전용 — 외부 API로는 노출 안 됨)")
public record HourlyTelemetryStatCreateRequest(
        @Schema(description = "그룹 ID", example = "5")
        @NotNull
        Long groupId,

        @Schema(description = "위치 ID", example = "42")
        @NotNull
        Long locationId,

        @Schema(description = "집계 시간대", example = "2026-07-23T14:00:00+09:00")
        @NotNull
        OffsetDateTime logHour,

        @Schema(description = "평균 메트릭 (JSON, 예: {\"co2\":850,\"temperature\":24.5})")
        @NotBlank
        String metricsAvg,

        @Schema(description = "최대 메트릭 (JSON)")
        @NotBlank
        String metricsMax,

        @Schema(description = "최소 메트릭 (JSON)")
        @NotBlank
        String metricsMin,

        @Schema(description = "액추에이터별 가동 분 (JSON, 예: {\"aircon\":45,\"purifier\":12})")
        @NotBlank
        String actuatorOnMinutes
) {
}