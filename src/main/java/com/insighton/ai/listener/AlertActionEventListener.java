package com.insighton.ai.listener;

import com.insighton.ai.config.RabbitConfig;
import com.insighton.ai.enginealert.dto.AiAlertActionEvent;
import com.insighton.ai.enginealert.dto.EngineAlertCreateRequest;
import com.insighton.ai.enginealert.service.EngineAlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class AlertActionEventListener {

    private final EngineAlertService engineAlertService;

    @RabbitListener(queues = RabbitConfig.ALERT_ACTION_QUEUE)
    public void handleAlertAction(AiAlertActionEvent event) {
        log.info("Rule Engine 알람 이벤트 수신 - locationId: {}, flowId: {}, severity: {}", event.locationId(),
                event.flowId(), event.severity());
        engineAlertService.createEngineAlert(new EngineAlertCreateRequest(
                event.groupId(),
                event.locationId(),
                event.flowId(),
                event.title(),
                event.message(),
                event.severity(),
                event.triggerValue()
        ));
    }
}
