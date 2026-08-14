package com.insighton.ai.domain.notification.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.insighton.ai.domain.notification.dto.DashboardNotificationResponse;
import com.insighton.ai.domain.notification.entity.NotificationType;
import com.insighton.ai.domain.notification.service.DashboardNotificationService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ToolContext;

@ExtendWith(MockitoExtension.class)
class NotificationChatToolTest {

    @Mock
    private DashboardNotificationService dashboardNotificationService;

    @InjectMocks
    private NotificationChatTool notificationChatTool;

    @Test
    void getUnreadNotifications_toolContext의_groupId로_조회한다() {
        ToolContext toolContext = new ToolContext(Map.of("groupId", 5L));
        DashboardNotificationResponse response = new DashboardNotificationResponse(1L, 42L,
                NotificationType.REPORT, 10L, "제목", false, OffsetDateTime.now());
        given(dashboardNotificationService.findUnreadDashboardNotifications(5L)).willReturn(List.of(response));

        List<DashboardNotificationResponse> result = notificationChatTool.getUnreadNotifications(toolContext);

        assertThat(result).containsExactly(response);
    }

    @Test
    void getUnreadNotifications_읽지_않은_알림이_없으면_빈_리스트를_반환한다() {
        ToolContext toolContext = new ToolContext(Map.of("groupId", 5L));
        given(dashboardNotificationService.findUnreadDashboardNotifications(5L)).willReturn(List.of());

        List<DashboardNotificationResponse> result = notificationChatTool.getUnreadNotifications(toolContext);

        assertThat(result).isEmpty();
    }
}
