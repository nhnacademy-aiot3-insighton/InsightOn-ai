package com.insighton.ai.telemetrystats.controller;

import com.insighton.ai.telemetrystats.dto.HourlyTelemetryStatResponse;
import com.insighton.ai.telemetrystats.service.HourlyTelemetryStatService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/hourly-telemetry-stats")
@RequiredArgsConstructor
public class HourlyTelemetryStatController {

    private final HourlyTelemetryStatService hourlyTelemetryStatService;

    @GetMapping
    public ResponseEntity<List<HourlyTelemetryStatResponse>> getHourlyTelemetryStatus(
            @Parameter(description = "위치 ID", example = "42", required = true,
                    schema = @Schema(type = "integer", format = "int64"))
            @RequestParam Long locationId,
            @Parameter(description = "조회 시작 시각", example = "2026-07-23T00:00:00+09:00")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @Parameter(description = "조회 종료 시각", example = "2026-07-23T23:59:59+09:00")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to
    ) {

        return ResponseEntity.ok(hourlyTelemetryStatService.findHourlyTelemetryStats(locationId, from, to));
    }
}
