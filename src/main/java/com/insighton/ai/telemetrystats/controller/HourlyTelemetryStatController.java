package com.insighton.ai.telemetrystats.controller;

import com.insighton.ai.telemetrystats.dto.HourlyTelemetryStatResponse;
import com.insighton.ai.telemetrystats.service.HourlyTelemetryStatService;
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
public class HourlyTelemetryStatController implements HourlyTelemetryStatApi {

    private final HourlyTelemetryStatService hourlyTelemetryStatService;

    @Override
    @GetMapping
    public ResponseEntity<List<HourlyTelemetryStatResponse>> getHourlyTelemetryStatus(
            @RequestParam Long locationId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to
    ) {
        return ResponseEntity.ok(hourlyTelemetryStatService.findHourlyTelemetryStats(locationId, from, to));
    }
}
