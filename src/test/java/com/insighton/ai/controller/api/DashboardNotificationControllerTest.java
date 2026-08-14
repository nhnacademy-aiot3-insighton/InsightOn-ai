package com.insighton.ai.controller.api;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.insighton.ai.adapter.client.GroupAuthorizationService;
import com.insighton.ai.adapter.client.exception.ForbiddenException;
import com.insighton.ai.domain.notification.dto.DashboardNotificationResponse;
import com.insighton.ai.domain.notification.entity.NotificationType;
import com.insighton.ai.domain.notification.exception.DashboardNotificationNotFoundException;
import com.insighton.ai.domain.notification.service.DashboardNotificationService;
import com.insighton.ai.domain.notification.sse.SseEmitterRegistry;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DashboardNotificationController.class)
class DashboardNotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardNotificationService notificationService;

    @MockitoBean
    private SseEmitterRegistry sseEmitterRegistry;

    @MockitoBean
    private GroupAuthorizationService groupAuthorizationService;

    @Test
    void getUnreadNotifications_정상_조회시_200과_목록을_반환() throws Exception {
        DashboardNotificationResponse response = new DashboardNotificationResponse(1L, 42L,
                NotificationType.REPORT, 10L, "제목", false, OffsetDateTime.now());
        given(notificationService.findUnreadDashboardNotifications(5L)).willReturn(List.of(response));

        mockMvc.perform(get("/api/v1/dashboard-notifications").param("groupId", "5").header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].dashboardNotificationId").value(1))
                .andExpect(jsonPath("$[0].isRead").value(false));
    }

    @Test
    void getUnreadNotifications_groupId가_없으면_400을_반환() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard-notifications").header("X-User-Id", "1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getUnreadNotifications_X_User_Id_헤더가_없으면_400을_반환() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard-notifications").param("groupId", "5"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void markAsRead_정상_처리시_200과_읽음_처리된_알림_반환() throws Exception {
        DashboardNotificationResponse response = new DashboardNotificationResponse(1L, 42L,
                NotificationType.REPORT, 10L, "제목", true, OffsetDateTime.now());
        given(notificationService.markAsRead(1L, 100L)).willReturn(response);

        mockMvc.perform(post("/api/v1/dashboard-notifications/1/read").header("X-User-Id", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isRead").value(true));
    }

    @Test
    void markAsRead_존재하지_않으면_404_반환() throws Exception {
        given(notificationService.markAsRead(999L, 100L))
                .willThrow(new DashboardNotificationNotFoundException(999L));

        mockMvc.perform(post("/api/v1/dashboard-notifications/999/read").header("X-User-Id", "100"))
                .andExpect(status().isNotFound());
    }

    @Test
    void markAsRead_권한이_없으면_403_반환() throws Exception {
        given(notificationService.markAsRead(1L, 100L))
                .willThrow(new ForbiddenException("MANAGER 이상 권한이 필요합니다."));

        mockMvc.perform(post("/api/v1/dashboard-notifications/1/read").header("X-User-Id", "100"))
                .andExpect(status().isForbidden());
    }

    @Test
    void streamNotifications_정상_요청시_SSE_스트림을_시작() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard-notifications/stream").param("groupId", "5")
                        .header("X-User-Id", "1"))
                .andExpect(request().asyncStarted())
                .andExpect(header().string("Content-Type", MediaType.TEXT_EVENT_STREAM_VALUE));
    }

    @Test
    void streamNotifications_groupId가_없으면_400_반환() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard-notifications/stream").header("X-User-Id", "1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void streamNotifications_X_User_Id_헤더가_없으면_400_반환() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard-notifications/stream").param("groupId", "5"))
                .andExpect(status().isBadRequest());
    }
}