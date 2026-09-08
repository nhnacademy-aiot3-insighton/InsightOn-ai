package com.insighton.ai.domain.notification.sse;

import com.insighton.ai.common.config.RabbitConfig;
import com.insighton.ai.domain.notification.dto.DashboardNotificationBroadcastEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * dashboard_notifications 저장 트랜잭션이 실제로 커밋된 이후에만 RabbitMQ로 발행한다. 트랜잭션 안에서 바로 발행하면 이후 롤백 시 DB엔 없는 알림이 클라이언트에 푸시되는 문제가 생길
 * 수 있어 분리
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DashboardNotificationEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishAfterCommit(DashboardNotificationBroadcastEvent event) {

        rabbitTemplate.convertAndSend(
                RabbitConfig.NOTIFICATION_FANOUT_EXCHANGE,
                "",
                event);

        log.info("알림 푸시 이벤트 발행 - groupId:{}", event.groupId());
    }
}
