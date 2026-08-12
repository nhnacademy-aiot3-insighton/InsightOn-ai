package com.insighton.ai.domain.enginealert.event;

import com.insighton.ai.common.config.RabbitConfig;
import com.insighton.ai.domain.enginealert.service.EngineAlertService;
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
    public void handleEngineAlertAction(EngineAlertActionEvent event) {
        log.info("Rule Engine 알람 이벤트 수신 - locationId:{}, flowId:{}, severity:{}",
                event.locationId(), event.flowId(), event.severity());

        engineAlertService.createEngineAlert(event);
    }
}
