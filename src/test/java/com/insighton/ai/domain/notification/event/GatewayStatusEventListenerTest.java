package com.insighton.ai.domain.notification.event;

import static org.mockito.ArgumentMatchers.argThat;

import com.insighton.ai.domain.notification.dto.DashboardNotificationCreateRequest;
import com.insighton.ai.domain.notification.entity.NotificationType;
import com.insighton.ai.domain.notification.service.DashboardNotificationService;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GatewayStatusEventListenerTest {

    @Mock
    private DashboardNotificationService dashboardNotificationService;

    private GatewayStatusEventListener gatewayStatusEventListener;

    @BeforeEach
    void setUp() {
        gatewayStatusEventListener = new GatewayStatusEventListener(dashboardNotificationService);
    }

    @Test
    void handleGatewayStatusChanged_FAULT면_연결_끊김_알림을_생성한다() {
        GatewayStatusChangedEvent event = new GatewayStatusChangedEvent(
                1L, 5L, "3층 게이트웨이", GatewayStatus.FAULT, OffsetDateTime.now());

        gatewayStatusEventListener.handleGatewayStatusChanged(event);

        Mockito.verify(dashboardNotificationService).create(argThat((DashboardNotificationCreateRequest req) ->
                req.groupId().equals(5L)
                        && req.notificationType() == NotificationType.GATEWAY
                        && req.sourceId().equals(1L)
                        && req.title().equals("3층 게이트웨이 게이트웨이 연결이 끊겼습니다.")));
    }

    @Test
    void handleGatewayStatusChanged_ACTIVE면_복구_알림을_생성한다() {
        GatewayStatusChangedEvent event = new GatewayStatusChangedEvent(
                1L, 5L, "3층 게이트웨이", GatewayStatus.ACTIVE, OffsetDateTime.now());

        gatewayStatusEventListener.handleGatewayStatusChanged(event);

        Mockito.verify(dashboardNotificationService).create(argThat((DashboardNotificationCreateRequest req) ->
                req.title().equals("3층 게이트웨이 게이트웨이 연결이 복구되었습니다.")));
    }
}
