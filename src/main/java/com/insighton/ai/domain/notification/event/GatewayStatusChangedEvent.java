package com.insighton.ai.domain.notification.event;

import java.time.OffsetDateTime;

public record GatewayStatusChangedEvent(
        Long gatewayId,
        Long groupId,
        String gatewayName,
        GatewayStatus status,
        OffsetDateTime occurredAt
) {
}