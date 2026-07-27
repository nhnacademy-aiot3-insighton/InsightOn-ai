package com.insighton.ai.notification.controller;

import com.insighton.ai.notification.dto.DashboardNotificationResponse;
import com.insighton.ai.notification.service.DashboardNotificationService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Dashboard Notifications", description = "대시보드 알림 조회 API")
@RestController
@RequestMapping("/api/dashboard-notifications")
@RequiredArgsConstructor
public class DashboardNotificationController {

    private final DashboardNotificationService notificationService;

    @Operation(summary = "안 읽은 알림 목록 조회", description = "groupId 기준으로 안 읽은 알림 목록을 최신순으로 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping
    public ResponseEntity<List<DashboardNotificationResponse>> getUnreadNotifications(
            @Parameter(description = "그룹 ID", example = "5", required = true,
                    schema = @Schema(type = "integer", format = "int64"))
            @RequestParam Long groupId
    ) {
        return ResponseEntity.ok(notificationService.findUnreadDashboardNotifications(groupId));
    }

    @Operation(summary = "알림 읽음 처리", description = "알림 클릭 시 읽음 처리하고 원본 상세 페이지로 이동할 때 사용합니다.")
    @ApiResponse(responseCode = "200", description = "처리 성공")
    @ApiResponse(responseCode = "404", description = "알림 없음")
    @PostMapping("/{dashboardNotificationId}/read")
    public ResponseEntity<DashboardNotificationResponse> markAsRead(
            @Parameter(description = "알림 ID", example = "1", schema = @Schema(type = "integer", format = "int64"))
            @PathVariable Long dashboardNotificationId
    ) {
        return ResponseEntity.ok(notificationService.markAsRead(dashboardNotificationId));
    }
}