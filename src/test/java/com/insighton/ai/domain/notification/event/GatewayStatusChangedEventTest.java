package com.insighton.ai.domain.notification.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class GatewayStatusChangedEventTest {

    @Test
    void 필드값을_그대로_보관한다() {
        OffsetDateTime occurredAt = OffsetDateTime.parse("2026-09-07T09:00:00+09:00");

        GatewayStatusChangedEvent event = new GatewayStatusChangedEvent(
                1L, 5L, "3층 게이트웨이", GatewayStatus.FAULT, occurredAt);

        assertThat(event.gatewayId()).isEqualTo(1L);
        assertThat(event.groupId()).isEqualTo(5L);
        assertThat(event.gatewayName()).isEqualTo("3층 게이트웨이");
        assertThat(event.status()).isEqualTo(GatewayStatus.FAULT);
        assertThat(event.occurredAt()).isEqualTo(occurredAt);
    }
}
