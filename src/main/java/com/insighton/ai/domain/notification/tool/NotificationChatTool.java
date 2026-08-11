package com.insighton.ai.domain.notification.tool;

import com.insighton.ai.domain.notification.dto.DashboardNotificationResponse;
import com.insighton.ai.domain.notification.service.DashboardNotificationService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationChatTool {

    private final DashboardNotificationService dashboardNotificationService;

    @Tool(description = "현재 그룹의 안 읽은 알림(알람/제안/리포트 발생 요약) 목록을 조회한다.")
    public List<DashboardNotificationResponse> getUnreadNotifications(ToolContext toolContext) {
        Long groupId = (Long) toolContext.getContext().get("groupId");

        return dashboardNotificationService.findUnreadDashboardNotifications(groupId);
    }
}
