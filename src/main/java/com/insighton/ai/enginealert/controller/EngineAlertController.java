package com.insighton.ai.enginealert.controller;

import com.insighton.ai.enginealert.dto.EngineAlertResponse;
import com.insighton.ai.enginealert.service.EngineAlertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "EngineAlerts", description = "엔진 알람 조회 API")
@RestController
@RequestMapping("/api/engine-alerts")
@RequiredArgsConstructor
public class EngineAlertController {

    private final EngineAlertService engineAlertService;

    @Operation(summary = "엔진 알람 목록 조회", description = "groupId 기준으로 엔진 알람 목록을 조회합니다. locationId로 추가 필터링 가능합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping
    public ResponseEntity<List<EngineAlertResponse>> getEngineAlerts(
            @Parameter(description = "그룹 ID", example = "5", required = true,
                    schema = @Schema(type = "integer", format = "int64"))
            @RequestParam Long groupId,
            @Parameter(description = "위치 ID", example = "42",
                    schema = @Schema(type = "integer", format = "int64"))
            @RequestParam(required = false) Long locationId) {

        return ResponseEntity.ok(engineAlertService.findEngineAlerts(groupId, locationId));
    }

    @Operation(summary = "엔진 알람 상세 조회", description = "엔진 알람 ID로 상세 내용을 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "404", description = "엔진 알람 없음")
    @GetMapping("/{engineAlertId}")
    public ResponseEntity<EngineAlertResponse> getEngineAlert(
            @Parameter(description = "엔진 알람 ID", example = "1",
                    schema = @Schema(type = "integer", format = "int64")
            )
            @PathVariable Long engineAlertId,
            @Parameter(description = "요청자 사용자 ID (Gateway 주입)", required = true,
                    schema = @Schema(type = "integer", format = "int64"))
            @RequestHeader("X-User-Id") Long userId) {

        return ResponseEntity.ok(engineAlertService.findEngineAlert(engineAlertId, userId));
    }
}
