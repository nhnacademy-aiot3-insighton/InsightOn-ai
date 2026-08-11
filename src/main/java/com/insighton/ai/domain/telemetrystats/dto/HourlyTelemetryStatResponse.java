package com.insighton.ai.domain.telemetrystats.dto;

import com.insighton.ai.domain.telemetrystats.entity.HourlyTelemetryStat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(description = "시간별 텔레메트리 통계")
public record HourlyTelemetryStatResponse(
        @Schema(description = "통계 ID", example = "1") Long hourlyTelemetryStatId,
        @Schema(description = "위치 ID", example = "42") Long locationId,
        @Schema(description = "집계 시간대", example = "2026-07-23T14:00:00+09:00") OffsetDateTime logHour,
        @Schema(description = "평균 메트릭 (JSON, 예: {\"co2\":850,\"temperature\":24.5})") String metricsAvg,
        @Schema(description = "최대 메트릭 (JSON)") String metricsMax,
        @Schema(description = "최소 메트릭 (JSON)") String metricsMin,
        @Schema(description = "액추에이터별 가동 분 (JSON, 예: {\"aircon\":45,\"purifier\":12})") String actuatorOnMinutes,
        @Schema(description = "생성일시") OffsetDateTime createdAt
) {
    public static HourlyTelemetryStatResponse from(HourlyTelemetryStat stat) {
        return new HourlyTelemetryStatResponse(
                stat.getHourlyTelemetryStatId(),
                stat.getLocationId(),
                stat.getLogHour(),
                stat.getMetricsAvg(),
                stat.getMetricsMax(),
                stat.getMetricsMin(),
                stat.getActuatorOnMinutes(),
                stat.getCreatedAt()
        );
    }
}