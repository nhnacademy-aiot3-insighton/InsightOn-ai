package com.insighton.ai.notification.controller;

import com.insighton.ai.notification.dto.DashboardNotificationResponse;
import com.insighton.ai.notification.service.DashboardNotificationService;
import com.insighton.ai.notification.sse.SseEmitterRegistry;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
