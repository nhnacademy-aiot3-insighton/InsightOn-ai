package com.insighton.ai.controller.swagger;

import com.insighton.ai.domain.enginealert.dto.EngineAlertResponse;
import com.insighton.ai.domain.enginealert.entity.Severity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.ResponseEntity;

@Tag(name = "EngineAlerts", description = "엔진 알람 조회 API")
public interface EngineAlertApi {

    @Operation(summary = "엔진 알람 목록 조회", description = "groupId 기준으로 엔진 알람 목록을 조회합니다. locationId/severity로 추가 필터링 가능합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    ResponseEntity<List<EngineAlertResponse>> getEngineAlerts(
            @Parameter(description = "그룹 ID", example = "5", required = true,
                    schema = @Schema(type = "integer", format = "int64"))
            Long groupId,
            @Parameter(description = "위치 ID", example = "42",
                    schema = @Schema(type = "integer", format = "int64"))
            Long locationId,
            @Parameter(description = "심각도", example = "CRITICAL")
            Severity severity,
            @Parameter(description = "조회 시작 시각(ISO-8601, 미지정 시 3개월 전)", example = "2026-01-01T00:00:00+09:00")
            OffsetDateTime from,
            @Parameter(description = "조회 종료 시각(ISO-8601, 미지정 시 현재)", example = "2026-06-30T23:59:59+09:00")
            OffsetDateTime to
    );

    @Operation(summary = "엔진 알람 상세 조회", description = "엔진 알람 ID로 상세 내용을 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "404", description = "엔진 알람 없음")
    ResponseEntity<EngineAlertResponse> getEngineAlert(
            @Parameter(description = "엔진 알람 ID", example = "1", schema = @Schema(type = "integer", format = "int64"))
            Long engineAlertId,
            @Parameter(description = "요청자 사용자 ID (Gateway 주입)", required = true,
                    schema = @Schema(type = "integer", format = "int64"))
            Long userId
    );
}
