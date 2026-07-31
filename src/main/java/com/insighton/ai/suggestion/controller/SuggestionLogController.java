package com.insighton.ai.suggestion.controller;

import com.insighton.ai.suggestion.dto.SuggestionLogResponse;
import com.insighton.ai.suggestion.service.SuggestionLogService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Suggestions", description = "AI 제안 로그 조회 API")
@RestController
@RequestMapping("/api/suggestions")
@RequiredArgsConstructor
public class SuggestionLogController {

    private final SuggestionLogService suggestionLogService;

    @Operation(summary = "제안 목록 조회", description = "groupId 기준으로 제안 로그 목록을 조회합니다. locationId로 추가 필터링 가능합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping
    public ResponseEntity<List<SuggestionLogResponse>> getSuggestionLogs(
            @Parameter(description = "그룹 ID", example = "5", required = true,
                    schema = @Schema(type = "integer", format = "int64"))
            @RequestParam Long groupId,
            @Parameter(description = "위치 ID", example = "42",
                    schema = @Schema(type = "integer", format = "int64"))
            @RequestParam(required = false) Long locationId
    ) {
        return ResponseEntity.ok(suggestionLogService.findSuggestionLogs(groupId, locationId));
    }

    @Operation(summary = "제안 상세 조회", description = "제안 로그 ID로 상세 내용을 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "404", description = "제안 로그 없음")
    @GetMapping("/{suggestionLog-id}")
    public ResponseEntity<SuggestionLogResponse> getSuggestionLog(
            @Parameter(description = "제안 로그 ID", example = "1",
                    schema = @Schema(type = "integer", format = "int64"))
            @PathVariable("suggestionLog-id") Long suggestionLogId,
            @Parameter(description = "요청자 사용자 ID (Gateway 주입)", required = true,
                    schema = @Schema(type = "integer", format = "int64"))
            @RequestHeader("X-User-Id") Long userId
    ) {
        return ResponseEntity.ok(suggestionLogService.findSuggestionLog(suggestionLogId, userId));
    }

    @Operation(summary = "제안 수락", description = "AI의 제안을 수락합니다. (지금은 상태만 변경, Core 제어 API 실행은 Issue 08에서 추가 예정)")
    @ApiResponse(responseCode = "200", description = "처리 성공")
    @ApiResponse(responseCode = "404", description = "제안 로그 없음")
    @PostMapping("/{suggestionLog-id}/accept")
    public ResponseEntity<SuggestionLogResponse> accept(
            @Parameter(description = "제안 로그 ID", example = "1", schema = @Schema(type = "integer", format = "int64"))
            @PathVariable("suggestionLog-id") Long suggestionLogId,
            @Parameter(description = "요청자 사용자 ID (Gateway 주입)", required = true,
                    schema = @Schema(type = "integer", format = "int64"))
            @RequestHeader("X-User-Id") Long userId
    ) {
        return ResponseEntity.ok(suggestionLogService.accept(suggestionLogId, userId));
    }

    @Operation(summary = "제안 거절", description = "AI의 제안을 거절합니다.")
    @ApiResponse(responseCode = "200", description = "처리 성공")
    @ApiResponse(responseCode = "404", description = "제안 로그 없음")
    @PostMapping("/{suggestionLog-id}/reject")
    public ResponseEntity<SuggestionLogResponse> reject(
            @Parameter(description = "제안 로그 ID", example = "1", schema = @Schema(type = "integer", format = "int64"))
            @PathVariable("suggestionLog-id") Long suggestionLogId,
            @Parameter(description = "요청자 사용자 ID (Gateway 주입)", required = true,
                    schema = @Schema(type = "integer", format = "int64"))
            @RequestHeader("X-User-Id") Long userId
    ) {
        return ResponseEntity.ok(suggestionLogService.reject(suggestionLogId, userId));
    }
}