package com.insighton.ai.enginealert.controller;

import com.insighton.ai.enginealert.domain.Severity;
import com.insighton.ai.enginealert.dto.EngineAlertResponse;
import com.insighton.ai.enginealert.service.EngineAlertService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/engine-alerts")
@RequiredArgsConstructor
public class EngineAlertController implements EngineAlertApi {

    private final EngineAlertService engineAlertService;

    @Override
    @GetMapping
    public ResponseEntity<List<EngineAlertResponse>> getEngineAlerts(
            @RequestParam Long groupId,
            @RequestParam(required = false) Long locationId,
            @RequestParam(required = false) Severity severity
    ) {
        return ResponseEntity.ok(engineAlertService.findEngineAlerts(groupId, locationId, severity));
    }

    @Override
    @GetMapping("/{engineAlertId}")
    public ResponseEntity<EngineAlertResponse> getEngineAlert(
            @PathVariable Long engineAlertId,
            @RequestHeader("X-User-Id") Long userId
    ) {
        return ResponseEntity.ok(engineAlertService.findEngineAlert(engineAlertId, userId));
    }
}
