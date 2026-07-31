package com.insighton.ai.telemetrystats.controller;

import com.insighton.ai.telemetrystats.dto.HourlyTelemetryStatResponse;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.ResponseEntity;

public interface HourlyTelemetryStatApi {

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
