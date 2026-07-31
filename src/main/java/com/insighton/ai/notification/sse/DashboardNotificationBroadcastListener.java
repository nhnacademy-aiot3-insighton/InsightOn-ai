package com.insighton.ai.notification.sse;

import com.insighton.ai.config.RabbitConfig;
import com.insighton.ai.notification.dto.DashboardNotificationBroadcastEvent;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 다른 인스턴스가 만든 알림을 팬아웃으로 전달받아, 이 인스턴스가 물고 있는 SSE 커넥션에 실제로 푸시 인스턴스마다 자기만의 익명 인스턴스마다 자기만의 익명 큐를 선언해 모든 인스턴스가 매 이벤트를 다시
 * 받는다.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DashboardNotificationBroadcastListener {

    private final SseEmitterRegistry sseEmitterRegistry;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(autoDelete = "true", exclusive = "true"),
            exchange = @Exchange(value = RabbitConfig.NOTIFICATION_FANOUT_EXCHANGE,
                    type = ExchangeTypes.FANOUT)
    ))
    public void handleBroadcast(DashboardNotificationBroadcastEvent event) {
        List<SseEmitter> emitters = sseEmitterRegistry.getEmitters(event.groupId());

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("notification").data(event.notification()));
            } catch (IOException e) {
                sseEmitterRegistry.sseRemove(event.groupId(), emitter);
            }
        }

        log.info("알림 브로드캐스트 수신 - groupId: {}, size: {}", event.groupId(), emitters.size());
    }
}
