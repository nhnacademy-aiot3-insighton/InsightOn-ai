package com.insighton.ai.controller.swagger;

import com.insighton.ai.domain.telemetrystats.dto.HourlyTelemetryStatResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.OffsetDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

@Tag(name = "HourlyTelemetryStats", description = "시간별 텔레메트리 통계 조회 API")
public interface HourlyTelemetryStatApi {

    @Operation(summary = "시간별 텔레메트리 통계 조회",
            description = "groupId·locationId 기준으로 시간별 통계 목록을 페이지 단위로 조회합니다. locationId가 groupId 소속이 아니면 403이 반환됩니다. "
                    + "from/to로 기간 필터링 가능하며, 정렬은 항상 최신순 고정입니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공",
            content = @Content(schema = @Schema(implementation = HourlyTelemetryStatResponse.class)))
    @ApiResponse(responseCode = "403", description = "locationId가 groupId 소속이 아님")
    ResponseEntity<Page<HourlyTelemetryStatResponse>> getHourlyTelemetryStatus(
            @Parameter(description = "그룹 ID", example = "5", required = true,
                    schema = @Schema(type = "integer", format = "int64"))
            Long groupId,
            @Parameter(description = "위치 ID", example = "42", required = true,
                    schema = @Schema(type = "integer", format = "int64"))
            Long locationId,
            @Parameter(description = "조회 시작 시각", example = "2026-07-23T00:00:00+09:00")
            OffsetDateTime from,
            @Parameter(description = "조회 종료 시각", example = "2026-07-23T23:59:59+09:00")
            OffsetDateTime to,
            @Parameter(description = "페이지 번호(0부터)/크기")
            Pageable pageable
    );
}
