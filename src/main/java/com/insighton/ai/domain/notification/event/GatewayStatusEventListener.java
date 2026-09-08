package com.insighton.ai.domain.notification.event;

import com.insighton.ai.common.config.RabbitConfig;
import com.insighton.ai.domain.notification.dto.DashboardNotificationCreateRequest;
import com.insighton.ai.domain.notification.entity.NotificationType;
import com.insighton.ai.domain.notification.service.DashboardNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class GatewayStatusEventListener {
    private final DashboardNotificationService dashboardNotificationService;

    @RabbitListener(queues = RabbitConfig.GATEWAY_STATUS_QUEUE)
    public void handleGatewayStatusChanged(GatewayStatusChangedEvent event) {
        log.info("게이트웨이 상태 변경 이벤트 수신 - gatewayId: {}, groupId: {}, status: {}", event.gatewayId(), event.groupId(),
                event.status());

        String title = event.status() == GatewayStatus.FAULT
                ? event.gatewayName() + " 게이트웨이 연결이 끊겼습니다."
                : event.gatewayName() + " 게이트웨이 연결이 복구되었습니다.";

        dashboardNotificationService.create(new DashboardNotificationCreateRequest(
                event.groupId(), null, NotificationType.GATEWAY, event.gatewayId(), title
        ));
    }
}
