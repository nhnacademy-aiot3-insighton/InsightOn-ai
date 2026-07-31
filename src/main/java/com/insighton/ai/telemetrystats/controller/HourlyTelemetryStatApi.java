package com.insighton.ai.telemetrystats.controller;

import com.insighton.ai.telemetrystats.dto.HourlyTelemetryStatResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.ResponseEntity;

@Tag(name = "HourlyTelemetryStats", description = "시간별 텔레메트리 통계 조회 API")
public interface HourlyTelemetryStatApi {

    @Operation(summary = "시간별 텔레메트리 통계 조회", description = "locationId 기준으로 시간별 통계 목록을 조회합니다. from/to로 기간 필터링 가능합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    ResponseEntity<List<HourlyTelemetryStatResponse>> getHourlyTelemetryStatus(
            @Parameter(description = "위치 ID", example = "42", required = true,
                    schema = @Schema(type = "integer", format = "int64"))
            Long locationId,
            @Parameter(description = "조회 시작 시각", example = "2026-07-23T00:00:00+09:00")
            OffsetDateTime from,
            @Parameter(description = "조회 종료 시각", example = "2026-07-23T23:59:59+09:00")
            OffsetDateTime to
    );
}
