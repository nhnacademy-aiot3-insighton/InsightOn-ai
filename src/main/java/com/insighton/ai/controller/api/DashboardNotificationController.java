package com.insighton.ai.controller.api;

import com.insighton.ai.controller.swagger.DashboardNotificationApi;
import com.insighton.ai.domain.notification.dto.DashboardNotificationResponse;
import com.insighton.ai.domain.notification.entity.NotificationType;
import com.insighton.ai.domain.notification.service.DashboardNotificationService;
import com.insighton.ai.domain.notification.sse.SseEmitterRegistry;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/dashboard-notifications")
@RequiredArgsConstructor
public class DashboardNotificationController implements DashboardNotificationApi {

    private final DashboardNotificationService notificationService;
    private final SseEmitterRegistry sseEmitterRegistry;

    @Override
    @GetMapping
    public ResponseEntity<List<DashboardNotificationResponse>> getUnreadNotifications(
            @RequestParam Long groupId
    ) {
        return ResponseEntity.ok(notificationService.findUnreadDashboardNotifications(groupId));
    }

    @Override
    @GetMapping("/search")
    public ResponseEntity<Page<DashboardNotificationResponse>> searchNotifications(
            @RequestParam Long groupId,
            @RequestParam(required = false) Boolean isRead,
            @RequestParam(required = false) NotificationType notificationType,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(
                notificationService.getDashboardNotifications(groupId, isRead, notificationType, pageable));
    }

    @Override
    @PostMapping("/{dashboardNotificationId}/read")
    public ResponseEntity<DashboardNotificationResponse> markAsRead(
            @PathVariable Long dashboardNotificationId,
            @RequestHeader("X-User-Id") Long userId
    ) {
        return ResponseEntity.ok(notificationService.markAsRead(dashboardNotificationId, userId));
    }

    @Override
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamNotifications(@RequestParam Long groupId) {
        SseEmitter emitter = new SseEmitter(0L);
        sseEmitterRegistry.sseRegister(groupId, emitter);
        return emitter;
    }
}
