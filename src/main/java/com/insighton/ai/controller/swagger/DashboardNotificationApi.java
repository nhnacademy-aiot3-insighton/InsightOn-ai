package com.insighton.ai.controller.swagger;

import com.insighton.ai.domain.notification.dto.DashboardNotificationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "Dashboard Notifications", description = "대시보드 알림 조회 API")
public interface DashboardNotificationApi {

    @Operation(summary = "안 읽은 알림 목록 조회", description = "groupId 기준으로 안 읽은 알림 목록을 최신순으로 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    ResponseEntity<List<DashboardNotificationResponse>> getUnreadNotifications(
            @Parameter(description = "그룹 ID", example = "5", required = true,
                    schema = @Schema(type = "integer", format = "int64"))
            Long groupId
    );

    @Operation(summary = "알림 읽음 처리", description = "알림 클릭 시 읽음 처리하고 원본 상세 페이지로 이동할 때 사용합니다. MANAGER 이상만 읽음 처리 가능합니다(목록 조회 자체는 MEMBER도 가능).")
    @ApiResponse(responseCode = "200", description = "처리 성공")
    @ApiResponse(responseCode = "403", description = "그룹 비소속 또는 권한 부족")
    @ApiResponse(responseCode = "404", description = "알림 없음")
    ResponseEntity<DashboardNotificationResponse> markAsRead(
            @Parameter(description = "알림 ID", example = "1", schema = @Schema(type = "integer", format = "int64"))
            Long dashboardNotificationId,
            @Parameter(description = "요청자 사용자 ID (Gateway 주입)", required = true,
                    schema = @Schema(type = "integer", format = "int64"))
            Long userId

    );

    @Operation(summary = "안 읽은 알림 실시간 구독", description = "groupId 기준으로 새 알림을 SSE로 실시간 수신합니다. 헤더처럼 앱 전역에서 연결을 계속 유지하는 용도입니다.")
    @ApiResponse(responseCode = "200", description = "스트림 연결 성공")
    SseEmitter streamNotifications(
            @Parameter(description = "그룹 ID", example = "5", required = true,
                    schema = @Schema(type = "integer", format = "int64"))
            Long groupId);
}
